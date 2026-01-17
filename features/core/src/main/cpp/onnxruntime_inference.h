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

    // 모델 로드
    bool loadModel(const std::string& modelPath);

    // 일반화된 추론 함수 (T5용 int64 지원)
    // inputValues: 각 입력의 플랫 데이터
    // inputShapes: 각 입력의 shape
    std::vector<float> runInt64(
            const std::vector<const char*>& inputNames,
            const std::vector<std::vector<int64_t>>& inputValues,
            const std::vector<std::vector<int64_t>>& inputShapes,
            const std::vector<const char*>& outputNames
    );

    // 일반 추론용 (float 입력)
    std::vector<float> runFloat(
            const std::vector<const char*>& inputNames,
            const std::vector<std::vector<float>>& inputValues,
            const std::vector<std::vector<int64_t>>& inputShapes,
            const std::vector<const char*>& outputNames
    );
    void release();
private:
    Ort::Env env_;
    std::unique_ptr<Ort::Session> session_;
    Ort::SessionOptions sessionOptions_;
};
#endif
