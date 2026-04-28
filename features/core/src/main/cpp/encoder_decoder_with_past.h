#ifndef AIDEO_ENCODER_DECODER_WITH_PAST_H
#define AIDEO_ENCODER_DECODER_WITH_PAST_H

#include <cstdint>
#include <memory>
#include <string>
#include <utility>
#include <vector>
#include "logging.h"
#include "onnxruntime_inference.h"
#include "token_selector.h"

#define LOG_TAG_ENC_DEC_WITH_PAST "EncDecWithPast"

// 텍스트 기반의 seq2seq Transformer & 동적 KV Cache 아키텍처 전용 encoder-decoder-decoderWithPast autoRegressive loop decoder
class EncoderDecoderWithPast {
public:
    struct EncoderIoConfig {
        std::string inputIds = "input_ids";
        std::string attentionMask = "attention_mask";
        std::string lastHiddenState = "last_hidden_state";
    };

    struct DecoderIoConfig {
        std::string inputIds = "input_ids";
        std::string encoderAttentionMask = "encoder_attention_mask";
        std::string encoderHiddenStates = "encoder_hidden_states";
        std::string logits = "logits";
        std::string presentPrefix = "present.";
    };

    struct DecoderWithPastIoConfig {
        std::string inputIds = "input_ids";
        std::string encoderAttentionMask = "encoder_attention_mask";
        std::string encoderHiddenStates = "encoder_hidden_states";
        std::string pastKeyValuesPrefix = "past_key_values.";
        std::string decoderKey = "decoder.key";
        std::string decoderValue = "decoder.value";
        std::string encoderKey = "encoder.key";
        std::string encoderValue = "encoder.value";
        std::string logits = "logits";
        std::string presentPrefix = "present.";
    };

    EncoderDecoderWithPast(
            int numDecoderLayers,
            int numHeads,
            int hiddenSize,
            int64_t vocabSize
    );

    EncoderDecoderWithPast(
            int numDecoderLayers,
            int numHeads,
            int hiddenSize,
            int64_t vocabSize,
            EncoderIoConfig&& encoderIoConfig,
            DecoderIoConfig&& decoderIoConfig,
            DecoderWithPastIoConfig&& decoderWithPastIoConfig,
            std::unique_ptr<TokenSelector>&& tokenSelector
    );

    ~EncoderDecoderWithPast();

    bool load(
            const char* encoderPath,
            const char* decoderPath,
            const char* decoderWithPastPath
    );

    void release();

    /**
     * [encode - decode - decodeWithPast] 까지의 단계를 1 batchSize 로 트리거 \n
     *
     * decode, decodeWithPast 의 logits 계산 과정에 [tokenSelector_] 사용
     *
     * @param encoderInputIds : tokenized 원문 text
     * @param encoderAttentionMask : encoderInputIds 유효 토큰 mask
     * @param initialDecoderInputIds : shape = [eosTokenId, tgtLangTokenId]
     * @param eosTokenId : 모델에 구체화된 eosTokenId
     * @param maxLength : 모델에 구체화된 InputIds length
     * @return
     */
    std::vector<int64_t> generateSingle(
            const std::vector<int64_t>& encoderInputIds,
            const std::vector<int64_t>& encoderAttentionMask,
            const std::vector<int64_t>& initialDecoderInputIds,
            int64_t eosTokenId,
            int maxLength
    );

private:
    static constexpr const char* kEncoderSessionKey = "encoder";
    static constexpr const char* kDecoderSessionKey = "decoder";
    static constexpr const char* kDecoderWithPastSessionKey = "decoder_with_past";

    struct DecoderOutput {
        std::vector<float> logits;
        std::vector<std::vector<float>> presentKeyValues;
        std::vector<std::vector<int64_t>> presentKeyValueShapes;
        std::vector<std::string> kvOutputNames;
    };

    /**
     * KV Cache 에 사용될 output name 에서 layer 단위의 index, type 으로 parsing
     *
     * e.g) [present.X.{decoder|encoder}.{key|value}] => {레이어 인덱스, 타입 오프셋(0=decoder_key, 1=decoder_value, 2=encoder_key, 3=encoder_value)}
     *
     * @param name : KV Cache output name
     * @return : {레이어 인덱스, 타입 오프셋(0=decoder_key, 1=decoder_value, 2=encoder_key, 3=encoder_value)}, 실패 시 {-1, -1}
     */
    static std::pair<int, int> parseKvOutputName(const std::string& name);

    bool loadModelSession(
            const char* sessionKey,
            const char* modelPath,
            std::string& loadedPath,
            const char* modelName
    );

    std::vector<float> runEncoder(
            const std::vector<int64_t>& inputIds,
            const std::vector<int64_t>& attentionMask,
            int64_t batchSize,
            int64_t seqLength
    );

    DecoderOutput runDecoder(
            const std::vector<int64_t>& inputIds,
            const std::vector<int64_t>& encoderAttentionMask,
            const std::vector<float>& encoderHiddenStates,
            int64_t batchSize,
            int64_t decoderSeqLength,
            int64_t encoderSeqLength
    );

    DecoderOutput runDecoderWithPast(
            const std::vector<int64_t>& decoderInputIds,
            const std::vector<int64_t>& encoderAttentionMask,
            const std::vector<float>& encoderHiddenStates,
            const std::vector<std::vector<float>>& pastKeyValues,
            const std::vector<std::vector<int64_t>>& pastKeyValueShapes,
            int64_t batchSize,
            int64_t encoderSeqLength
    );

    std::unique_ptr<TokenSelector> tokenSelector_;
    OnnxInference inference_;
    EncoderIoConfig encoderIoConfig_;
    DecoderIoConfig decoderIoConfig_;
    DecoderWithPastIoConfig decoderWithPastIoConfig_;
    std::string loadedEncoderPath_;
    std::string loadedDecoderPath_;
    std::string loadedDecoderWithPastPath_;
    int numDecoderLayers_;
    int numHeads_;
    int hiddenSize_;
    int64_t vocabSize_;
};

#endif
