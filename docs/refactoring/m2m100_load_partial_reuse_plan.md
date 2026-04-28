# M2M100 load 단계별 재사용 구현 계획

## 목적

`M2M100Translator::load()`는 ONNX 모델, tokenizer, vocab, language token config를 순차적으로 로드한다.
현재 구조에서는 중간 단계에서 실패하면 이미 성공한 이전 단계도 다음 `load()` 호출에서 다시 수행해야 한다.

이 문서의 목표는 `load()` 과정에서 성공한 단계를 객체 내부에 유지하고, 다음 `load()` 호출에서 동일한 입력 경로가 들어오면 해당 단계를 재사용하도록 변경하는 것이다.

## 구현 원칙

1. 성공한 단계는 즉시 멤버 상태에 반영한다.
2. 실패한 단계 이후의 작업은 수행하지 않고 `false`를 반환한다.
3. 전체 단계가 모두 성공하기 전까지 `Translator::isLoaded()`는 `false`여야 한다.
4. 재사용 여부는 포인터 존재 여부만으로 판단하지 않고, 해당 상태를 만든 입력 path가 같은지 함께 확인한다.
5. path가 달라진 단계는 다시 로드한다.
6. 다시 로드하다가 실패하면 기존 성공 상태를 무조건 삭제하지 않는다.
7. 명시적 `release()` 호출 시에는 모든 부분 로드 상태와 path cache를 초기화한다.

## 대상 파일

- `features/core/src/main/cpp/m2m100_translator.h`
- `features/core/src/main/cpp/m2m100_translator.cpp`
- `features/core/src/main/cpp/encoder_decoder_with_past.h`
- `features/core/src/main/cpp/encoder_decoder_with_past.cpp`
- `features/core/src/main/cpp/onnxruntime_inference.h`
- `features/core/src/main/cpp/onnxruntime_inference.cpp`
- `features/core/src/main/cpp/tokenizer.h`
- `features/core/src/main/cpp/tokenizer.cpp`
- `features/core/src/main/cpp/language_token_map.h`
- `features/core/src/main/cpp/language_token_map.cpp`

## 최종 load 흐름

`M2M100Translator::load()`는 다음 순서로 동작한다.

```cpp
bool M2M100Translator::load(...) {
    setLoaded(false);

    if (!decoder_.load(...)) {
        return false;
    }

    if (!tokenizer_.load(...)) {
        return false;
    }

    if (!loadLanguageTokens(tokenizerConfigPath)) {
        return false;
    }

    eosTokenId_ = tokenizer_.getEosTokenId();
    setLoaded(true);
    return true;
}
```

핵심은 `load()` 시작 시 `release()`를 호출하지 않는 것이다.
대신 전체 완료 전에는 항상 `setLoaded(false)`로 내려둔다.

## ONNX 세션 재사용

### 현재 문제

`OnnxInference::loadEncoderDecoderModel()`은 세션을 멤버에 직접 대입한다.

```cpp
encoderSession_ = std::make_unique<Ort::Session>(...);
decoderSession_ = std::make_unique<Ort::Session>(...);
decoderWithPastSession_ = std::make_unique<Ort::Session>(...);
```

이 구조에서는 decoder 생성 중 예외가 발생하더라도 encoder는 이미 생성된 상태로 남는다.
다만 현재는 이 상태를 의도적으로 관리하지 않기 때문에 다음 호출에서 재사용할 수 없다.

### 변경 방향

`OnnxInference`에 각 세션이 어떤 path로 로드되었는지 저장한다.

```cpp
std::string loadedEncoderPath_;
std::string loadedDecoderPath_;
std::string loadedDecoderWithPastPath_;
```

각 세션은 독립적으로 재사용한다.

```cpp
bool OnnxInference::loadEncoderSession(const char* encoderPath);
bool OnnxInference::loadDecoderSession(const char* decoderPath);
bool OnnxInference::loadDecoderWithPastSession(const char* decoderWithPastPath);
```

