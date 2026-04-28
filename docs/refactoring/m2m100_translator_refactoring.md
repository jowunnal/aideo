# M2M100Translator 리팩토링 구현 계획

## 1. Context

`features/core/src/main/cpp/m2m100_translator.{h,cpp}` 의 `M2M100Translator` 는 facebook/M2M-100 ONNX 모델 추론 전용 클래스로, 다음 네 가지 책임이 한 클래스에 섞여 있다:

1. 번역의 가장 일반적 인터페이스 — `translate(text, src, tgt)`, 언어 ↔ 토큰 ID 매핑, special token, isLoaded 상태.
2. encoder-decoder 아키텍처의 디코딩 루프 — encoder run → decoder init → decoder_with_past autoregressive loop, KV cache 관리.
3. transformer 공통 동작인 logits → 다음 토큰 변환 — 현재 greedy.
4. M2M100 모델 특화 — encoder/decoder input 구성, `tokenizer_config.json` 의 `__ko__` 형식 언어 토큰 파싱, 모델 상수 12 layers / 16 heads / 1024 hidden / 128112 vocab.

향후 다른 번역 모델 추가 시 — 그것이 encoder-decoder (NLLB, MarianMT) 든, decoder-only 든, encoder-only 든 — 같은 base 가 디코딩 루프 골격을 강제하면 안 된다. 모델 아키텍처는 모델 구현체가 책임지고 정의해야 한다. 동시에, **logits → 다음 토큰** 변환은 transformer 기반 모델 전반의 공통 동작이므로, 특정 아키텍처 컴포넌트(`EncoderDecoderWithPast`) 안에 가두지 않고 별도 컴포넌트로 분리해 다른 아키텍처에서도 재사용한다.

이번 리팩토링의 디자인 원칙:

- **`Translator` base 는 thin abstract interface.** 모든 번역 모델의 진짜 공통점인 `translate(text, srcLang, tgtLang) → string` 추상 메서드, `release()` 기본 구현, `isLoaded()` 만 갖는다. tokenizer, 언어 토큰 매핑(`langToTokenId_`), special token (eos, pad 등) 은 모델마다 종류·구성·사용법이 달라서 base 에 두지 않고 각 구체 클래스가 직접 보유. 이렇게 하면 ML Kit / MarianMT-pair 처럼 언어 토큰 개념이 없는 모델도 Translator 를 만족시킬 수 있다.
- **encoder-decoder 디코딩 루프는 별도 컴포넌트 `EncoderDecoderWithPast` 로 분리** — M2M100Translator 가 has-a (composition) 로 보유.
- **logits → 다음 토큰 변환은 별도 클래스 `TokenSelector` (abstract) + `GreedyTokenSelector` (default 구현) 로 분리** — 어떤 아키텍처 컴포넌트(`EncoderDecoderWithPast`, 향후 `EncoderOnly`, `DecoderOnly` 등)든 멤버로 보유해서 호출.
- **상속(is-a) 관계는 `Translator ← M2M100Translator` 한 단계만**. 아키텍처와 token selection 은 상속이 아니라 구성으로 표현.
- `OnnxInference`, `Tokenizer`, `m2m100_jni.cpp`, Kotlin/Hilt 계층은 이번 작업에서 **건드리지 않는다**.
- 기존 코드의 `//TODO` 주석은 모두 **그대로 이식**한다 (이동 위치는 §6 매핑 표 참조).

기대 효과:

- decoder-only / encoder-only 모델 추가 시 `Translator` 만 상속하고, 디코딩 루프는 자체 구현 또는 별도 utility 클래스 (`DecoderOnly` 등) 를 만들어 composition. `TokenSelector` 는 그대로 재사용.
- 기존 NLLB / MarianMT 같은 encoder-decoder 모델은 `EncoderDecoderWithPast` 와 `GreedyTokenSelector` 를 그대로 재사용해 모델별 input 구성만 작성.
- M2M100Translator 의 .cpp 파일은 약 361줄 → ~120줄로 축소.
- JNI 호환 100% 유지 (`m2m100_jni.cpp`, `M2M100Native.kt`, Hilt 무수정).

---

## 2. 변경 / 신규 파일 목록

신규 (6개):
- `features/core/src/main/cpp/translator.h`
- `features/core/src/main/cpp/translator.cpp`
- `features/core/src/main/cpp/token_selector.h`
- `features/core/src/main/cpp/token_selector.cpp`
- `features/core/src/main/cpp/encoder_decoder_with_past.h`
- `features/core/src/main/cpp/encoder_decoder_with_past.cpp`

수정 (3개):
- `features/core/src/main/cpp/m2m100_translator.h` — Translator 상속 전환, EncoderDecoderWithPast 멤버 추가, 중복 멤버/메서드 제거.
- `features/core/src/main/cpp/m2m100_translator.cpp` — 디코딩 루프 본체 삭제, `decoder_.generate()` 호출로 교체, load() 내 setter 들을 base 헬퍼로 교체, TODO 보존.
- `features/core/src/main/cpp/CMakeLists.txt` — 신규 .cpp 세 개 등록.

변경 없음 (확인용):
- `features/core/src/main/cpp/m2m100_jni.cpp`
- `features/core/src/main/cpp/onnxruntime_inference.{h,cpp}`
- `features/core/src/main/cpp/tokenizer.{h,cpp}`
- Kotlin / Hilt / AI Pack 계층 일체 (`features/core/src/main/kotlin/.../inference/translation/`, `M2M100Native.kt`, `TranslationModule.kt`, `:ai_translation`).

---

## 3. 신규 파일 상세 설계

