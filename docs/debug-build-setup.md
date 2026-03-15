# Debug 빌드에서 Play Asset Delivery / DFM 없이 직접 실행

## Context

AI 모델 파일이 크기 때문에 Play Asset Delivery(4개 AI Pack)와 Dynamic Feature Module(6개 QNN HTP 모듈)로 분산 배포 중. 이로 인해 Android Studio에서 직접 Run/Debug가 불가능하고, bundletool 명령어를 사용해야 하며 Profiler 등 IDE 도구를 쓸 수 없는 문제가 있음.

목표: `debug` 빌드 타입에서는 모든 assets을 APK에 직접 포함하고 PAD/DFM 없이 동작하도록 설정.

---

## 발견된 문제

### 문제 1: `ai_speech_sensevoice`의 `models#group_base` 디렉토리
`ai_speech_sensevoice/src/main/assets/models#group_base/sense_voice_model.int8.onnx`

PAD는 `#group_base` 접미사를 제거하고 `models/`로 전달하지만, debug에서 srcDirs로 직접 포함하면 `models#group_base/`라는 디렉토리명이 그대로 유지됨. 이렇게 되면 `context.assets.list("models")`가 이 파일을 찾지 못해 SenseVoice 초기화 크래시 발생.

→ **Gradle copy task로 빌드 시 `models#group_base/` → `models/`로 정규화**

### 문제 2: UI 레이어의 팩 상태 체크 (치명적)
`VideoGridContent`와 `LibraryVideoGridList`는 영상 클릭 시 `getAiPackManager().getPackStates()`로 팩 상태를 확인 후 `COMPLETED`일 때만 `TranscribeService`를 시작함. debug에서는 팩이 Play로 설치된 게 아니므로 상태가 `NOT_INSTALLED` → 전사 시작 불가.

→ **`isAiPackReady()` 헬퍼 함수 추가: debug sourceset에서 항상 `true` 반환**

---

## 변경 파일 목록

| # | 파일 | 변경 유형 |
|---|------|---------|
| 1 | `app/build.gradle.kts` | 수정 |
| 2 | `features/core/src/main/kotlin/.../utils/AiPackUtils.kt` | 수정 (함수 추가) |
| 3 | `features/core/src/debug/kotlin/.../utils/AiPackUtils.kt` | 신규 생성 |
| 4 | `features/gallery/.../VideoGridContent.kt` | 수정 |
| 5 | `features/library/.../LibraryVideoGridList.kt` | 수정 |

---

## 구현 상세

### Step 1: `app/build.gradle.kts`

#### 1-1. `assetPacks` / `dynamicFeatures` — release/bundle 빌드에서만 선언

`assetPacks`/`dynamicFeatures`는 AGP에서 build type별 설정이 불가능한 최상위 속성이므로,
Gradle task 이름으로 release 빌드 여부를 판단해 조건부 선언.

```kotlin
val isReleaseBuild = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
}

if (isReleaseBuild) {
    assetPacks += listOf(
        ":ai_speech_base", ":ai_translation",
        ":ai_speech_whisper", ":ai_speech_sensevoice"
    )
    dynamicFeatures += setOf(
        ":htp_v69_sm8475", ":htp_v69_sm8450", ":htp_v73_sm8550",
        ":htp_v75", ":htp_v79", ":htp_v81",
    )
}
```

#### 1-2. debug sourceSets + sensevoice 정규화 copy task

```kotlin
// sensevoice: models#group_base/ → models/ 로 정규화
val copySenseVoiceDebugAssets = tasks.register<Copy>("copySenseVoiceDebugAssets") {
    from("$rootDir/ai_speech_sensevoice/src/main/assets/models#group_base")
    into(layout.buildDirectory.dir("generated/sensevoice_debug_assets/models"))
}
tasks.whenTaskAdded {
    if (name == "mergeDebugAssets") dependsOn(copySenseVoiceDebugAssets)
}

sourceSets {
    getByName("debug") {
        assets.srcDirs(
            "../ai_speech_base/src/main/assets",
            "../ai_speech_whisper/src/main/assets",
            // ai_speech_sensevoice 는 copy task 결과물로 포함
            layout.buildDirectory.dir("generated/sensevoice_debug_assets").get().asFile,
            "../ai_translation/src/main/assets",
            "../htp_v73_sm8550/src/main/assets",
        )
    }
}
```

debug APK의 `assets/`에 포함되는 것:
- `models/` — 모든 ONNX 모델 파일 (4개 Pack 통합, 파일명 충돌 없음)
- `qnn_stub/` — QNN shared libraries
- `qnn_models/sm8550/` — SoC 특화 컴파일 모델

---

### Step 2: `features/core/src/main/.../AiPackUtils.kt` 수정

`isAiPackReady()` 함수 추가 (release 빌드용: 실제 팩 설치 여부 동기 확인):

