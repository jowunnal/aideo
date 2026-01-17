#include "t5_inference.h"
#include "logging.h"
#include <algorithm>
#include <iostream>

#define LOG_TAG_T5 "T5_Inference"

T5Inference::T5Inference() {
    // 엔진 생성
    engine_ = std::make_unique<OnnxInference>();
    AIDEO_LOGI(LOG_TAG_T5, "T5 Controller Initialized");
}

T5Inference::~T5Inference() {
    release();
}

bool T5Inference::loadModel(const std::string& modelPath) {
    if (!engine_) return false;
    return engine_->loadModel(modelPath);
}

bool T5Inference::loadTokenizer(const std::string& tokenizerPath) {
    tokenizer_ = std::make_unique<Tokenizer>();
    return tokenizer_->load(tokenizerPath);
}

void T5Inference::release() {
    if (engine_) engine_->release();
    engine_.reset();
    tokenizer_.reset();
    AIDEO_LOGI(LOG_TAG_T5, "T5 Resources Released");
}

std::string T5Inference::generateText(const std::string& inputText, int maxLength) {
    if (!engine_ || !tokenizer_) {
        AIDEO_LOGE(LOG_TAG_T5, "Components not initialized");
        return "";
    }

    // 1. Encode
    std::vector<int64_t> inputIds = tokenizer_->encode(inputText);
    std::vector<int64_t> attentionMask(inputIds.size(), 1);

    // 2. Prepare Decoder Loop
    int64_t padTokenId = tokenizer_->getPadTokenId();
    int64_t eosTokenId = tokenizer_->getEosTokenId();
    std::vector<int64_t> decoderInputIds = {padTokenId};

    // 입력 이름 정의 (모델에 따라 다를 수 있음)
    std::vector<const char*> inputNames = {"input_ids", "attention_mask", "decoder_input_ids"};
    std::vector<const char*> outputNames = {"logits"};

    // Vocab size를 Tokenizer에서 가져오기
    size_t vocabSize = tokenizer_->getVocabSize();

    // 3. Autoregressive Generation
    for (int step = 0; step < maxLength; step++) {
        // Prepare Shapes: [1, seq_len]
        std::vector<int64_t> inputShape = {1, static_cast<int64_t>(inputIds.size())};
        std::vector<int64_t> decoderShape = {1, static_cast<int64_t>(decoderInputIds.size())};

        // Execute Engine (다중 입력 전달)
        // inputValues 순서: input_ids, attention_mask, decoder_input_ids
        std::vector<float> logits = engine_->runInt64(
                inputNames,
                {inputIds, attentionMask, decoderInputIds},
                {inputShape, inputShape, decoderShape},
                outputNames
        );

        if (logits.empty()) {
            AIDEO_LOGE(LOG_TAG_T5, "Inference failed at step %d", step);
            break;
        }

        // Greedy Decode
        int64_t nextToken = argmax(logits, vocabSize);

        // EOS 토큰이면 종료
        if (nextToken == eosTokenId) break;

        // 다음 스텝을 위해 추가
        decoderInputIds.push_back(nextToken);
    }

    // 4. Decode
    // decoderInputIds의 첫 번째(START) 토큰은 제외하고 디코딩
    std::vector<int64_t> resultIds(decoderInputIds.begin() + 1, decoderInputIds.end());
    return tokenizer_->decode(resultIds);
}

int64_t T5Inference::argmax(const std::vector<float>& logits, size_t vocabSize) {
    if (logits.empty()) return 0;

    // 마지막 토큰의 logits 위치 계산
    // output logits shape: [1, decoder_len, vocab_size]
    // vector는 flat하므로 마지막 vocabSize 만큼만 보면 됨
    size_t offset = logits.size() - vocabSize;
    if (offset >= logits.size()) offset = 0; // 안전장치

    float maxVal = logits[offset];
    int64_t maxIdx = 0;

    for (size_t i = 1; i < vocabSize; ++i) {
        if (logits[offset + i] > maxVal) {
            maxVal = logits[offset + i];
            maxIdx = i;
        }
    }
    return maxIdx;
}