### 3.1 `translator.h`

```cpp
// Translator - 모든 번역 모델의 thin abstract interface.

#ifndef AIDEO_TRANSLATOR_H
#define AIDEO_TRANSLATOR_H

#include <string>
#include "logging.h"

#define LOG_TAG_TRANSLATOR "Translator"

class Translator {
public:
    virtual ~Translator();

    // 모든 번역 모델의 진짜 공통점 — 입력 텍스트를 srcLang→tgtLang 으로 번역.
    // srcLang/tgtLang 의 의미·처리 방식은 모델마다 다름:
    //   - M2M100/NLLB: 언어 코드를 토큰 ID 로 변환해서 input 에 prepend
    //   - MarianMT-pair / ML Kit: 모델 자체에 언어 쌍이 박혀 있어 검증·라우팅 용도
    virtual std::string translate(const std::string& text,
                                   const std::string& srcLang,
                                   const std::string& tgtLang,
                                   int maxLength = 256) = 0;

    virtual void release();
    bool isLoaded() const { return isLoaded_; }

    // load(...) 는 모델별로 시그니처가 다르므로 base 에 두지 않음.
    //   각 구체 클래스가 자신의 시그니처로 public load 를 가짐.

protected:
    void setLoaded(bool v) { isLoaded_ = v; }
    bool isLoaded_ = false;
};

#endif
```

### 3.2 `translator.cpp`

thin base 의 trivial implementation. `~Translator()` 와 `release()` 만.

```cpp
#include "translator.h"

Translator::~Translator() = default;

void Translator::release() {
    isLoaded_ = false;
}
```

### 3.3 `token_selector.h`

```cpp
// TokenSelector - logits → 다음 토큰 변환 전략 (transformer 공통).

#ifndef AIDEO_TOKEN_SELECTOR_H
#define AIDEO_TOKEN_SELECTOR_H

#include <vector>
#include <cstdint>

class TokenSelector {
public:
    virtual ~TokenSelector() = default;

    // logits 에서 다음 토큰 ID 를 선택.
    // - logits: shape [batch_size, decoder_seq_len, vocab_size] 의 평탄화된 1D 벡터.
    //           구현체가 마지막 위치 vocab 만 사용하든, 전체를 사용하든 자유.
    // - vocabSize: 모델의 vocab 크기.
    // - fallbackTokenId: logits 가 비었거나 비정상일 때 반환할 토큰(보통 eosTokenId).
    virtual int64_t select(const std::vector<float>& logits,
                            int64_t vocabSize,
                            int64_t fallbackTokenId) const = 0;
};

class GreedyTokenSelector : public TokenSelector {
public:
    int64_t select(const std::vector<float>& logits,
                    int64_t vocabSize,
                    int64_t fallbackTokenId) const override;
};

#endif
```

### 3.4 `token_selector.cpp`

`m2m100_translator.cpp:315-350` 의 `selectNextToken` 본체를 그대로 옮긴다. eosTokenId_ 멤버 의존을 fallbackTokenId 인자로 교체. 기존 TODO 주석 보존:

```cpp
#include "token_selector.h"
#include <algorithm>

//TODO: logits 는 vocab table 전체에 대해 현재의 decode 결과가 얼마나 적합한가를 점수(확률)로 나타낸 값들. 즉, vocab 크기가 크면 연산량이 많아짐.
//TODO: 그런데, GreedyTokenSelector::select() 함수 구현을 보면 greedy 하게 탐색하고 있음. 가장 높은 점수를 가진 토큰을 가져옴. 단순하고 빠르지만, 정확도에 의문점이 있음.
//TODO: 현재 추론의 정확도가 떨어지는 상황이기 때문에 개선의 여지가 있어 보임.
int64_t GreedyTokenSelector::select(const std::vector<float>& logits,
                                     int64_t vocabSize,
                                     int64_t fallbackTokenId) const {
    // Greedy decoding: 가장 높은 확률의 토큰 선택
    // logits shape: [batch_size, decoder의 입력 seq_len, vocab_size]

    if (logits.empty()) {
        return fallbackTokenId;
    }

    size_t logitsSize = logits.size();
    auto vocabSizeU = static_cast<size_t>(vocabSize);

    // decoder, decoder_with_past 의 마지막 입력 토큰의 vocab 만 사용(decoder 의 inputIds 에서 eos 는 무시)
    size_t lastTokenOffset = 0;
    if (logitsSize >= vocabSizeU) {
        lastTokenOffset = logitsSize - vocabSizeU;
    }

    // TODO: 애초에 vocabSizeU 와 offset 이 다르면 안됨. 다른 것에 대해서는 예외 처리가 되어야지, 이걸 마음대로 정상 처리하면 안됨.
    size_t actualVocabSize = std::min(vocabSizeU, logitsSize - lastTokenOffset);

    if (actualVocabSize == 0) {
        return fallbackTokenId;
    }

    int64_t maxIdx = 0;
    float maxVal = logits[lastTokenOffset];

    for (size_t i = 1; i < actualVocabSize; ++i) {
        if (logits[lastTokenOffset + i] > maxVal) {
            maxVal = logits[lastTokenOffset + i];
            maxIdx = static_cast<int64_t>(i);
        }
    }

    return maxIdx;
}
```

### 3.5 `encoder_decoder_with_past.h`

