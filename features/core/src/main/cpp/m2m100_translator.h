#ifndef AIDEO_M2M100_TRANSLATOR_H
#define AIDEO_M2M100_TRANSLATOR_H

#include <string>
#include "encoder_decoder_with_past.h"
#include "language_token_map.h"
#include "logging.h"
#include "tokenizer.h"
#include "translator.h"

#define LOG_TAG_M2M100 "M2M100"

// facebook/M2M-100 ONNX 추론 (encoder-decoder-decoderWithPast + SentencePiece + 언어 토큰)
class M2M100Translator final : public Translator {
public:
    M2M100Translator();

    // 소멸자는 반드시 override 된 Translator#release() 를 호출해야 함.
    ~M2M100Translator() override;

    // 모델 및 토크나이저 로드 (JNI 시그니처 그대로 유지)
    bool load(
            const char* encoderPath,
            const char* decoderPath,
            const char* decoderWithPastPath,
            const char* spModelPath,
            const char* vocabPath,
            const char* tokenizerConfigPath
    );

    // Translator override — lang 코드 → 토큰 ID 변환 후 M2M100 입력을 구성해 번역.
    std::string translate(
            const std::string& text,
            const std::string& srcLang,
            const std::string& tgtLang,
            int maxLength = 256
    ) override;

    // decoder_.release() + languageTokens_.clear() + Translator::release()
    void release() override;

private:
    bool loadLanguageTokens(const char* tokenizerConfigPath);

    EncoderDecoderWithPast decoder_;

    // SentencePiece + vocab.json — M2M100 입력/출력 토큰화
    Tokenizer tokenizer_;

    // "ko" → 128022 등
    LanguageTokenMap languageTokens_;

    // M2M100 special token — encoder/decoder input 구성에 사용
    // base 가 아닌 모델별로 보유 (모델마다 필요한 토큰이 다름).
    int64_t eosTokenId_ = 2;
    std::string loadedTokenizerConfigPath_;

    // 모델 설정 (M2M100의 설정값, facebook/m2m100/config.json 에 명시된 학습할 때 결정된 값)
    // layer 개수(decoder 의 반복 횟수)
    static constexpr int NUM_DECODER_LAYERS = 12;
    // attention(무엇을 얼마나 참고해야 하는가)에 대한 관점의 수 ==> 16개의 관점으로 self-attention(decoder 이전 출력), cross-attention(encoder 의 결과 == 원문) 을 참고
    static constexpr int NUM_HEADS = 16;
    // 각 토큰 벡터의 길이
    static constexpr int HIDDEN_SIZE = 1024;
    static constexpr int64_t VOCAB_SIZE = 128112;
};

#endif
