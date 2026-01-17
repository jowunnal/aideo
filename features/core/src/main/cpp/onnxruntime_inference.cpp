#include "onnxruntime_inference.h"
#include <thread>
#include <algorithm>

OnnxInference::OnnxInference()
        : env_(ORT_LOGGING_LEVEL_WARNING, "OnnxInference") {
    AIDEO_LOGI(LOG_TAG_ONNX, "OnnxInference created");

    // 모바일 최적화: 전체 코어의 절반, 최소 2개, 최대 4개
    unsigned int totalCores = std::thread::hardware_concurrency();
    unsigned int numThreads = std::min(4u, std::max(2u, totalCores / 2));
    if (totalCores == 0) numThreads = 2;  // 감지 실패 시 기본값

    AIDEO_LOGI(LOG_TAG_ONNX, "Total cores: %u, Using threads: %u", totalCores, numThreads);

    // 세션 옵션 설정
    sessionOptions_.SetIntraOpNumThreads(numThreads);
    sessionOptions_.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
}

OnnxInference::~OnnxInference() {
    release();
}

bool OnnxInference::loadModel(const std::string& modelPath) {
    try {
        AIDEO_LOGI(LOG_TAG_ONNX, "Loading model from: %s", modelPath.c_str());
        session_ = std::make_unique<Ort::Session>(
                env_, modelPath.c_str(), sessionOptions_
        );
        AIDEO_LOGI(LOG_TAG_ONNX, "Model loaded successfully");
        return true;
    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Failed to load model: %s", e.what());
        return false;
    }
}

std::vector<float> OnnxInference::runInt64(
        const std::vector<const char*>& inputNames,
        const std::vector<std::vector<int64_t>>& inputValues,
        const std::vector<std::vector<int64_t>>& inputShapes,
        const std::vector<const char*>& outputNames) {

    std::vector<float> results;
    if (!session_) return results;

    try {
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<Ort::Value> inputTensors;

        for (size_t i = 0; i < inputValues.size(); ++i) {
            inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                    memoryInfo,
                    const_cast<int64_t*>(inputValues[i].data()), inputValues[i].size(),
                    inputShapes[i].data(), inputShapes[i].size()
            ));
        }

        auto outputTensors = session_->Run(
                Ort::RunOptions{nullptr},
                inputNames.data(), inputTensors.data(), inputTensors.size(),
                outputNames.data(), outputNames.size()
        );

        if (!outputTensors.empty() && outputTensors[0].IsTensor()) {
            const float* floatData = outputTensors[0].GetTensorData<float>();
            auto tensorInfo = outputTensors[0].GetTensorTypeAndShapeInfo();
            results.assign(floatData, floatData + tensorInfo.GetElementCount());
        }
    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Inference failed: %s", e.what());
    }
    return results;
}

std::vector<float> OnnxInference::runFloat(
        const std::vector<const char*>& inputNames,
        const std::vector<std::vector<float>>& inputValues,
        const std::vector<std::vector<int64_t>>& inputShapes,
        const std::vector<const char*>& outputNames) {

    std::vector<float> results;
    if (!session_) return results;
    try {
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<Ort::Value> inputTensors;

        for (size_t i = 0; i < inputValues.size(); ++i) {
            inputTensors.push_back(Ort::Value::CreateTensor<float>(
                    memoryInfo,
                    const_cast<float*>(inputValues[i].data()), inputValues[i].size(),
                    inputShapes[i].data(), inputShapes[i].size()
            ));
        }

        auto outputTensors = session_->Run(
                Ort::RunOptions{nullptr},
                inputNames.data(), inputTensors.data(), inputTensors.size(),
                outputNames.data(), outputNames.size()
        );

        if (!outputTensors.empty() && outputTensors[0].IsTensor()) {
            const float* floatData = outputTensors[0].GetTensorData<float>();
            auto tensorInfo = outputTensors[0].GetTensorTypeAndShapeInfo();
            results.assign(floatData, floatData + tensorInfo.GetElementCount());
        }
    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Inference failed: %s", e.what());
    }
    return results;
}

void OnnxInference::release() {
    session_.reset();
    AIDEO_LOGI(LOG_TAG_ONNX, "OnnxInference released");
}