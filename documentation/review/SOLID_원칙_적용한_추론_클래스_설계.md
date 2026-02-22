# SOLID 원칙을 적용한 추론 관련 클래스 설계

각 `Feature` 모듈들은 `Feature-Core` 모듈에 존재하는 AI 모델 추론 관련 클래스들을 이용합니다.

추론 관련 전체 클래스 및 의존성 그래프는 다음과 같이 설계합니다.

<img src="AI_class_dependency_graph.png" />

e.g) 

- `feature/Gallery` 모듈의 TranscribeService 는 자막 추론을 위해 `features/core/SpeechToTranscription` 을 의존
- `SpeechToTranscription` 에서 선택된 음성 AI 모델(Sensevoice/Whisper) 클래스와 SpeakerDiarization, VAD 클래스를 이용하여 `음성 -> 자막 추론` End-to-End Pipeline 을 실행
- TranscribeService 에서 추론 결과를 번역하기 위해 `features/core/Translation` 을 의존
- Translation 에서는 선택된 번역 모델을 이용하여 내부 저장소의 자막 파일을 선택된 언어로 번역한 후, 생성된 번역 파일을 저장합니다.

## 설계 목적

위 의존성 그래프 설계를 통해 다음 목적들을 실현합니다.

1. 단일 책임 원칙(SRP) & 인터페이스 분리 원칙(ISP)
- **Manager Class** : `AI Model Category(SpeechToText, Translation etc)` 만의 책임을 갖습니다. 해당 `AI Model Category` 를 실현하기 위해 필요한 AI 모델들을 의존합니다. Manager 클래스는 `AI Model Category` 를 실현하기 위해 필요한 변경사항 외에는, 내부 구조의 변경이 일어나지 않습니다.
- **AI Model Class** : 특정 `AI Model(SenseVoice, Whisper, Silero-VAD etc)` 만의 책임을 갖습니다. 특정 `AI Model` 의 입출력 구조와 같은 구체적 정보가 변경하지 않는 한, 내부 구조의 변경이 일어나지 않습니다.
- **추론 Runtime Library 관련 Class** : AI Model 의 특정 Format(Onnx, LiteRT) 을 읽고, 이해 할 수 있는 `추론 Runtime Library(Onnx-Runtime)` 만의 책임을 갖습니다. `추론 Runtime Library` 의 버전 변경으로 인한 내부 변경이 일어나지 않는 한, 이 클래스의 변경이 일어나지 않습니다.

2. 개방-폐쇄 원칙(OCP)

개방-폐쇄 원칙의 핵심은 가장 변할 가능성이 적은 컴포넌트를 중심으로 의존성 방향을 설계해야 한다는 점입니다. 

이 원칙을 근거로 가장 변경될 가능성이 적은 `Onnx Native Runtime Library < AI 모델 클래스 < AI Category Manager 클래스 < Feature` 방향으로 의존성을 설계했습니다.

Native Runtime Library 는 외부 의존성이지만, 특별한 문제가 없는 한 버전 변경 가능성이 적으며, 변경이 있다 하더라도 캡슐화로 최대한 외부 요소를 그대로 드러내지 않도록 합니다.

AI Model 이 Runtime Library 를 의존하도록 구현함으로써, 특정 Model 을 어떠한 Format(Onnx, LiteRT) 으로 추출하여도 그에 맞는 Runtime Library 관련 Class 를 선택함으로써 팩토리 처럼 사용할 수 있도록 설계했습니다.

3. 리스코프 치환 원칙(LSP)

LSP 의 핵심은 잘 설계된 추상화 입니다.  

잘 설계 되지 못한 추상화는 구현체를 생성할 때 변경 사항을 야기하고, 이것이 구현되는 다른 모든 클래스에 영향을 미칩니다. 

이를 방지하기 위해, 먼저 앱에서 필요로 하는 핵심 기능을 명확히 설정합니다. 이를 기반으로 핵심 기능을 실현하기 위한 명확한 책임을 나눕니다.

나뉘어진 명확한 책임을 기반으로 추상화(고수준에서 필요한 핵심 기능을 설정)한 뒤에 이를 구체화 하는 과정을 거쳤습니다.

위 과정을 수행하여 설계된 클래스 구조는 다음과 같습니다.

앱에서 필요로 하는 핵심 기능은 `비디오의 자동 자막 생성` 입니다. 요구사항이 복잡하기 때문에 3가지의 작은 문제로 나눕니다.

1. 비디오로 부터 음성을 추출
2. 추출된 음성을 텍스트로 추론
3. 추론 결과를 번역 및 저장

해당 책임을 기반으로 4가지의 고수준 Component 에 대해 각각의 핵심 기능 추상화 합니다.

1. **MediaFileManager** : 앱 내의 비디오를 가져오거나, 비디오로 부터 음성을 추출하는, __Media와 관련된 핵심 기능을 수행하는 클래스__ 입니다.
2. **AI 모델** : AI 모델의 입·출력 구조에 맞게 __전·후 처리__ 를 수행하고, 모델의 아키텍처에 따라 분할된 여러 모델을 직렬로 트리거하는 클래스 입니다.
3. **추론 Runtime Library** : AI Model 의 특정 Format 을 읽고, 이해할 수 있는 `추론 Runtime Library` 의 필요한 기능들을 캡슐화 하는 클래스 입니다. 모델의 초기화, 리소스 해제, 추론을 직접 실행합니다.

이후, 3가지 Component 를 기반으로 핵심 기능을 구현하기 위한 각 구현체 클래스들을 생성하였습니다.

- MediaFileManager(Interface)
  - AndroidMediaFileManager(Impl)
- SpeechRecognition(Interface)
  - SenseVoice(Impl)
  - Whisper(Impl)
- Translation(Interface)
  - MLKit(Impl)
  - M2M100(Impl)

그리고 **AI Category Manager** 컴포넌트를 생성합니다. 이는 설명한 바와 같이, 필요한 AI 모델들을 주입 받아, 실현하고자 하는 목적을 구현하는 Manager 클래스 입니다. 예를 들어, SpeechRecognition, VAD, SpeakerDiarization 의 특정 클래스들을 주입받아 `음성 -> 텍스트 추론(SpeechToText)` 카테고리를 구현합니다.

- SpeechToTranscription
- TranslationManager

4. 의존성 역전 원칙(DIP)

- **MediaFileManager** : 기기 내부의 `Media File I/O` 을 다룹니다. 이는 Platform 의존성(Android 의 경우 MediaCodec)이 강하며, 특정 Platform 에서 필요한 구현체를 주입할 수 있도록 추상화 합니다.
- **AI 모델** : 이는 구체적인 AI 모델의 의존성이 강합니다. 특정 `AI Model Category Manager` 에서 필요한 AI 구현체 모델을 주입할 수 있도록 추상화 합니다.
- **AI Model Runtime Library** : 이는 AI Model 의 특정 Format 에 의존성이 강합니다. 특정 AI Model 이 어떤 Format(Onnx, LiteRT, etc) 으로 생성되었는가에 따라 필요한 `추론 Runtime Library` 를 구성합니다.

