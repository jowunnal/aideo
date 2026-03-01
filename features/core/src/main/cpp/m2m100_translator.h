
// M2M100 Translator - High-level translation API using ONNX Runtime

#ifndef AIDEO_M2M100_TRANSLATOR_H //include guard
#define AIDEO_M2M100_TRANSLATOR_H

#include <string>
#include <vector>
#include <unordered_map>
#include "logging.h"
#include "onnxruntime_inference.h"
#include "tokenizer.h"

#define LOG_TAG_M2M100 "M2M100"

class M2M100Translator {
public:
    M2M100Translator();
    ~M2M100Translator();

    // 모델 및 토크나이저 로드
    bool load(
            const std::string& encoderPath,
            const std::string& decoderPath,
            const std::string& decoderWithPastPath,
            const std::string& spModelPath,
            const std::string& vocabPath,
            const std::string& tokenizerConfigPath
    );

    // 번역 실행
    std::string translate(
            const std::string& text,
            const std::string& srcLang,
            const std::string& tgtLang,
            int maxLength = 256
    );

    // 리소스 해제
    void release();

    // 지원 언어 확인
    bool isLanguageSupported(const std::string& lang) const;

    // 단일 텍스트 번역 (검증 완료된 언어 토큰 ID 사용)
    std::string translateSingle(
            const std::string& text,
            int64_t srcLangId,
            int64_t tgtLangId,
            int maxLength
    );

    // 언어 코드를 토큰 ID로 변환 (예: "ko" -> 128052)
    int64_t getLanguageTokenId(const std::string& lang) const;

private:

    // 다음 토큰 선택 (greedy decoding)
    int64_t selectNextToken(const std::vector<float>& logits, int64_t vocabSize);

    // ONNX 추론 엔진
    OnnxInference inference_;

    // 토크나이저
    Tokenizer tokenizer_;

    // 언어 코드 -> 토큰 ID 매핑
    std::unordered_map<std::string, int64_t> langToTokenId_;

    // 특수 토큰 IDs
    int64_t eosTokenId_ = 2;      // </s>
    int64_t padTokenId_ = 1;      // <pad>

    // 모델 설정 (M2M100 기본값)
    static constexpr int NUM_DECODER_LAYERS = 12;
    static constexpr int NUM_HEADS = 16;
    static constexpr int HIDDEN_SIZE = 1024;
    static constexpr int64_t VOCAB_SIZE = 128112;

    bool isLoaded_ = false;
};

#endif