```cpp
// EncoderDecoderWithPast - encoder + decoder + decoder_with_past 3-model autoregressive 디코딩.

#ifndef AIDEO_ENCODER_DECODER_WITH_PAST_H
#define AIDEO_ENCODER_DECODER_WITH_PAST_H

#include <memory>
#include <string>
#include <vector>
#include <utility>
#include "logging.h"
#include "onnxruntime_inference.h"
#include "token_selector.h"

#define LOG_TAG_ENC_DEC_WITH_PAST "EncDecWithPast"

class EncoderDecoderWithPast {
public:
    EncoderDecoderWithPast(int numDecoderLayers,
                            int numHeads,
                            int hiddenSize,
                            int64_t vocabSize,
                            std::unique_ptr<TokenSelector> tokenSelector
                                = std::make_unique<GreedyTokenSelector>());
    ~EncoderDecoderWithPast();

    bool load(const char* encoderPath,
              const char* decoderPath,
              const char* decoderWithPastPath);
    void release();

    // 디코딩 generate. 반환: 생성된 토큰 시퀀스 (토크나이저 디코딩은 호출 측이 담당).
    // - initialDecoderInputIds: 모델별로 [eos, tgtLangId] (M2M100/NLLB) 또는 [pad] (MarianMT) 등.
    // - 종료 조건: 다음 토큰 == eosTokenId 또는 maxLength 도달.
    // - logits → 다음 토큰 선택은 생성자에 주입된 TokenSelector 사용 (default GreedyTokenSelector).
    std::vector<int64_t> generate(const std::vector<int64_t>& encoderInputIds,
                                   const std::vector<int64_t>& initialDecoderInputIds,
                                   int64_t eosTokenId,
                                   int maxLength);

private:
    // KV output 이름에서 레이어 인덱스와 타입을 파싱.
    // 이름 형식: "present.X.{decoder|encoder}.{key|value}"
    // 반환값: {레이어 인덱스, 타입 오프셋(0=dec_key, 1=dec_val, 2=enc_key, 3=enc_val)}, 실패 시 {-1, -1}
    static std::pair<int, int> parseKvOutputName(const std::string& name);

    std::unique_ptr<TokenSelector> tokenSelector_;
    OnnxInference inference_;
    int     numDecoderLayers_;
    int     numHeads_;
    int     hiddenSize_;
    int64_t vocabSize_;
};

#endif
```

### 3.6 `encoder_decoder_with_past.cpp`

`m2m100_translator.cpp:135-299` (디코딩 루프 단계 2~6) 와 `m2m100_translator.cpp:16-32` (parseKvOutputName) 를 그대로 이식. `selectNextToken` 호출은 `tokenSelector_->select(logits, vocabSize_, eosTokenId)` 로 교체. 기존 TODO 보존.

