Aideo 앱 개발(25.12 ~ 26.02) 까지의 주요 변경점들을 기록하려 합니다.

# 비디오 -> 자막 End-to-End Pipeline 구축

## TTS 추론에 Sherpa-onnx 이용

[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 는 샤오미에서 개발 및 maintaining 하는 Onnx 기반 음성 AI 추론 런타임 통합 라이브러리 입니다.

sherpa-onnx 를 이용하여 앱 내에서 활용하는 음성 AI 모델은 다음과 같습니다.

- Silero-VAD : VAD(Voice Activity Detection: 음성 활동 감지)
- pyannote-segmentation-3.0 : Speaker Diarization 의 segmentation
- 3d-speaker-embedding : Speaker Diarization(화자 분리) 의 embedding
- SenseVoice : Speech Recognition(음성 인식 -> 텍스트 추론)
- Whisper : Speech Recognition
- Punctuation : 구두점 삽입(중국어에만 적용)

### Sherpa-onnx & AI 모델 활용

앱의 주요 기능인 `Video -> 자막 생성`의 End-to-End Pipeline 은 다음과 같습니다.

1. `mediaCodec` 로 비디오의 음성 트랙 추출
2. `Silero-VAD` 로 오디오에서 최대 9.5초 분량의 음성을 감지
3. 유효 음성 길이를 그대로 `Speech Recognition` 하면, 동 시간대의 자막 길이가 너무 길기 때문에 분할을 위한 `Speaker Diarization` 을 수행
  1. `pyannote-segmentation` 으로 무음을 기준으로 음성을 분할
  2. `3d-speaker-embedding` 으로 화자별-음성 기준으로 embedding
  3. 화자별-음성 clustering
4. 분할된 `화자별-음성`을 사용자에 의해 선택된 `Speech Recognition` 모델(SenseVoice/Whisper)로 Text 추론
5. 각 추론 결과를 StringBuilder 로 병합 후 `.srt` 생성 및 내부 저장소에 저장

### Pipeline Details

End-to-End Pipeline 은 추론 소요 시간이 길기 때문에 `ForegroundService` 에서 실행되며, 3가지 단위로 분할하여 비동기로 수행합니다.

- 비디오의 음성 트랙 추출 : MediaCodec 의 decoder 로 raw 음성 데이터를 30초 분량씩 모아, `16000 Sample Rate & Float32 type` 으로 변환한 후 AudioBuffer Channel 로 전송
- AudioBuffer Channel : VAD 입력 구조에 맞게 WindowSize(512) 만큼 Audio 를 분할하여 Inference Channel 로 전송
- Inference Channel : 분할된 Audio 를 VAD 실행 -> 최대 9.5초 분량의 Audio를 Speaker Diarization 실행 -> `화자별-음성` Mapped Audio 를 Speech Recognition 실행 후 StringBuilder 에 append

추론이 모두 수행되면, StringBuilder 의 char 들을 String 으로 변환하여 `.srt` 포맷의 자막 파일로 내부 저장소에 저장합니다.

## Sherpa-onnx 를 이용하지 않는 seq2seq 번역 추론

`Speech Recognition` 후 변환된 `.srt` 파일의 번역을 위해 앱에서는 `MLkit` 과 facebook 의 `m2_m100` 모델을 활용합니다.

sherpa-onnx 은 음성 관련 모델만 지원하기 때문에, sherpa-onnx 에서 이미 활용하고 있는 onnx-runtime native library 를 이용하여 `seq2seq` 아키텍처로 만들어진 m2m100 모델의 추론 로직을 작성하였습니다.

cpp 코드는 AI 를 최대한 활용하여 작성한 후, 효율성을 기준으로 review -> update 과정을 거쳤습니다. 이후 ndk 빌드를 위한 CMake 스크립트를 작성하고, JNI Wrapper 클래스를 생성하여 통합 하였습니다.

결과적으로 비디오 플레이어 화면내에서 사용자에 의해 선택되어진 번역 언어 및 번역 모델(MLkit/m2m100)로 자막 파일의 번역을 수행 및 저장하는 과정에 이용됩니다.