각 함수의 규칙은 동일하다.

```cpp
if (encoderSession_ && loadedEncoderPath_ == encoderPath) {
    return true;
}

auto session = std::make_unique<Ort::Session>(env_, encoderPath, sessionOptions_);
encoderSession_ = std::move(session);
loadedEncoderPath_ = encoderPath;
return true;
```

예외가 발생하면 해당 단계는 `false`를 반환한다.
이때 이전에 성공한 다른 세션은 유지한다.

### loadEncoderDecoderModel() 변경

`loadEncoderDecoderModel()`은 세션별 로드 함수를 순서대로 호출한다.

```cpp
bool OnnxInference::loadEncoderDecoderModel(...) {
    if (!loadEncoderSession(encoderPath)) {
        return false;
    }
    if (!loadDecoderSession(decoderPath)) {
        return false;
    }
    if (!loadDecoderWithPastSession(decoderWithPastPath)) {
        return false;
    }
    return true;
}
```

### release() 변경

`release()`는 세션과 path cache를 함께 초기화한다.

```cpp
void OnnxInference::release() {
    encoderSession_.reset();
    decoderSession_.reset();
    decoderWithPastSession_.reset();

    loadedEncoderPath_.clear();
    loadedDecoderPath_.clear();
    loadedDecoderWithPastPath_.clear();
}
```

## EncoderDecoderWithPast 재사용

`EncoderDecoderWithPast`는 `OnnxInference`를 감싸는 계층이므로, public API는 크게 바꾸지 않는다.

```cpp
bool EncoderDecoderWithPast::load(
        const char* encoderPath,
        const char* decoderPath,
        const char* decoderWithPastPath) {
    return inference_.loadEncoderDecoderModel(
            encoderPath,
            decoderPath,
            decoderWithPastPath
    );
}
```

세션별 재사용 판단은 `OnnxInference`가 담당한다.
`EncoderDecoderWithPast`는 로드 성공 여부만 상위에 전달한다.

## Tokenizer 재사용

### 현재 문제

`Tokenizer::load()`은 SentencePiece model과 vocab을 순차 로드한다.
vocab 로드 중 실패하면 SentencePiece model은 이미 교체되었을 수 있고, vocab map도 부분적으로 채워질 수 있다.

### 변경 방향

`Tokenizer`에 로드된 path를 저장한다.

```cpp
std::string loadedSpModelPath_;
std::string loadedVocabPath_;
```

SentencePiece와 vocab을 독립 단계로 나눈다.

```cpp
bool loadSentencePiece(const char* spModelPath);
bool loadVocab(const char* vocabPath);
```

### SentencePiece 로드 규칙

```cpp
if (sp_ && loadedSpModelPath_ == spModelPath) {
    return true;
}

auto nextProcessor = std::make_unique<sentencepiece::SentencePieceProcessor>();
auto status = nextProcessor->Load(spModelPath);
if (!status.ok()) {
    return false;
}

sp_ = std::move(nextProcessor);
loadedSpModelPath_ = spModelPath;
return true;
```

새 processor를 로컬에서 만든 뒤 성공 시에만 멤버에 반영한다.
따라서 실패해도 기존 `sp_`는 손상되지 않는다.

### vocab 로드 규칙

vocab도 로컬 map에 먼저 파싱한다.

```cpp
std::unordered_map<std::string, int64_t> nextVocab;
std::unordered_map<int64_t, std::string> nextReverseVocab;
```

파싱이 끝난 뒤 성공 시에만 commit한다.

```cpp
vocab_ = std::move(nextVocab);
reverseVocab_ = std::move(nextReverseVocab);
loadedVocabPath_ = vocabPath;
```

특수 토큰도 로컬 변수로 먼저 계산한 뒤 commit한다.

```cpp
int64_t nextBosTokenId = 0;
int64_t nextPadTokenId = 1;
int64_t nextEosTokenId = 2;
int64_t nextUnkTokenId = 3;
```

