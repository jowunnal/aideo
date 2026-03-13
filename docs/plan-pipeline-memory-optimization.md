# 파이프라인 메모리 최적화 및 모델 재사용 계획

## 문제 배경

### 원래 질문
ForegroundService(`TranscribeService`)가 시스템에 의해 종료됐을 때 `START_STICKY`로 재생성 후 추론 작업을 이어서 실행할 수 있는 흐름이 필요한가?

### 근본 원인 분석
- 포그라운드 서비스가 시스템에 의해 kill되는 이유는 **메모리 부족**
- 추론 작업 자체가 메모리를 많이 사용하므로, kill 후 재시작해도 같은 메모리 부족으로 다시 실패할 가능성이 높음
- 따라서 **복구 메커니즘보다 메모리 부족 상황을 만들지 않는 것이 우선**

### 추론 재개(checkpoint/resume) 검토 결과
- 기술적으로 가능: 각 SR 세그먼트가 stateless (매번 fresh stream 생성/해제), 멱등성 보장
- 그러나 근본 원인(메모리 부족)을 해결하지 못하면 재개해도 다시 kill됨
- 따라서 메모리 최적화가 선행되어야 함

## 현재 구조 분석

### 파이프라인 흐름
```
extractAudioFromVideo (IO, MediaCodec)
  → Channel<FloatArray> (capacity=5)
  → processExtractedAudioWithVad (VAD 512-window → SD)
    → Channel<SingleSpeechSegment> (capacity=10)
    → transcribeSingleSegment (SR: Whisper or SenseVoice)
      → speechRecognition.getResult() → SRT 텍스트
        → Punctuation (optional)
          → storeSubtitleFile → .srt
```

### 현재 메모리 상주 문제
`initialize()`에서 VAD + SD + SR을 **모두 한꺼번에 로드**하고, `onDestroy()`의 `release()`에서 **한꺼번에 해제**.

```
현재: VAD+SD+SR 전부 ████████████████████████████████████████
이상: VAD+SD         ████████████████████░░░░░░░░░░░░░░░░░░░
      SR             ░░░░░░░░████████████████████████░░░░░░░
      Punctuation    ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░████░░░░
```

Phase 2(VAD+SD)와 Phase 3(SR)은 Channel로 연결되어 시간이 겹치므로 동시 상주는 불가피하지만, **Phase 2 완료 후 VAD+SD를 해제하면 Phase 3 나머지 구간에서 SR만 남게 됨**.

## 최적화 방향: 두 가지 독립 축

### A. 추론 중 피크 메모리 절감 — Phase 완료 시 release
- 목적: 추론 실행 중 메모리 피크를 낮춰 시스템 kill 방지
- 방법: 각 Phase가 끝나면 해당 컴포넌트의 네이티브 메모리 해제

### B. 추론 간 모델 재사용 — 포그라운드에서 서비스 유지
- 목적: 사용자가 연속으로 다른 영상을 추론할 때 모델 로딩 오버헤드 제거
- 방법: 추론 완료 후 `stopSelf()` 하지 않고 모델을 유지, 내부 상태만 초기화
- 전제: 사용자가 앱을 포그라운드로 유지 중이라면, A 영상 추론 후 B 영상 추론을 이어서 할 가능성이 높음

### A와 B의 상충 관계
- A는 메모리를 아끼려고 release, B는 속도를 위해 release를 피함
- **`ForegroundObserver.isForeground`에 따라 분기**:
  - 포그라운드 → B 경로 (재사용 우선)
  - 백그라운드 → A 경로 (메모리 절감 우선)

## 각 컴포넌트 stateful 분석 및 재사용 가능 여부 (확인 완료)

### 네이티브 모델 레벨 — stateful 여부

| 모델 | 아키텍처 | stateful | 근거 |
|------|---------|:---:|------|
| SileroVAD | streaming RNN | **O** | hidden state 누적. `reset()` 제공 |
| Pyannote segmentation | temporal convolutional network | **X** | sliding-window 방식으로 각 윈도우 독립 처리. ONNX 그래프에 hidden state 텐서 없음 |
| 3D-Speaker embedding | ECAPA-TDNN 기반 feature extractor | **X** | 단일 forward pass로 임베딩 벡터 추출. 호출 간 상태 없음 |
| Whisper | Transformer encoder-decoder | **X** | KV-cache는 OfflineStream 내부에 격리. recognizer(ptr)는 모델 가중치(read-only)만 보유. sherpa-onnx Offline API는 설계 자체가 stateless |
| SenseVoice | Non-autoregressive encoder-only (CTC) | **X** | 단일 forward pass로 전체 전사 생성. 호출 간 상태 없음 |