```cpp
#include "encoder_decoder_with_past.h"
#include <regex>

EncoderDecoderWithPast::EncoderDecoderWithPast(int numDecoderLayers,
                                                 int numHeads,
                                                 int hiddenSize,
                                                 int64_t vocabSize,
                                                 std::unique_ptr<TokenSelector> tokenSelector)
    : tokenSelector_(std::move(tokenSelector)),
      numDecoderLayers_(numDecoderLayers),
      numHeads_(numHeads),
      hiddenSize_(hiddenSize),
      vocabSize_(vocabSize) {
    // 모델 파라미터 설정 (현 m2m100_translator.cpp:34-39 의 책임 이동)
    inference_.setNumDecoderLayers(numDecoderLayers_);
    inference_.setNumHeads(numHeads_);
    inference_.setHiddenSize(hiddenSize_);
}

EncoderDecoderWithPast::~EncoderDecoderWithPast() {
    release();
}

bool EncoderDecoderWithPast::load(const char* encoderPath,
                                   const char* decoderPath,
                                   const char* decoderWithPastPath) {
    return inference_.loadEncoderDecoderModel(encoderPath, decoderPath, decoderWithPastPath);
}

void EncoderDecoderWithPast::release() {
    inference_.release();
}

// static
// (현 m2m100_translator.cpp:16-32 그대로)
std::pair<int, int> EncoderDecoderWithPast::parseKvOutputName(const std::string& name) {
    static const std::regex pattern(R"(present\.(\d+)\.(decoder|encoder)\.(key|value))");
    std::smatch match;
    if (std::regex_search(name, match, pattern) && match.size() == 4) {
        int layerIdx = std::stoi(match[1].str());
        bool isEncoder = (match[2].str() == "encoder");
        bool isValue   = (match[3].str() == "value");
        int typeOffset = (isEncoder ? 2 : 0) + (isValue ? 1 : 0);
        return {layerIdx, typeOffset};
    }
    return {-1, -1};
}

// 디코딩 루프 — 현 m2m100_translator.cpp:135-299 의 단계 2~6 + 단계 7 의 토큰 시퀀스 반환.
// 호출 측이 단계 1(encoder input 구성), 단계 7(tokenizer.decode) 을 담당.
std::vector<int64_t> EncoderDecoderWithPast::generate(
        const std::vector<int64_t>& encoderInputIds,
        const std::vector<int64_t>& initialDecoderInputIds,
        int64_t eosTokenId,
        int maxLength) {

    std::vector<int64_t> generatedTokens;

    try {
        auto encoderSeqLen = static_cast<int64_t>(encoderInputIds.size());
        //TODO: 어차피 1로 다 채워서 보내면, 무슨 의미가 있는 거지?
        std::vector<int64_t> attentionMask(encoderSeqLen, 1);

        // 단계 2: Encoder 실행
        auto encoderHiddenStates = inference_.runEncoder(
                encoderInputIds, attentionMask, 1, encoderSeqLen);
        if (encoderHiddenStates.empty()) {
            AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Encoder returned empty output");
            return generatedTokens;
        }

        // 단계 4: 첫 번째 Decoder 실행 (KV 캐시 초기화)
        auto decoderOutput = inference_.runDecoder(
                initialDecoderInputIds,
                attentionMask,
                encoderHiddenStates,
                1,
                static_cast<int64_t>(initialDecoderInputIds.size()),
                encoderSeqLen);
        if (decoderOutput.logits.empty()) {
            AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Decoder returned empty logits");
            return generatedTokens;
        }

        // 단계 5: 다음 토큰 선택
        int64_t nextToken = tokenSelector_->select(decoderOutput.logits, vocabSize_, eosTokenId);
        generatedTokens.push_back(nextToken);

        // 단계 6: Autoregressive generation with KV cache
        //TODO: onnxruntime_inference.cpp 내부에서 kvCache 에 대해 범용으로 사용하기 위해, 각 모델의 export 사항 별 달라지는 kvCache 규격을 normalization 한 것으로 보임
        //TODO: normalization 과정을 결과적으로 성능 오버헤드를 야기하니까, 이 부분을 사용하지 않는 선에서의 개선된 코드가 필요함.
        std::vector<std::vector<float>> allKVCache;
        std::vector<std::vector<int64_t>> allKVShapes;
        allKVCache.resize(numDecoderLayers_ * 4);
        allKVShapes.resize(numDecoderLayers_ * 4);

        if (decoderOutput.kvOutputNames.size() == decoderOutput.presentKeyValues.size()) {
            for (size_t i = 0; i < decoderOutput.presentKeyValues.size(); ++i) {
                auto [layerIdx, typeOffset] = parseKvOutputName(decoderOutput.kvOutputNames[i]);
                if (layerIdx >= 0 && layerIdx < numDecoderLayers_ && typeOffset >= 0) {
                    int targetIdx = layerIdx * 4 + typeOffset;
                    allKVCache[targetIdx] = std::move(decoderOutput.presentKeyValues[i]);
                    allKVShapes[targetIdx] = std::move(decoderOutput.presentKeyValueShapes[i]);
                } else {
                    AIDEO_LOGW(LOG_TAG_ENC_DEC_WITH_PAST, "Could not parse KV name: %s",
                                decoderOutput.kvOutputNames[i].c_str());
                }
            }

            //TODO allKVCache 로의 이동이 필요할까? 그냥 바로 decoderOutput 을 이용할 수는 없을까? 만약, 이 로직이 틀리면 emptySlot 발생 -> decoderWithPast 동작의 정확도가 보장이 안된다.
            int emptySlots = 0;
            for (int i = 0; i < numDecoderLayers_ * 4; ++i) {
                if (allKVCache[i].empty()) emptySlots++;
            }
            if (emptySlots > 0) {
                AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST,
                            "WARNING: %d KV cache slots are empty after initial setup!", emptySlots);
            }
        } else {
            for (size_t i = 0; i < decoderOutput.presentKeyValues.size(); ++i) {
                allKVCache[i] = std::move(decoderOutput.presentKeyValues[i]);
                allKVShapes[i] = std::move(decoderOutput.presentKeyValueShapes[i]);
            }
        }

        std::vector<int64_t> nextInputIds(1);

        for (int step = 0; step < maxLength - 1; ++step) {
            if (nextToken == eosTokenId) break;

            nextInputIds[0] = nextToken;

            auto nextOutput = inference_.runDecoderWithPast(
                    nextInputIds,
                    attentionMask,
                    encoderHiddenStates,  // TODO: 이미 kvCache 로 cross-attention 의 연산 결과가 있는데, 굳이 encoderHiddenStates 를 또 넘긴다?
                    allKVCache,
                    allKVShapes,
                    1,             // batch_size
                    encoderSeqLen  // TODO: attentionMask 길이와 같으므로 중복임. 제거 필요
            );

            if (nextOutput.logits.empty()) {
                AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST,
                            "DecoderWithPast returned empty logits at step %d", step);
                break;
            }

            nextToken = tokenSelector_->select(nextOutput.logits, vocabSize_, eosTokenId);
            generatedTokens.push_back(nextToken);

            // KV 캐시 업데이트 - (현 m2m100_translator.cpp:256-294 와 동일)
            if (nextOutput.kvOutputNames.size() == nextOutput.presentKeyValues.size()) {
                for (size_t i = 0; i < nextOutput.presentKeyValues.size(); ++i) {
                    auto [layerIdx, typeOffset] = parseKvOutputName(nextOutput.kvOutputNames[i]);
                    if (layerIdx >= 0 && layerIdx < numDecoderLayers_ && typeOffset >= 0) {
                        int targetIdx = layerIdx * 4 + typeOffset;
                        allKVCache[targetIdx] = std::move(nextOutput.presentKeyValues[i]);
                        allKVShapes[targetIdx] = std::move(nextOutput.presentKeyValueShapes[i]);
                    } else if (step == 0) {
                        AIDEO_LOGW(LOG_TAG_ENC_DEC_WITH_PAST,
                                    "Update: could not parse KV name: %s",
                                    nextOutput.kvOutputNames[i].c_str());
                    }
                }
            } else {
                size_t kvOutputSize = nextOutput.presentKeyValues.size();
                if (kvOutputSize == static_cast<size_t>(numDecoderLayers_ * 4)) {
                    allKVCache  = std::move(nextOutput.presentKeyValues);
                    allKVShapes = std::move(nextOutput.presentKeyValueShapes);
                } else if (kvOutputSize == static_cast<size_t>(numDecoderLayers_ * 2)) {
                    for (int i = 0; i < numDecoderLayers_; ++i) {
                        int allIdx = i * 4;
                        int decIdx = i * 2;
                        allKVCache[allIdx]     = std::move(nextOutput.presentKeyValues[decIdx]);
                        allKVCache[allIdx + 1] = std::move(nextOutput.presentKeyValues[decIdx + 1]);
                        allKVShapes[allIdx]     = std::move(nextOutput.presentKeyValueShapes[decIdx]);
                        allKVShapes[allIdx + 1] = std::move(nextOutput.presentKeyValueShapes[decIdx + 1]);
                    }
                } else {
                    AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST,
                                "Unexpected KV cache size: %zu", kvOutputSize);
                    break;
                }
            }
        }
    } catch (const std::exception& e) {
        AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "generate failed: %s", e.what());
    }

    return generatedTokens;
}
```