```kotlin
fun Context.isAiPackReady(packName: String): Boolean {
    return getAiPackManager().getPackLocation(packName) != null
}
```

---

### Step 3: `features/core/src/debug/.../AiPackUtils.kt` (신규)

전체 파일을 main sourceset과 동일하게 작성하되 두 함수를 오버라이드:

```kotlin
package jinproject.aideo.core.utils

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.aipacks.AiPackManagerFactory
import com.google.android.play.core.aipacks.AiPackStates
import jinproject.aideo.core.inference.AiModelConfig
import timber.log.Timber
import java.io.File

// 핵심 오버라이드: Play 없이 local assets에서 경로 반환
fun Context.getPackAssetPath(packName: String): String? {
    val modelsDir = File(filesDir, AiModelConfig.MODELS_ROOT_DIR)
    if (!modelsDir.exists()) modelsDir.mkdirs()
    val fileNames = assets.list(AiModelConfig.MODELS_ROOT_DIR) ?: emptyArray()
    Timber.d("Debug: extracting ${fileNames.size} model files to internal storage")
    for (fileName in fileNames) {
        val outFile = File(modelsDir, fileName)
        if (!outFile.exists()) {
            assets.open("${AiModelConfig.MODELS_ROOT_DIR}/$fileName").use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
    return filesDir.absolutePath
}

// debug에서는 항상 설치됨으로 처리
fun Context.isAiPackReady(packName: String): Boolean = true

// 나머지는 main과 동일
fun Context.getAiPackManager() = AiPackManagerFactory.getInstance(this)
fun Context.getAiPackStates(packName: String): Task<AiPackStates> =
    getAiPackManager().getPackStates(listOf(packName))
fun Task<AiPackStates>.getPackStatus(packName: String): Int? =
    result.packStates()[packName]?.status()
```

**동작 원리**:
- `modelsDir`가 없으면 생성, 파일별 존재 여부 확인 후 미추출 파일만 복사
- `filesDir.absolutePath` 반환 → `"${getPackAssetPath(pack)}/models/xxx.onnx"` 패턴 그대로 동작
- `extractQnnStubsToInternalStorage()`, `copyAssetToInternalStorage()` — 이미 `context.assets` 직접 사용하므로 변경 없이 동작

---

### Step 4: `features/gallery/.../VideoGridContent.kt` 수정

비동기 `getPackStates()` 체크를 `isAiPackReady()` 동기 체크로 교체:

```kotlin
onClick = {
    if (context.isAiPackReady(AiModelConfig.SPEECH_BASE_PACK)) {
        context.startForegroundService(
            Intent(context, TranscribeService::class.java).apply {
                putExtra("videoItem", video.toVideoItem())
            }
        )
    } else {
        context.getAiPackManager().fetch(listOf(AiModelConfig.SPEECH_BASE_PACK))
        localShowSnackBar.invoke(
            SnackBarMessage(
                headerMessage = context.getString(R.string.download_failed_or_pending),
                contentMessage = context.getString(R.string.download_retry_request)
            )
        )
    }
}
```

---

### Step 5: `features/library/.../LibraryVideoGridList.kt` 수정

VideoGridContent와 동일한 패턴으로 교체.

---

## 파일 구조 요약

```
app/build.gradle.kts                          ← 조건부 PAD/DFM + debug srcDirs + copy task

features/core/src/
  main/kotlin/.../utils/AiPackUtils.kt        ← isAiPackReady() 추가
  debug/kotlin/.../utils/AiPackUtils.kt       ← getPackAssetPath() + isAiPackReady()=true 오버라이드 [신규]

features/gallery/.../VideoGridContent.kt      ← isAiPackReady() 동기 체크 사용
features/library/.../LibraryVideoGridList.kt  ← isAiPackReady() 동기 체크 사용
```

---

## 주의 사항

1. **파일명 충돌 없음**: 4개 AI Pack의 `models/` 파일명이 모두 고유함
2. **debug APK 크기**: 모든 AI 모델 포함 → 수백 MB 이상, 로컬 개발용만 사용
3. **task 이름 조건**: Gradle configuration cache 활성화 시 task 이름 체크가 캐시될 수 있으나, 이 프로젝트에서 현재 사용하지 않으면 문제 없음
4. **SettingAIModelScreen**: pack 다운로드 UI가 debug에서 "not installed" 표시될 수 있으나, 전사 기능 자체에는 영향 없으므로 이번 계획에서 제외

---

## 검증 방법

1. Android Studio에서 `Run 'app'` (assembleDebug + install) 실행
2. 앱에서 영상 선택 → TranscribeService 정상 시작 확인
3. AI 파이프라인 (음성 인식/번역) 동작 확인
4. Android Studio Profiler CPU/Memory 프로파일링 확인
5. 중단점 디버깅 확인
6. `./gradlew bundleRelease` 정상 빌드 확인 (PAD/DFM 구조 유지)