**확인 결과: SileroVAD를 제외한 모든 모델이 stateless. release 없이 네이티브 인스턴스 그대로 재사용 가능.**

#### 확인 근거 상세

**Whisper**: Transformer encoder는 self-attention 기반으로 호출 간 상태 없음. decoder의 KV-cache는 `OfflineStream` 내부에 격리되며, stream은 매 호출마다 생성/해제. sherpa-onnx의 Offline/Online 분리 설계에서 Offline은 명시적으로 stateless batch processing을 의미. (출처: Whisper 논문 "Robust Speech Recognition via Large-Scale Weak Supervision", Radford et al., 2022; sherpa-onnx API 설계)

**SenseVoice**: Non-autoregressive encoder-only 모델. autoregressive decoder 없이 단일 forward pass로 전체 토큰 생성. 호출 간 누적 상태 구조적으로 불가. (출처: FunAudioLLM 기술 보고서, 2024; sherpa-onnx에서 OfflineRecognizer로만 지원)

**Speaker Diarization (Pyannote + 3D-Speaker)**: `OfflineSpeakerDiarization.process()`는 self-contained — 내부에서 segmentation → embedding 추출 → agglomerative clustering을 수행하고, 모든 중간 데이터는 로컬 변수. reset()이 없는 이유는 초기화할 내부 상태가 없기 때문. 단, 호출 간 화자 ID 일관성은 보장되지 않음(독립 클러스터링) — 현재 파이프라인에서 VAD 세그먼트별 독립 처리이므로 문제없음. (출처: sherpa-onnx C++ 구현 `offline-speaker-diarization-pyannote-impl.h`의 `Process()` 구조, sherpa-onnx 공식 예제)

> **참고**: sherpa-onnx C++ 소스(`offline-speaker-diarization-pyannote-impl.h`)에서 `Process()`가 로컬 변수만 사용하는지 직접 확인하면 SD stateless에 대한 최종 검증이 가능함.

### Kotlin 래퍼 레벨 — 재사용 시 초기화 필요 상태

| 컴포넌트 | release() 멱등 | 네이티브 stateless | 재사용 시 초기화 대상 |
|----------|:-:|:-:|------|
| SileroVad | `isInitialized` 가드 있음 | X (stateful) | `vad.reset()` 호출 |
| SpeakerDiarization | `isInitialized` 가드 있음 | O | 없음 — 네이티브 인스턴스 그대로 재사용 |
| Whisper | `isInitialized` 가드 있음 | O | `transcribedResult.clear()`, `timeInfo` 초기화, `isUsed = false` |
| SenseVoice | `isInitialized` 가드 있음 | O | Whisper와 동일 |
| Punctuation | `isInitialized` 가드 있음 | O | 없음 (이미 lazy init, stateless 호출) |

> **검증 완료**: 5개 컴포넌트 모두 `release()`에 `if (isInitialized)` 가드가 이미 존재함. 별도 가드 추가 불필요.

## 구현 계획

### 1단계: SR에 resetState() 메서드 추가
- `SpeechRecognition` 추상 클래스에 `open fun resetState()` 추가
- Whisper, SenseVoice에서 override: `transcribedResult.clear()`, `timeInfo` 초기화, `isUsed = false`
- recognizer는 유지 (release하지 않음)

**영향 파일:**
- `features/core/.../inference/speechRecognition/api/SpeechRecognition.kt`
- `features/core/.../inference/speechRecognition/Whisper.kt`
- `features/core/.../inference/speechRecognition/SenseVoice.kt`

### 2단계: cancelAndReInitialize() 정리 + initializeSpeechRecognition() 보강
- **cancelAndReInitialize()**: stateless 모델의 불필요한 release 제거 + phase-release 상태 대응
  - `speakerDiarization.release()` + `speakerDiarization.initialize()` → `speakerDiarization.initialize()` 만 호출 (내부 가드가 이미 초기화된 경우 스킵, phase-release로 해제된 경우 재초기화)
  - `speechRecognition.release()` → `speechRecognition.resetState()`로 대체 (네이티브 인스턴스 유지, Kotlin 상태만 초기화)
  - `vad.reset()` → `vad.initialize()` + `vad.reset()` 으로 변경. `initialize()` 내부 가드가 이미 초기화된 경우 스킵하고, phase-release로 해제된 경우 재초기화 후 reset
  - `initializeSpeechRecognition()` → 유지 (모델 타입 변경 대응)
  - Channel cancel 후 재생성 → 유지 (이전 상태 제거를 위해 반드시 필요)
  - **phase-release 후 재진입 시나리오**: 포그라운드→백그라운드→포그라운드를 반복하면 VAD+SD는 phase-release로 해제되고 SR은 유지될 수 있음. 이때 다음 추론 시 `cancelAndReInitialize()`가 호출되면:
    - VAD: `isInitialized == false` → `initialize()`가 재로딩 → `reset()`으로 상태 초기화
    - SD: `isInitialized == false` → `initialize()`가 재로딩
    - SR: `isInitialized == true` → `initialize()` 내부 가드로 스킵, `resetState()`로 Kotlin 상태만 초기화
    - 각 컴포넌트의 내부 `isInitialized` 가드가 자연스럽게 분기를 처리하므로 별도 조건문 불필요