### load() 변경

```cpp
bool Tokenizer::load(const char* spModelPath, const char* vocabPath) {
    if (!loadSentencePiece(spModelPath)) {
        return false;
    }
    if (!loadVocab(vocabPath)) {
        return false;
    }
    return true;
}
```

## LanguageTokenMap 재사용

### 현재 문제

`M2M100Translator::load()` 안에서 `tokenizer_config.json`을 직접 파싱하고 `languageTokens_`에 바로 등록한다.
파싱 중간에 예외가 발생하면 language token map이 부분적으로 채워질 수 있다.

### 변경 방향

`M2M100Translator`에 config path를 저장한다.

```cpp
std::string loadedTokenizerConfigPath_;
```

언어 토큰 로드는 별도 private 함수로 분리한다.

```cpp
bool loadLanguageTokens(const char* tokenizerConfigPath);
```

같은 path가 이미 로드되어 있고, map이 비어 있지 않다면 재사용한다.

```cpp
if (loadedTokenizerConfigPath_ == tokenizerConfigPath && languageTokens_.isNotEmpty()) {
    return true;
}
```

이를 위해 `LanguageTokenMap`에 상태 확인 함수를 추가한다.

```cpp
bool empty() const;
```

### 파싱 규칙

기존 `languageTokens_`에 바로 쓰지 않고 로컬 map에 먼저 등록한다.

```cpp
LanguageTokenMap nextLanguageTokens;
```

파싱 성공 후에만 commit한다.

```cpp
languageTokens_ = std::move(nextLanguageTokens);
loadedTokenizerConfigPath_ = tokenizerConfigPath;
```

언어 토큰이 하나도 없으면 실패로 처리한다.
M2M100 번역에서는 source/target language token이 필수이기 때문이다.

## M2M100Translator 상태 관리

### load() 시작

`load()` 시작 시 전체 번역 가능 상태만 false로 내린다.

```cpp
setLoaded(false);
```

`decoder_`, `tokenizer_`, `languageTokens_`는 즉시 release하지 않는다.
이들이 바로 재사용 대상이다.

### load() 실패

중간 실패 시에는 실패 지점 이전의 성공 상태를 유지한다.

예:

1. encoder 성공
2. decoder 실패
3. `load()`는 `false` 반환
4. 다음 호출에서 encoder path가 같으면 encoder는 skip
5. decoder부터 다시 시도

### load() 성공

모든 단계가 성공한 뒤에만 `setLoaded(true)`를 호출한다.

```cpp
eosTokenId_ = tokenizer_.getEosTokenId();
setLoaded(true);
return true;
```

### release()

명시적 release는 전체 리소스 해제 의미를 유지한다.

```cpp
void M2M100Translator::release() {
    decoder_.release();
    tokenizer_.release();
    languageTokens_.clear();
    loadedTokenizerConfigPath_.clear();
    Translator::release();
}
```

이를 위해 `Tokenizer::release()`를 추가한다.

```cpp
void Tokenizer::release();
```

`Tokenizer::release()`는 다음 상태를 초기화한다.

- `sp_`
- `vocab_`
- `reverseVocab_`
- special token ids
- `loadedSpModelPath_`
- `loadedVocabPath_`

## 상태 예시

### 최초 로드 중 decoder 실패

입력:

```text
encoder=A/encoder.onnx
decoder=A/decoder.onnx
decoderWithPast=A/decoder_with_past.onnx
```

결과:

```text
encoderSession_: loaded
decoderSession_: empty
decoderWithPastSession_: empty
isLoaded_: false
```

다음 동일 입력 `load()`:

```text
encoder: reuse
decoder: load
decoderWithPast: load
```

### tokenizer vocab 실패

결과:

```text
ONNX sessions: loaded or reused
SentencePiece: loaded or reused
vocab: previous valid vocab or empty
isLoaded_: false
```