---

## 4. 수정 파일 상세

### 4.1 `m2m100_translator.h` (수정 후 전체)

```cpp
// M2M100 Translator - facebook/M2M-100 ONNX 추론 (encoder-decoder + SentencePiece + 언어 토큰).

#ifndef AIDEO_M2M100_TRANSLATOR_H
#define AIDEO_M2M100_TRANSLATOR_H

#include <string>
#include <unordered_map>
#include "logging.h"
#include "translator.h"
#include "tokenizer.h"
#include "encoder_decoder_with_past.h"

#define LOG_TAG_M2M100 "M2M100"

class M2M100Translator : public Translator {
public:
    M2M100Translator();
    ~M2M100Translator() override;

    // 모델 및 토크나이저 로드 (JNI 시그니처 그대로 유지)
    bool load(const char* encoderPath,
              const char* decoderPath,
              const char* decoderWithPastPath,
              const char* spModelPath,
              const char* vocabPath,
              const char* tokenizerConfigPath);

    // Translator override — lang 코드 → 토큰 ID 변환 후 translateSingle 위임.
    std::string translate(const std::string& text,
                          const std::string& srcLang,
                          const std::string& tgtLang,
                          int maxLength = 256) override;

    // 언어 코드가 이미 토큰 ID 로 해석된 경우 (JNI batch 경로 등).
    std::string translateSingle(const std::string& text,
                                 int64_t srcLangId,
                                 int64_t tgtLangId,
                                 int maxLength);

    // 언어 코드 ↔ 토큰 ID 매핑 — M2M100 의 `added_tokens_decoder` 형식 한정.
    bool    isLanguageSupported(const std::string& lang) const;
    int64_t getLanguageTokenId(const std::string& lang) const;

    void release() override;  // decoder_.release() + langToTokenId_.clear() + Translator::release()

private:
    EncoderDecoderWithPast decoder_;  // composition (has-a). default GreedyTokenSelector 자동 생성.
    Tokenizer tokenizer_;              // SentencePiece + vocab.json — M2M100 입력/출력 토큰화.
    std::unordered_map<std::string, int64_t> langToTokenId_;  // "ko" → 128022 등.

    // M2M100 special token — encoder/decoder input 구성에 사용.
    // base 가 아닌 모델별로 보유 (모델마다 필요한 토큰이 다름).
    int64_t eosTokenId_ = 2;

    // 모델 설정 (M2M100 학습값, facebook/m2m100/config.json 명시)
    static constexpr int     NUM_DECODER_LAYERS = 12;  // decoder layer 개수
    static constexpr int     NUM_HEADS          = 16;  // attention head 개수
    static constexpr int     HIDDEN_SIZE        = 1024;
    static constexpr int64_t VOCAB_SIZE         = 128112;
};

#endif
```

base 가 thin abstract 이므로 `tokenizer_`, `langToTokenId_`, `eosTokenId_`, `isLanguageSupported`, `getLanguageTokenId`, `translateSingle` 은 모두 `M2M100Translator` 자체 멤버/메서드로 유지. base 에서 가져오는 것은 `isLoaded_`/`setLoaded()`/`release()` 의 thin shell + `translate()` pure-virtual 만. 제거되는 기존 멤버/메서드: `inference_` (→ `decoder_` 안으로 이동), `padTokenId_` (사용처 없음), `isLoaded_` (base 가 보유), private `selectNextToken` (→ `GreedyTokenSelector::select`). `parseKvOutputName` static helper 도 `EncoderDecoderWithPast` 로 이동.

### 4.2 `m2m100_translator.cpp` (수정 후 전체)

