# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release AAB (for Play Store)
./gradlew bundleRelease

# Run all unit tests
./gradlew test

# Run tests for a specific module
./gradlew :features:core:testDebugUnitTest

# Build a specific module
./gradlew :features:gallery:assembleDebug
```

### Local Properties Required

`local.properties` must define the following keys for the build to succeed:
- `signing.storeFile`, `signing.storePassword`, `signing.keyAlias`, `signing.keyPassword`
- `adMob.test.appId`, `adMob.test.rewardId`, `adMob.test.unitId`
- `adMob.real.appId`, `adMob.real.rewardId`, `adMob.real.unitId`
- `play.serviceAccountJsonPath`

## Architecture Overview

Aideo is a multi-module Android app (Kotlin, Jetpack Compose, Hilt, MVVM/MVI) that creates subtitle (SRT) files from videos through an end-to-end AI pipeline: audio extraction → VAD → speaker diarization → speech recognition → punctuation → translation.

### Module Map

| Module | Role |
|--------|------|
| `:app` | Entry point: `MainActivity`, navigation graph, AdMob, in-app update |
| `:features:core` | Core AI pipeline, C++ JNI (ONNX/M2M100), media extraction |
| `:features:gallery` | Video picker UI + `TranscribeService` (ForegroundService) |
| `:features:library` | List of completed subtitle files |
| `:features:player` | ExoPlayer/Media3 video playback with subtitles |
| `:features:setting` | AI model selection, subscription, terms |
| `:data` | DataStore (proto3), `LocalFileDataSource`, `MediaRepository` |
| `:design` | Shared resources (strings, icons, themes) |

### AI Pack Modules (Play for AI Delivery)

Delivered on-demand via Google Play:
- `:ai_speech_base` — Silero VAD, punctuation, diarization ONNX models
- `:ai_speech_whisper` — Whisper model files
- `:ai_speech_sensevoice` — SenseVoice model files
- `:ai_translation` — M2M100 translation model files

### QNN Dynamic Feature Modules

Each module delivers Qualcomm HTP-accelerated model binaries for a specific Snapdragon SoC. They contain `qnn_stub/` (QNN shared libraries) and `qnn_models/<soc>/` (compiled model `.so` + `.bin`):

| Module | SoC Target |
|--------|-----------|
| `:htp_v69_sm8450` | SM8450 |
| `:htp_v69_sm8475` | SM8475 |
| `:htp_v73_sm8550` | SM8550 |
| `:htp_v75` | SM8650 |
| `:htp_v79` | SM8750 |
| `:htp_v81` | SM8850 |

Device targeting is configured in `app/device_targeting_config.xml`. The `AvailableSoCModel` enum in `features/core` maps `Build.SOC_MODEL` to the appropriate module.

### Core AI Pipeline

The pipeline is orchestrated by `TranscribeService` (ForegroundService in `:features:gallery`) which calls `SpeechToTranscription` and `TranslationManager`:

```
Video URI
  → MediaFileManager.extractAudioData()      [MediaCodec, IO dispatcher]
  → extractedAudioChannel (Channel<FloatArray>)
  → SileroVad.acceptWaveform()               [VAD, ONNX via OnnxRuntime JNI]
  → SpeakerDiarization.process()             [sherpa-onnx JNI]
  → inferenceAudioChannel (Channel<SingleSpeechSegment>)
  → SpeechRecognition.transcribe()           [Whisper or SenseVoice via sherpa-onnx / QNN]
  → Punctuation.addPunctuationOnSrt()        [ONNX, optional]
  → SpeechToTranscription.storeSubtitleFile() → .srt file
  → TranslationManager.translateSubtitle()   [ML Kit or M2M100 C++ JNI]
```

### Inference Abstractions

- `SpeechRecognition` (abstract class in `features/core/.../speechRecognition/api/`) — implemented by `Whisper` and `SenseVoice`; injected as `Map<SpeechRecognitionAvailableModel, Provider<SpeechRecognition>>` via Hilt multibinding
- `Translation` (abstract class in `features/core/.../translation/api/`) — implemented by `MlKitTranslation` and `M2M100`; selected at runtime based on user setting
- `QnnAccelerator` interface — applied to models that support Qualcomm HTP acceleration; `socModel: AvailableSoCModel` controls which QNN binary to load

### Convention Plugins (`build-logic/`)

Custom Gradle plugins applied in module `build.gradle.kts` files:
- `jinProject.android.application` — app module config
- `jinProject.android.library` — library module config
- `jinProject.android.hilt` — adds Hilt dependencies/kapt
- `jinProject.android.compose` — Compose compiler + dependencies
- `jinProject.android.dynamic-feature` — Dynamic Feature module config
- `jinProject.android.ai-pack` — AI Pack module config

### Navigation

Compose Navigation with typed routes. Top-level destinations: Gallery, Library, Setting. Player is navigated to via deep link (`aideo://app/player/<uri>`) from `TranscribeService` when transcription completes.

### Key Libraries

- **sherpa-onnx** (`libsherpa-onnx-jni.so`) — VAD, diarization, Whisper/SenseVoice inference
- **ONNX Runtime** (`libonnxruntime.so`) — M2M100 translation, punctuation
- **sentencepiece** (`libsentencepiece.so`) — tokenization for M2M100
- **Media3/ExoPlayer** — video playback
- **ML Kit Translation** — on-device translation alternative to M2M100
- **Firebase Analytics + Crashlytics**, **AdMob**, **Play Billing**, **Play In-App Update**