- **initializeSpeechRecognition()**: modelType 변경 시 일관성 보장
  - 현재: `::speechRecognition.isInitialized && modelType == 기존` → return (동일 모델이면 스킵)
  - 추가: `::speechRecognition.isInitialized && modelType != 기존` → 기존 SR `release()` 후 새 SR `initialize()`
  - 변경 전후 모두 커버하도록 로직 정리:
    ```kotlin
    private suspend fun initializeSpeechRecognition() {
        val model = localSettingDataSource.getSelectedSpeechRecognitionModel().first()
        val modelType = SpeechRecognitionAvailableModel.findByName(model)

        if (::speechRecognition.isInitialized) {
            if (speechRecognition.availableSpeechRecognition == modelType) return
            speechRecognition.release()  // 모델 타입이 변경된 경우 기존 해제
        }

        speechRecognition = speechRecognitionProviders[modelType]!!.get().apply {
            initialize()
        }
    }
    ```
- **SpeechToTranscription.isInitialized 프로퍼티 제거** (SSOT 원칙)
  - 각 AI 모델 클래스(SileroVad, SpeakerDiarization, Whisper, SenseVoice, Punctuation)가 내부에 `isInitialized` 가드를 이미 보유
  - 오케스트레이터인 SpeechToTranscription이 별도로 `isInitialized`를 관리하면 이중 상태 → SSOT 위반
  - `initialize()`: 기존 `if (isInitialized) return` 가드 제거. 각 컴포넌트의 `initialize()` 내부 가드가 중복 호출을 방지
  - `release()`: `if (!::speechRecognition.isInitialized) return` — lateinit 초기화 여부만 체크하여 `UninitializedPropertyAccessException` 방지. 각 컴포넌트의 `release()` 내부 가드가 이중 해제 방지
  - `TranscribeService.onStartCommand()`: `if (speechToTranscription.isInitialized)` 참조 제거 필요 → `initialize()` 호출이 성공하면 바로 `processSubtitle()` 진행하도록 흐름 변경

**영향 파일:**
- `features/core/.../SpeechToTranscription.kt`

### 3단계: SpeechToTranscription에 phase-release 분기 로직 추가
- `isForeground` 상태를 참조할 수 있도록 (생성자 파라미터, 콜백, 또는 Hilt 주입)
- `processExtractedAudioWithVad()` 끝: 백그라운드일 때만 `vad.release()` + `speakerDiarization.release()`
- `transcribe()` 끝: 백그라운드면 `speechRecognition.release()`, 포그라운드면 `speechRecognition.resetState()`
- Punctuation: 백그라운드면 사용 후 즉시 release, 포그라운드면 유지 (이미 lazy init이므로 비용 낮음)
- **`initialize()`도 동일 패턴 적용**: 각 컴포넌트의 `initialize()` 내부 가드가 이미 초기화된 경우 스킵, phase-release로 해제된 경우 재초기화를 자동으로 처리. 따라서 `initialize()`는 무조건 모든 컴포넌트의 `initialize()`를 호출하면 됨

**영향 파일:**
- `features/core/.../SpeechToTranscription.kt`

### 4단계: DataStore에 추론 중 VideoItem 캐싱 추가
- **목적**: `START_STICKY`로 서비스 재생성 시 `intent == null`이므로, 추론 진행 중이던 VideoItem 정보를 내부 저장소에서 복원
- **data.proto 변경**:
  ```protobuf
  message PlayerPreferences {
    ...
    InferenceVideoItem pending_inference_video = 6;
  }

  message InferenceVideoItem {
    string uri = 1;
    int64 id = 2;
    string thumbnail_path = 3;
    string date = 4;
  }
  ```
- **LocalSettingDataSource 변경**:
  - `setPendingInferenceVideo(videoItem)` — 추론 시작 시 캐싱
  - `getPendingInferenceVideo(): Flow<InferenceVideoItem?>` — 복원 시 읽기
  - `clearPendingInferenceVideo()` — 추론 완료/실패 시 제거