```cpp
//
// Created by PC on 2026-01-18.
//

#include "m2m100_translator.h"
#include "json.hpp"
#include <fstream>

using json = nlohmann::json;

M2M100Translator::M2M100Translator()
    : decoder_(NUM_DECODER_LAYERS, NUM_HEADS, HIDDEN_SIZE, VOCAB_SIZE) {
    // EncoderDecoderWithPast 의 default 인자가 GreedyTokenSelector 자동 생성.
}

M2M100Translator::~M2M100Translator() {
    release();
}

bool M2M100Translator::load(const char* encoderPath,
                             const char* decoderPath,
                             const char* decoderWithPastPath,
                             const char* spModelPath,
                             const char* vocabPath,
                             const char* tokenizerConfigPath) {
    try {
        // 1. ONNX 모델 로드
        //TODO 만약, tokenizer 나 다른 path 에서 오류가 발생하면, 여기서 생성된 inference 는 메모리에 이미 올라갔는데 어떻게 되나? 또, 아래의 각 단계들도 마찬가지.
        if (!decoder_.load(encoderPath, decoderPath, decoderWithPastPath)) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Failed to load ONNX models");
            return false;
        }

        // 2. 토크나이저 로드 (SentencePiece model + vocab.json)
        if (!tokenizer_.load(spModelPath, vocabPath)) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Failed to load tokenizer");
            return false;
        }

        // 3. 언어 토큰 매핑 로드 (tokenizer_config.json의 added_tokens_decoder에서 추출)
        //TODO 2번째(토크나이저 로딩)단계 에서 한번에 처리해도 될 듯?
        std::ifstream file(tokenizerConfigPath);
        if (!file.is_open()) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Failed to open tokenizer_config.json");
            return false;
        }

        json config = json::parse(file);
        if (config.contains("added_tokens_decoder")) {
            auto& decoder = config["added_tokens_decoder"];
            for (auto& [idStr, tokenInfo] : decoder.items()) {
                const auto& content = tokenInfo["content"].get_ref<const std::string&>();
                // "__ko__" -> "ko"
                if (content.size() > 4 &&
                    content.compare(0, 2, "__") == 0 &&
                    content.compare(content.size() - 2, 2, "__") == 0) {
                    std::string langCode = content.substr(2, content.size() - 4);
                    //TODO 굳이 long long? 4바이트로도 될 것 같은디
                    langToTokenId_[langCode] = std::stoll(idStr);
                }
            }
        }

        // 특수 토큰 설정 (M2M100 자체 멤버 — base 에는 special token 개념 없음)
        eosTokenId_ = tokenizer_.getEosTokenId();
        setLoaded(true);
        return true;

    } catch (const std::exception& e) { //TODO 네이티브 메모리 공간에서 발생한 예외를 JVM 에서 잡을 수 없기 때문에(바로 크래시 발생) 예외 처리가 매우 중요
        AIDEO_LOGE(LOG_TAG_M2M100, "Failed to load M2M100: %s", e.what());
        return false;
    }
}

// Translator::translate override — 현 m2m100_translator.cpp:106-127 에서 isLoaded 체크 + lang→id 변환만.
std::string M2M100Translator::translate(const std::string& text,
                                         const std::string& srcLang,
                                         const std::string& tgtLang,
                                         int maxLength) {
    if (!isLoaded()) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Model not loaded");
        return "";
    }

    int64_t srcLangId = getLanguageTokenId(srcLang);
    int64_t tgtLangId = getLanguageTokenId(tgtLang);

    if (srcLangId == -1 || tgtLangId == -1) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Unsupported language: src=%s, tgt=%s",
                   srcLang.c_str(), tgtLang.c_str());
        return "";
    }

    return translateSingle(text, srcLangId, tgtLangId, maxLength);
}

//TODO 어차피 translateSingle 은 여기서만 호출하고, 그렇지 않다 하더라도 굳이 언어:언어토큰 으로의 맵핑을 따로 처리해야 할 이유가 있을까?
//TODO 함수를 나누는 목적이었다면, translate() 에서 굳이 언어토큰맵핑 만 따로 처리한건 뭘까? 애초에 translate() 내부의 여러 로직들도 구분 가능한 단위로 나눴어야 했을 것 같음.
std::string M2M100Translator::translateSingle(const std::string& text,
                                                int64_t srcLangId,
                                                int64_t tgtLangId,
                                                int maxLength) {
    if (!isLoaded()) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Model not loaded");
        return "";
    }

    try {
        // 1. M2M100 형식 encoder input: [srcLangId, ...textTokens, eos]
        //TODO addEos 는 아에 따로 할거면 빼는게 맞음. 쓸거면 아에 쓰는게 맞고. 굳이 이값을 인자로 쓸 수 있는데 밑에서 안쓰는건 문제임. 게다가 함수 자체는 작은 단위로 둬야 하니 그냥 빼는게 맞다고 생각함.
        auto textTokens = tokenizer_.encode(text, false);

        std::vector<int64_t> encoderInputIds;
        encoderInputIds.reserve(textTokens.size() + 2);
        encoderInputIds.push_back(srcLangId);
        encoderInputIds.insert(encoderInputIds.end(), textTokens.begin(), textTokens.end());
        encoderInputIds.push_back(eosTokenId_);

        // 2. M2M100 형식 initial decoder input: [eos, tgtLangId]
        std::vector<int64_t> initialDecoderInputIds = { eosTokenId_, tgtLangId };

        // 3. 디코딩
        auto generatedTokens = decoder_.generate(
                encoderInputIds, initialDecoderInputIds, eosTokenId_, maxLength);

        // 4. 토큰 디코딩
        return tokenizer_.decode(generatedTokens);

    } catch (const std::exception& e) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Translation failed: %s", e.what());
        return "";
    }
}

bool M2M100Translator::isLanguageSupported(const std::string& lang) const {
    return langToTokenId_.find(lang) != langToTokenId_.end();
}

// 현 m2m100_translator.cpp:307-313 그대로.
int64_t M2M100Translator::getLanguageTokenId(const std::string& lang) const {
    auto it = langToTokenId_.find(lang);
    if (it != langToTokenId_.end()) {
        return it->second;
    }
    return -1;
}

void M2M100Translator::release() {
    decoder_.release();
    langToTokenId_.clear();
    Translator::release();
}
```

`parseKvOutputName` static helper, 디코딩 루프 (135-299), `selectNextToken` (315-350), 기존 release (356-360) 모두 제거. `translate`, `translateSingle`, `getLanguageTokenId`, `isLanguageSupported` 는 base 가 아닌 `M2M100Translator` 자체 메서드로 유지 (`translate` 만 base 의 pure-virtual override).

### 4.3 `CMakeLists.txt`

`features/core/src/main/cpp/CMakeLists.txt:25-30` 의 `add_library(onnx-inference SHARED ...)` 블록을 수정:

```cmake
add_library(onnx-inference SHARED
        onnxruntime_inference.cpp
        tokenizer.cpp
        translator.cpp                  # 신규
        token_selector.cpp              # 신규
        encoder_decoder_with_past.cpp   # 신규
        m2m100_translator.cpp
        m2m100_jni.cpp
)
```

다른 부분(IMPORTED 라이브러리, target_link_libraries) 변경 없음.

---

## 5. 재사용할 기존 코드

이번 작업에서 **건드리지 않고 그대로 재사용**:

- `OnnxInference` (`onnxruntime_inference.h/.cpp`) — `loadEncoderDecoderModel`, `runEncoder`, `runDecoder`, `runDecoderWithPast`, `setNumDecoderLayers/Heads/HiddenSize`, `release`. `EncoderDecoderWithPast` 의 멤버로 그대로 보유.
- `Tokenizer` (`tokenizer.h/.cpp`) — `load`, `encode`, `decode`, `getEosTokenId`, `getPadTokenId`. `M2M100Translator` 의 멤버로 그대로 보유 (base 가 thin abstract 이므로 모델별 멤버). 메서드명 `load` 의 모델 결합은 코드 내 TODO 이지만 별도 작업.
- KV 이름 파싱 정규식 — `m2m100_translator.cpp:16-32` 의 `parseKvOutputName` 본체를 `EncoderDecoderWithPast::parseKvOutputName` static 메서드로 이동.
- Greedy 디코딩 본체 — `m2m100_translator.cpp:315-350` 의 `selectNextToken` 본체를 `GreedyTokenSelector::select` 로 이식.
- `nlohmann::json` (`json.hpp`) — M2M100 의 `tokenizer_config.json` 파싱은 `m2m100_translator.cpp::load()` 안에 그대로 유지.
- JNI 진입점 5개 — `m2m100_jni.cpp` 의 native 함수들은 `g_translator->load/translate/translateSingle/getLanguageTokenId/isLanguageSupported/release` 를 그대로 호출. 메서드 시그니처를 유지하므로 무수정.

---

## 6. 기존 TODO 주석 이식 매핑

| 원 위치 | 이동 위치 |
|---|---|
| `m2m100_translator.cpp:56` (load 의 인플라이트 로딩 실패 시 메모리) | `m2m100_translator.cpp::load()` 의 동일 위치 |
| `m2m100_translator.cpp:69` (토크나이저 로딩 단계 통합) | `m2m100_translator.cpp::load()` 의 동일 위치 |
| `m2m100_translator.cpp:86` (long long 사용) | `m2m100_translator.cpp::load()` 의 `registerLanguageToken` 호출 직전 |
| `m2m100_translator.cpp:98` (네이티브 예외 처리 중요성) | `m2m100_translator.cpp::load()` 의 catch 블록 직전 |
| `m2m100_translator.cpp:104-105` (translate 함수 분할 의문) | `m2m100_translator.cpp::translateSingle()` 직전 |
| `m2m100_translator.cpp:141` (encode addEos 인자 일관성) | `m2m100_translator.cpp::translateSingle()` 의 `tokenizer_.encode(text, false)` 호출 직전 |
| `m2m100_translator.cpp:146` (attentionMask 1로 채우는 의미) | `encoder_decoder_with_past.cpp::generate()` 의 `attentionMask` 생성 직전 |
| `m2m100_translator.cpp:178-180` (greedy 정확도 의문) | `token_selector.cpp::GreedyTokenSelector::select()` 직전 |
| `m2m100_translator.cpp:187-188` (KV cache normalization 오버헤드) | `encoder_decoder_with_past.cpp::generate()` 의 `allKVCache` 선언 직전 |
| `m2m100_translator.cpp:209` (allKVCache 이동 필요성) | `encoder_decoder_with_past.cpp::generate()` 의 emptySlots 검사 직전 |
| `m2m100_translator.cpp:240` (encoderHiddenStates 중복) | `encoder_decoder_with_past.cpp::generate()` 의 `runDecoderWithPast` 호출 인자 |
| `m2m100_translator.cpp:244` (encoderSeqLen 중복) | 위와 동일 위치 |
| `m2m100_translator.cpp:332` (vocabSize/offset 검증) | `token_selector.cpp::GreedyTokenSelector::select()` 의 `actualVocabSize` 계산 직전 |
| `onnxruntime_inference.h:15,84,97`, `tokenizer.h:12,20,35,37`, `m2m100_jni.cpp:16` | 원파일 그대로 (해당 파일 미수정) |

---

## 7. 단계별 마이그레이션

각 단계 후 컴파일 가능 상태가 유지되도록 한다.