다음 동일 입력 `load()`:

```text
ONNX sessions: reuse
SentencePiece: reuse
vocab: retry
```

### tokenizer_config 실패

결과:

```text
ONNX sessions: loaded or reused
Tokenizer: loaded or reused
LanguageTokenMap: previous valid map or empty
isLoaded_: false
```

다음 동일 입력 `load()`:

```text
ONNX sessions: reuse
Tokenizer: reuse
LanguageTokenMap: retry
```

## 주의할 점

### isLoaded()의 의미

부분 로드 상태가 있어도 `isLoaded()`는 `false`일 수 있다.
`isLoaded()`는 "번역 가능한 완성 상태"만 의미해야 한다.

### translate() 방어 조건

`translate()`는 기존처럼 `isLoaded()`가 false이면 즉시 실패해야 한다.
부분 로드 상태에서 번역을 시도하면 안 된다.

### path 문자열 비교

path는 `std::string`으로 저장한다.
`const char*` 포인터 주소 비교를 하면 안 된다.

```cpp
loadedEncoderPath_ == std::string(encoderPath)
```

혹은 함수 시작에서 문자열로 변환한다.

```cpp
std::string nextEncoderPath(encoderPath);
```

### null path 처리

현재 JNI 호출 경로상 path가 null로 들어오지 않는다는 전제가 있어도, native 계층에서는 방어하는 것이 낫다.
각 load 함수는 path가 null이거나 빈 문자열이면 `false`를 반환한다.

## 구현 순서

1. `LanguageTokenMap::empty()` 추가
2. `Tokenizer::release()` 추가
3. `Tokenizer`에 path cache 필드 추가
4. `Tokenizer::load()`을 `loadSentencePiece()`와 `loadVocab()` 기반으로 변경
5. `OnnxInference`에 세션별 path cache 필드 추가
6. `OnnxInference`에 세션별 private load 함수 추가
7. `OnnxInference::loadEncoderDecoderModel()`을 세션별 재사용 흐름으로 변경
8. `OnnxInference::release()`에서 path cache까지 초기화
9. `M2M100Translator`에 `loadedTokenizerConfigPath_` 추가
10. `M2M100Translator::loadLanguageTokens()` private 함수 추가
11. `M2M100Translator::load()`에서 `release()` 없이 단계별 재사용 흐름 적용
12. `M2M100Translator::release()`에서 tokenizer와 config path까지 초기화
13. native build 실행

## 검증 계획

### 정적 확인

```bash
rg "loadLanguageTokens|loadedTokenizerConfigPath_|loadedEncoderPath_|loadedSpModelPath_" features/core/src/main/cpp
```

의도한 상태 필드와 함수가 모두 연결되어 있는지 확인한다.

### 빌드 확인

```bash
./gradlew :features:core:externalNativeBuildDebug --no-daemon
```

### 수동 시나리오 확인

실제 모델 파일이 준비된 환경에서 다음 케이스를 확인한다.

1. decoder path를 잘못 넣어 실패시킨다.
2. 같은 encoder path와 올바른 decoder path로 다시 호출한다.
3. 로그상 encoder 재사용, decoder 로드가 수행되는지 확인한다.
4. tokenizer config path를 잘못 넣어 실패시킨다.
5. 같은 ONNX/tokenizer path와 올바른 config path로 다시 호출한다.
6. ONNX/tokenizer 단계가 재사용되는지 확인한다.

## 완료 기준

다음 조건을 만족하면 완료로 본다.

1. `load()` 중간 실패 후에도 성공한 이전 단계가 유지된다.
2. 다음 `load()`에서 동일 path 단계는 재로드하지 않는다.
3. path가 달라진 단계는 새로 로드한다.
4. 전체 성공 전까지 `isLoaded()`는 false다.
5. `release()` 호출 시 모든 리소스와 path cache가 초기화된다.
6. native build가 성공한다.