**영향 파일:**
- `data/src/main/proto/data.proto`
- `data/src/main/kotlin/.../datasource/local/LocalSettingDataSource.kt`

### 5단계: TranscribeService 서비스 생명주기 변경
- **`START_STICKY` 반환**: `onStartCommand()`에서 `START_NOT_STICKY` → `START_STICKY`
- **추론 시작 시 VideoItem 캐싱**: `processSubtitle()` 진입 전 `localSettingDataSource.setPendingInferenceVideo(videoItem)` 호출
- **추론 완료/실패 시 캐시 제거**: `extractAudioAndTranscribe()`의 `onSuccess`/`onFailure`에서 `clearPendingInferenceVideo()` 호출
- **intent == null 복구 (START_STICKY 재생성)**:
  ```kotlin
  // onStartCommand에서 videoItem == null이고 intent == null인 경우
  // → localSettingDataSource.getPendingInferenceVideo()로 캐시된 VideoItem 복원
  // → 복원 성공 시 처음부터 추론 재실행 (checkpoint가 아닌 전체 재실행)
  // → 복원 실패 시 (캐시 없음) stopSelf()
  ```
- **추론 완료 후 서비스 생명주기 — 포그라운드/백그라운드 서비스 전환**:
  - **추론 완료 시 (앱 포그라운드)**: `stopForeground(STOP_FOREGROUND_REMOVE)` 호출 → 서비스가 백그라운드 서비스로 전환, 알림 제거, 모델은 메모리에 유지 (대기 모드)
  - **추론 완료 시 (앱 백그라운드)**: 기존대로 `release()` + `stopSelf()`
  - **대기 모드 중 앱이 백그라운드로 전환**: Android 8+의 백그라운드 실행 제한에 의해 약 1분 후 시스템이 서비스 자동 중지 → `onDestroy()` → `release()`. 수동 타이머 불필요 — 시스템이 메모리 상황에 따라 자연스럽게 관리
  - **대기 모드 중 앱이 다시 포그라운드로 복귀 (시스템 중지 전)**: 모델이 아직 메모리에 있으므로 그대로 재사용 가능. 새 추론 요청 시 `startForeground()` 재호출로 포그라운드 서비스로 승격
  - **대기 모드 중 시스템이 서비스를 중지한 후 앱 복귀**: 서비스가 이미 종료됨. 새 추론 요청 시 서비스 재생성 → 모델 재로딩 (일반적인 첫 실행 흐름)
- **대기 모드 진입/탈출 흐름 요약**:
  ```
  추론 완료 (포그라운드)
    → stopForeground(STOP_FOREGROUND_REMOVE)  [대기 모드 진입]
    → 백그라운드 서비스로 계속 실행, 모델 유지

  대기 모드 + 앱 백그라운드 전환
    → 시스템이 ~1분 후 자동 중지 → onDestroy() → release()

  대기 모드 + 새 onStartCommand 수신
    → startForeground()  [포그라운드 서비스로 재승격]
    → cancelAndReInitialize()  [Kotlin 상태 초기화, 모델 재사용]
    → 즉시 추론 시작
  ```

**영향 파일:**
- `features/gallery/.../TranscribeService.kt`

## 미확인 사항

1. **각 모델의 네이티브 메모리 크기**: VAD, SD, SR 각각의 메모리 점유량을 프로파일링하면 phase-release의 실질적 효과를 정량화 가능

## 영향받는 파일 전체 목록

| 파일 | 변경 유형 |
|------|----------|
| `features/core/.../inference/speechRecognition/api/SpeechRecognition.kt` | resetState() 추가 |
| `features/core/.../inference/speechRecognition/Whisper.kt` | resetState() override |
| `features/core/.../inference/speechRecognition/SenseVoice.kt` | resetState() override |
| `features/core/.../SpeechToTranscription.kt` | isInitialized 제거, release() lateinit 체크, cancelAndReInitialize() 정리, initializeSpeechRecognition() modelType 변경 처리, phase-release 분기, isForeground 참조 |
| `data/src/main/proto/data.proto` | `InferenceVideoItem` 메시지 + `pending_inference_video` 필드 추가 |
| `data/src/main/kotlin/.../datasource/local/LocalSettingDataSource.kt` | setPendingInferenceVideo, getPendingInferenceVideo, clearPendingInferenceVideo 추가 |
| `features/gallery/.../TranscribeService.kt` | START_STICKY, intent null 복구, VideoItem 캐싱/복원, 서비스 생명주기 변경, 대기 모드 |