1. **`token_selector.h` + `token_selector.cpp` 신규 작성** — `TokenSelector` interface + `GreedyTokenSelector` 구현. greedy 본체는 현 `m2m100_translator.cpp:315-350` 그대로 이식, eosTokenId_ 멤버 의존을 fallbackTokenId 인자로 교체. 이 시점에 어디서도 include 하지 않으므로 빌드 영향 0.
2. **`translator.h` + `translator.cpp` 신규 작성** — `Translator` thin abstract base. pure-virtual `translate(text, src, tgt, maxLength)`, virtual `release()` (default: `isLoaded_ = false`), `isLoaded()`, protected `setLoaded()` 만. 토크나이저/언어 토큰/special token 일체 base 에 두지 않음. 빌드 영향 0.
3. **`encoder_decoder_with_past.h` + `.cpp` 신규 작성** — 현 `m2m100_translator.cpp:135-299` 의 디코딩 루프를 *복사*해서 이식 (원본 아직 삭제 X). `parseKvOutputName` static helper 도 이식. greedy 호출은 `tokenSelector_->select(...)` 로 교체. 모델 상수는 멤버 변수로. 기존 TODO 주석 §6 매핑대로 보존.
4. **`CMakeLists.txt` 에 신규 세 .cpp 등록** — §4.3 의 변경.
5. **`m2m100_translator.h` 상속 전환** — `: public Translator` 추가, `EncoderDecoderWithPast decoder_` 멤버 추가. `tokenizer_`, `langToTokenId_`, `eosTokenId_` 는 M2M100 자체 멤버로 유지. 제거되는 항목: 멤버 `inference_`(→ `decoder_` 안으로), `padTokenId_`(사용처 없음), `isLoaded_`(base 가 보유) / 메서드 `selectNextToken`(private, → `GreedyTokenSelector`). `translate` 는 base 의 pure-virtual 을 `override` 로 구현, `translateSingle`/`getLanguageTokenId`/`isLanguageSupported` 는 base 가 아니므로 override 가 아닌 일반 멤버. 생성자 위임 추가.
6. **`m2m100_translator.cpp` 슬림화** — §4.2 의 형태로 재작성. 제거: 디코딩 루프 본체 (135-299), `parseKvOutputName` static (16-32), 기존 `selectNextToken` (315-350), 기존 release 의 inference_ 호출 부분 (356-360). `load()` 는 §4.2 의 형태로 (`registerLanguageToken` 호출 → `langToTokenId_[langCode] = ...` 직접 접근). `translate()` override 는 isLoaded 체크 + lang→id 변환 + `translateSingle` 위임. `translateSingle()` 은 §4.2 의 4단계 형태로. `getLanguageTokenId`, `isLanguageSupported` 는 본체 그대로. `release()` 는 `decoder_.release()` + `langToTokenId_.clear()` + `Translator::release()`.
7. **컴파일 확인** — 사용자 직접 수행.

---

## 8. 검증 (빌드는 사용자 직접 수행)

회귀 검증 시 확인할 항목:

- **JNI 호환성**: `m2m100_jni.cpp` 의 `g_translator->load/translate/translateSingle/getLanguageTokenId/isLanguageSupported/release` 호출이 컴파일되어야 한다. `M2M100Native.kt` 측 native 메서드 호출이 깨지지 않아야 한다.
- **기능 회귀 (golden case)**: `:features:gallery` 의 `TranscribeService` 경로로 짧은 영상 → SRT 생성 → M2M100 번역 결과가 리팩토링 전과 동일한지 (예: en→ko 한 문장).
- **batch 경로**: `M2M100Native.translateBatch` (length-prefixed buffer, `m2m100_jni.cpp:90-150`) 가 동일 입력으로 동일 array 반환.
- **boolean API**: `isLanguageSupported("ko")`, `isLanguageSupported("zz")` 동등.
- **메모리 / 수명**: `release()` 후 `langToTokenId_` 비워지고, `EncoderDecoderWithPast::release` 가 `OnnxInference::release` 를 호출하는지. 소멸자 호출 순서 — `~M2M100Translator()` body → M2M100 멤버 역순 소멸 (`eosTokenId_` → `langToTokenId_` → `tokenizer_` → `decoder_`) → `~Translator()` body — 가 안전한지 (`release()` 가 `~M2M100Translator()` 에서 호출되므로 `decoder_.release()` 와 `tokenizer_` 소멸이 모두 정상 경로로 진행).
- **선택 사항 — hot path 오버헤드**: token selection 이 `tokenSelector_->select` 의 virtual 호출 1회로 바뀌어 step 당 vtable lookup 1회 증가. greedy 본체 대비 무시 가능하지만, 토큰당 latency 가 base PR 대비 회귀 없는지 가볍게 측정.

---

## 9. Out of Scope (별도 작업)

코드 내 TODO 로 식별되지만 본 PR 범위 밖. 모두 두 번째 모델 통합 시점이나 별도 PR 에서 다룬다.

- `OnnxInference::setNumDecoderLayers/Heads/HiddenSize` setter 의 위치 — `onnxruntime_inference.h:84,97` TODO. 본래 모델별 상수이므로 `EncoderDecoderWithPast` 가 생성자 인자로 받고 있는 게 더 자연스럽지만, 현재는 setter 호출을 `EncoderDecoderWithPast` 가 대신 함.
- `Tokenizer::load` → `loadSentencePieceVocab` 같은 모델 중립 이름 리네이밍 — `tokenizer.h:12,20`.
- `Tokenizer` 의 `unique_ptr<sentencepiece::SentencePieceProcessor>` 를 일반 객체로 — `tokenizer.h:35`. vocab/reverseVocab 메모리 사용량 — `tokenizer.h:37`.
- JNI 다중 인스턴스화 — `g_translator` 단일 전역 → handle 기반, `m2m100_jni.cpp:16`.
- KV cache normalization 오버헤드 제거 — `m2m100_translator.cpp:187-188` TODO. allKVCache 로의 이동 없이 직접 사용.
- 디코딩 전략 확장 — `m2m100_translator.cpp:178-180` TODO. `BeamSearchTokenSelector`, `TopKTokenSelector`, `TopPTokenSelector` 같은 새 `TokenSelector` 구현체로 추가하면 `EncoderDecoderWithPast` 의 생성자 인자만 바꿔서 즉시 적용 가능.
- 모델별 stop token 다양화 — 일부 모델의 `</s>` 외 추가 종료 토큰이 등장하면 `generate()` 인자나 hook 으로 확장.
