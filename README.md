<a href="https://play.google.com/store/apps/details?id=jinproject.aideo.app">
	<img src="https://img.shields.io/badge/PlayStore-v1.0.0-4285F4?style=for-the-badge&logo=googleplay&logoColor=white&link=https://play.google.com/store/apps/details?id=jinproject.aideo.app" />
</a>

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-blue.svg)](https://kotlinlang.org)

[![AGP](https://img.shields.io/badge/AGP-8.10.0-green.svg)](https://gradle.org/)

[![minSdkVersion](https://img.shields.io/badge/minSdkVersion-30-red)](https://developer.android.com/distribute/best-practices/develop/target-sdk)

[![targetSdkVersion](https://img.shields.io/badge/targetSdkVersion-36-orange)](https://developer.android.com/distribute/best-practices/develop/target-sdk)

<img src="documentation/screenshot_total.png" />

# 개발 기간

2025.12 ~ 2026.02

# 앱 소개

Aideo 는 AI 로 비디오의 자막을 생성해 주는 앱 입니다.

# Why?

새로운 앱에서는 이전에 경험해보지 못했던 비디오 플레이어(Exoplayer) 를 활용해보고 싶었습니다. 그러던 와중에 On-Device AI 에 대해 접하게 되었고, 이 두가지 기술을 결합한 비즈니스를 구현해보고 싶어졌습니다.

무엇이 좋을까? 고민 하던 와중에 비디오의 자막을 생성해주는 기능으로 가닥을 잡았고, 이 기능을 시작점으로 여러가지 비디오와 AI 를 결합한 기능들을 제공해주는 앱으로 Aideo 라는 이름을 결정하게 되었습니다.

Aideo 는 최근 많이 보였던 보안 이슈들을 민감하게 생각하는 사용자들을 타겟으로 합니다. 로컬 기기내에 설치된 AI 모델로 자막을 생성해 주기 때문에, 사용자는 민감한 데이터들이 노출될 염려를 할 필요가 없습니다. 물론, AI 모델 자체의 크기가 UX 와 PlayStore 정책에 문제가 될 수 있기 때문에, Google 에서 제안하는 여러 Play Delivery 를 결합하여 이 문제들을 보완하고 있습니다.

# 주요 기능

- **자막 생성** : 비디오의 자막을 생성해 주기 위해서 비디오로 부터 음성을 추출,  추출된 음성을 AI 모델 입력에 맞게 전처리, AI 모델을 활용한 추론 로직, 추론 결과인 텍스트를 `.srt` 포맷으로 후처리, 원하는 언어로 번역, 자막 파일 저장 까지의 End-to-End Pipeline 을 구축하고, 이를 사용자에게 간편한 방식으로 제공합니다.

아직 Aideo 에서는 자막 생성외의 별다른 기능은 제공되지 않습니다.

# Stacks

| Category | Skill Set |
| ----- | ----- |
| Language | Kotlin |
| UI toolkit | Compose |
| Architecture | Google-Recommended Architecture |
| Design Pattern | MVVM, MVI |
| Asynchronous | Kotlinx.Coroutines, Kotlinx.Coroutines.Flow |
| Dependency Injection | Hilt |
| Data | DataStore(proto3) |
| Google | InAppPurchase, InAppUpdate, Admob |
| Firebase | Firebase-Analytics, Firebase-Crashlytics |
| CI/CD | Git hooks |

# What Did I Do?

[비디오 -> 자막 End-to-End Pipeline 구축](documentation/review/비디오_자막_End-to-End_Pipeline_구축.md)

[클래스 설계와 목적](documentation/review/클래스_설계_목적.md)