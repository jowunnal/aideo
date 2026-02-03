//
// Created by PC on 2026-01-16.
//

#ifndef AIDEO_ONNXRUNTIME_INFERENCE_H
#define AIDEO_ONNXRUNTIME_INFERENCE_H

#include <string>
#include <vector>
#include "logging.h"
#include "onnxruntime_cxx_api.h"

#define LOG_TAG_ONNX "ONNX_Native"

class OnnxInference {
public:
    OnnxInference();
    ~OnnxInference();

    // Encoder-Decoder 모델 로드 (M2M100 등)
    bool loadEncoderDecoderModel(
            const std::string& encoderPath,
            const std::string& decoderPath,
            const std::string& decoderWithPastPath
    );

    // Encoder 추론 - encoder_hidden_states 반환
    std::vector<float> runEncoder(
            const std::vector<int64_t>& inputIds,
            const std::vector<int64_t>& attentionMask,
            int64_t batchSize,
            int64_t seqLength
    );

    // Decoder 추론 (첫 토큰) - logits와 present KV cache 반환
    struct DecoderOutput {
        std::vector<float> logits;
        std::vector<std::vector<float>> presentKeyValues;  // [layer][data]
        std::vector<std::vector<int64_t>> presentKeyValueShapes;  // [layer][shape]
        std::vector<std::string> kvOutputNames;  // KV 출력 이름 (디버그/매핑용)
    };

    DecoderOutput runDecoder(
            const std::vector<int64_t>& inputIds,
            const std::vector<int64_t>& attentionMask,
            const std::vector<float>& encoderHiddenStates,
            int64_t batchSize,
            int64_t decoderSeqLength,
            int64_t encoderSeqLength
    );

    // Decoder with past 추론 (후속 토큰)
    DecoderOutput runDecoderWithPast(
            const std::vector<int64_t>& inputIds,
            const std::vector<int64_t>& attentionMask,
            const std::vector<float>& encoderHiddenStates,
            const std::vector<std::vector<float>>& pastKeyValues,
            const std::vector<std::vector<int64_t>>& pastKeyValueShapes,
            int64_t batchSize,
            int64_t encoderSeqLength
    );

    void release();

    // 모델 설정
    void setNumDecoderLayers(int numLayers) { numDecoderLayers_ = numLayers; }
    void setNumHeads(int numHeads) { numHeads_ = numHeads; }
    void setHiddenSize(int hiddenSize) { hiddenSize_ = hiddenSize; }

private:
    Ort::Env env_;
    Ort::SessionOptions sessionOptions_;

    // Encoder-Decoder 모델용
    std::unique_ptr<Ort::Session> encoderSession_;
    std::unique_ptr<Ort::Session> decoderSession_;
    std::unique_ptr<Ort::Session> decoderWithPastSession_;

    // 모델 설정
    int numDecoderLayers_ = 12;  // M2M100 기본값
    int numHeads_ = 16;
    int hiddenSize_ = 1024;
};
#endif
