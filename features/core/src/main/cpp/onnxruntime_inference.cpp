#include "onnxruntime_inference.h"
#include <thread>
#include <algorithm>
#include <map>
#include <cmath>

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

bool OnnxInference::loadEncoderDecoderModel(
        const std::string& encoderPath,
        const std::string& decoderPath,
        const std::string& decoderWithPastPath) {
    try {
        AIDEO_LOGI(LOG_TAG_ONNX, "Loading encoder from: %s", encoderPath.c_str());
        encoderSession_ = std::make_unique<Ort::Session>(
                env_, encoderPath.c_str(), sessionOptions_
        );
        AIDEO_LOGI(LOG_TAG_ONNX, "Encoder loaded successfully");

        AIDEO_LOGI(LOG_TAG_ONNX, "Loading decoder from: %s", decoderPath.c_str());
        decoderSession_ = std::make_unique<Ort::Session>(
                env_, decoderPath.c_str(), sessionOptions_
        );
        AIDEO_LOGI(LOG_TAG_ONNX, "Decoder loaded successfully");

        AIDEO_LOGI(LOG_TAG_ONNX, "Loading decoder_with_past from: %s", decoderWithPastPath.c_str());
        decoderWithPastSession_ = std::make_unique<Ort::Session>(
                env_, decoderWithPastPath.c_str(), sessionOptions_
        );
        AIDEO_LOGI(LOG_TAG_ONNX, "Decoder with past loaded successfully");

        return true;
    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Failed to load encoder-decoder model: %s", e.what());
        return false;
    }
}

std::vector<float> OnnxInference::runEncoder(
        const std::vector<int64_t>& inputIds,
        const std::vector<int64_t>& attentionMask,
        int64_t batchSize,
        int64_t seqLength) {

    std::vector<float> encoderHiddenStates;
    if (!encoderSession_) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Encoder session not loaded");
        return encoderHiddenStates;
    }

    Ort::AllocatorWithDefaultOptions allocator;

    try {
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
        std::vector<int64_t> inputShape = {batchSize, seqLength};

        // Get actual input/output names from the model
        std::vector<Ort::AllocatedStringPtr> inputNamePtrs;
        std::vector<const char*> inputNames;
        for (size_t i = 0; i < encoderSession_->GetInputCount(); ++i) {
            inputNamePtrs.push_back(encoderSession_->GetInputNameAllocated(i, allocator));
            inputNames.push_back(inputNamePtrs.back().get());
        }

        std::vector<Ort::AllocatedStringPtr> outputNamePtrs;
        std::vector<const char*> outputNames;
        for (size_t i = 0; i < encoderSession_->GetOutputCount(); ++i) {
            outputNamePtrs.push_back(encoderSession_->GetOutputNameAllocated(i, allocator));
            outputNames.push_back(outputNamePtrs.back().get());
        }

        // Create tensors in the order the model expects
        std::vector<Ort::Value> inputTensors;
        for (size_t i = 0; i < inputNames.size(); ++i) {
            std::string name(inputNames[i]);
            if (name == "input_ids") {
                inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                        memoryInfo,
                        const_cast<int64_t*>(inputIds.data()), inputIds.size(),
                        inputShape.data(), inputShape.size()
                ));
            } else if (name == "attention_mask") {
                inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                        memoryInfo,
                        const_cast<int64_t*>(attentionMask.data()), attentionMask.size(),
                        inputShape.data(), inputShape.size()
                ));
            } else {
                AIDEO_LOGE(LOG_TAG_ONNX, "Unknown encoder input: %s", name.c_str());
                return encoderHiddenStates;
            }
        }

        // 디버그: 입력 확인
        AIDEO_LOGI(LOG_TAG_ONNX, "Encoder input - batch: %lld, seq: %lld", batchSize, seqLength);
        std::string inputIdsStr;
        for (size_t i = 0; i < std::min(inputIds.size(), (size_t)10); i++) {
            inputIdsStr += std::to_string(inputIds[i]) + " ";
        }
        AIDEO_LOGI(LOG_TAG_ONNX, "Encoder input_ids (first 10): %s", inputIdsStr.c_str());

        auto outputTensors = encoderSession_->Run(
                Ort::RunOptions{nullptr},
                inputNames.data(), inputTensors.data(), inputTensors.size(),
                outputNames.data(), outputNames.size()
        );

        if (!outputTensors.empty() && outputTensors[0].IsTensor()) {
            const float* data = outputTensors[0].GetTensorData<float>();
            auto tensorInfo = outputTensors[0].GetTensorTypeAndShapeInfo();
            auto shape = tensorInfo.GetShape();

            // 디버그: 출력 shape 확인
            std::string shapeStr;
            for (auto dim : shape) {
                shapeStr += std::to_string(dim) + " ";
            }
            AIDEO_LOGI(LOG_TAG_ONNX, "Encoder output shape: [%s]", shapeStr.c_str());

            // 디버그: 출력 값 샘플 확인 (처음 5개, NaN/Inf 체크)
            size_t totalElements = tensorInfo.GetElementCount();
            float minVal = data[0], maxVal = data[0];
            bool hasNaN = false, hasInf = false;
            for (size_t i = 0; i < totalElements; i++) {
                if (std::isnan(data[i])) hasNaN = true;
                if (std::isinf(data[i])) hasInf = true;
                if (data[i] < minVal) minVal = data[i];
                if (data[i] > maxVal) maxVal = data[i];
            }
            AIDEO_LOGI(LOG_TAG_ONNX, "Encoder output stats - min: %f, max: %f, hasNaN: %d, hasInf: %d",
                       minVal, maxVal, hasNaN, hasInf);

            encoderHiddenStates.assign(data, data + totalElements);
        }
    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Encoder inference failed: %s", e.what());
    }
    return encoderHiddenStates;
}

OnnxInference::DecoderOutput OnnxInference::runDecoder(
        const std::vector<int64_t>& inputIds,
        const std::vector<int64_t>& attentionMask,
        const std::vector<float>& encoderHiddenStates,
        int64_t batchSize,
        int64_t decoderSeqLength,
        int64_t encoderSeqLength) {

    DecoderOutput output;
    if (!decoderSession_) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Decoder session not loaded");
        return output;
    }

    Ort::AllocatorWithDefaultOptions allocator;

    try {
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

        // Get actual input names from the model and create tensors in correct order
        std::vector<Ort::AllocatedStringPtr> inputNamePtrs;
        std::vector<const char*> inputNames;
        for (size_t i = 0; i < decoderSession_->GetInputCount(); ++i) {
            inputNamePtrs.push_back(decoderSession_->GetInputNameAllocated(i, allocator));
            inputNames.push_back(inputNamePtrs.back().get());
        }

        std::vector<int64_t> inputIdsShape = {batchSize, decoderSeqLength};
        std::vector<int64_t> attentionMaskShape = {batchSize, encoderSeqLength};
        std::vector<int64_t> encoderHiddenShape = {batchSize, encoderSeqLength, static_cast<int64_t>(hiddenSize_)};

        std::vector<Ort::Value> inputTensors;
        for (size_t i = 0; i < inputNames.size(); ++i) {
            std::string name(inputNames[i]);
            if (name == "input_ids") {
                inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                        memoryInfo,
                        const_cast<int64_t*>(inputIds.data()), inputIds.size(),
                        inputIdsShape.data(), inputIdsShape.size()
                ));
            } else if (name == "encoder_attention_mask") {
                inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                        memoryInfo,
                        const_cast<int64_t*>(attentionMask.data()), attentionMask.size(),
                        attentionMaskShape.data(), attentionMaskShape.size()
                ));
            } else if (name == "encoder_hidden_states") {
                inputTensors.push_back(Ort::Value::CreateTensor<float>(
                        memoryInfo,
                        const_cast<float*>(encoderHiddenStates.data()), encoderHiddenStates.size(),
                        encoderHiddenShape.data(), encoderHiddenShape.size()
                ));
            } else {
                AIDEO_LOGE(LOG_TAG_ONNX, "Unknown decoder input: %s", name.c_str());
                return output;
            }
        }

        // Get actual output names from the model
        std::vector<Ort::AllocatedStringPtr> outputNamePtrs;
        std::vector<const char*> outputNames;
        for (size_t i = 0; i < decoderSession_->GetOutputCount(); ++i) {
            outputNamePtrs.push_back(decoderSession_->GetOutputNameAllocated(i, allocator));
            outputNames.push_back(outputNamePtrs.back().get());
        }

        auto outputTensors = decoderSession_->Run(
                Ort::RunOptions{nullptr},
                inputNames.data(), inputTensors.data(), inputTensors.size(),
                outputNames.data(), outputNames.size()
        );

        // logits 추출
        if (!outputTensors.empty() && outputTensors[0].IsTensor()) {
            const float* logitsData = outputTensors[0].GetTensorData<float>();
            auto tensorInfo = outputTensors[0].GetTensorTypeAndShapeInfo();
            auto shape = tensorInfo.GetShape();

            // 디버그: logits shape
            std::string shapeStr;
            for (auto dim : shape) {
                shapeStr += std::to_string(dim) + " ";
            }
            AIDEO_LOGI(LOG_TAG_ONNX, "Decoder logits shape: [%s]", shapeStr.c_str());

            // 디버그: logits 값 범위
            size_t totalElements = tensorInfo.GetElementCount();
            float minVal = logitsData[0], maxVal = logitsData[0];
            for (size_t i = 0; i < totalElements; i++) {
                if (logitsData[i] < minVal) minVal = logitsData[i];
                if (logitsData[i] > maxVal) maxVal = logitsData[i];
            }
            AIDEO_LOGI(LOG_TAG_ONNX, "Decoder logits stats - min: %f, max: %f", minVal, maxVal);

            output.logits.assign(logitsData, logitsData + totalElements);
        }

        // present key values 추출
        // 디버그: 출력 이름 로깅
        AIDEO_LOGI(LOG_TAG_ONNX, "Decoder output count: %zu", outputTensors.size());
        for (size_t i = 0; i < std::min(outputNames.size(), (size_t)5); ++i) {
            AIDEO_LOGI(LOG_TAG_ONNX, "  Decoder Output[%zu]: %s", i, outputNames[i]);
        }

        for (size_t i = 1; i < outputTensors.size(); ++i) {
            if (outputTensors[i].IsTensor()) {
                const float* data = outputTensors[i].GetTensorData<float>();
                auto tensorInfo = outputTensors[i].GetTensorTypeAndShapeInfo();
                output.presentKeyValues.emplace_back(data, data + tensorInfo.GetElementCount());
                output.presentKeyValueShapes.push_back(tensorInfo.GetShape());
                output.kvOutputNames.push_back(outputNames[i]);
            }
        }
        AIDEO_LOGI(LOG_TAG_ONNX, "Decoder KV cache tensors: %zu", output.presentKeyValues.size());

    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Decoder inference failed: %s", e.what());
    }
    return output;
}

OnnxInference::DecoderOutput OnnxInference::runDecoderWithPast(
        const std::vector<int64_t>& inputIds,
        const std::vector<int64_t>& attentionMask,
        const std::vector<float>& encoderHiddenStates,
        const std::vector<std::vector<float>>& pastKeyValues,
        const std::vector<std::vector<int64_t>>& pastKeyValueShapes,
        int64_t batchSize,
        int64_t encoderSeqLength) {

    DecoderOutput output;
    if (!decoderWithPastSession_) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Decoder with past session not loaded");
        return output;
    }

    Ort::AllocatorWithDefaultOptions allocator;

    try {
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);

        // Get actual input names from the model
        std::vector<Ort::AllocatedStringPtr> modelInputNamePtrs;
        std::vector<std::string> modelInputNames;
        for (size_t i = 0; i < decoderWithPastSession_->GetInputCount(); ++i) {
            modelInputNamePtrs.push_back(decoderWithPastSession_->GetInputNameAllocated(i, allocator));
            modelInputNames.push_back(modelInputNamePtrs.back().get());
        }

        // Build a map of tensor name -> tensor for flexible ordering
        std::map<std::string, Ort::Value> tensorMap;

        // input_ids: [batch, 1] - 한 토큰만
        std::vector<int64_t> inputIdsShape = {batchSize, 1};
        tensorMap.emplace("input_ids", Ort::Value::CreateTensor<int64_t>(
                memoryInfo,
                const_cast<int64_t*>(inputIds.data()), inputIds.size(),
                inputIdsShape.data(), inputIdsShape.size()
        ));

        // encoder_attention_mask
        std::vector<int64_t> attentionMaskShape = {batchSize, encoderSeqLength};
        tensorMap.emplace("encoder_attention_mask", Ort::Value::CreateTensor<int64_t>(
                memoryInfo,
                const_cast<int64_t*>(attentionMask.data()), attentionMask.size(),
                attentionMaskShape.data(), attentionMaskShape.size()
        ));

        // encoder_hidden_states
        std::vector<int64_t> encoderHiddenShape = {batchSize, encoderSeqLength, static_cast<int64_t>(hiddenSize_)};
        tensorMap.emplace("encoder_hidden_states", Ort::Value::CreateTensor<float>(
                memoryInfo,
                const_cast<float*>(encoderHiddenStates.data()), encoderHiddenStates.size(),
                encoderHiddenShape.data(), encoderHiddenShape.size()
        ));

        // past key values 추가
        for (int i = 0; i < numDecoderLayers_; ++i) {
            int baseIdx = i * 4;

            // Check bounds
            if (static_cast<size_t>(baseIdx + 3) >= pastKeyValues.size()) {
                AIDEO_LOGE(LOG_TAG_ONNX, "KV cache index out of bounds");
                return output;
            }

            // decoder key
            std::string decoderKeyName = "past_key_values." + std::to_string(i) + ".decoder.key";
            tensorMap.emplace(decoderKeyName, Ort::Value::CreateTensor<float>(
                    memoryInfo,
                    const_cast<float*>(pastKeyValues[baseIdx].data()),
                    pastKeyValues[baseIdx].size(),
                    pastKeyValueShapes[baseIdx].data(),
                    pastKeyValueShapes[baseIdx].size()
            ));

            // decoder value
            std::string decoderValueName = "past_key_values." + std::to_string(i) + ".decoder.value";
            tensorMap.emplace(decoderValueName, Ort::Value::CreateTensor<float>(
                    memoryInfo,
                    const_cast<float*>(pastKeyValues[baseIdx + 1].data()),
                    pastKeyValues[baseIdx + 1].size(),
                    pastKeyValueShapes[baseIdx + 1].data(),
                    pastKeyValueShapes[baseIdx + 1].size()
            ));

            // encoder key
            std::string encoderKeyName = "past_key_values." + std::to_string(i) + ".encoder.key";
            tensorMap.emplace(encoderKeyName, Ort::Value::CreateTensor<float>(
                    memoryInfo,
                    const_cast<float*>(pastKeyValues[baseIdx + 2].data()),
                    pastKeyValues[baseIdx + 2].size(),
                    pastKeyValueShapes[baseIdx + 2].data(),
                    pastKeyValueShapes[baseIdx + 2].size()
            ));

            // encoder value
            std::string encoderValueName = "past_key_values." + std::to_string(i) + ".encoder.value";
            tensorMap.emplace(encoderValueName, Ort::Value::CreateTensor<float>(
                    memoryInfo,
                    const_cast<float*>(pastKeyValues[baseIdx + 3].data()),
                    pastKeyValues[baseIdx + 3].size(),
                    pastKeyValueShapes[baseIdx + 3].data(),
                    pastKeyValueShapes[baseIdx + 3].size()
            ));
        }

        // Create input tensors in the order the model expects
        std::vector<Ort::Value> inputTensors;
        std::vector<const char*> inputNames;
        for (const auto& name : modelInputNames) {
            auto it = tensorMap.find(name);
            if (it != tensorMap.end()) {
                inputTensors.push_back(std::move(it->second));
                inputNames.push_back(name.c_str());
            } else {
                AIDEO_LOGE(LOG_TAG_ONNX, "Missing input tensor for: %s", name.c_str());
                return output;
            }
        }

        // 출력 이름: 동적으로 모델에서 가져옴
        std::vector<Ort::AllocatedStringPtr> outputNamePtrs;
        std::vector<const char*> outputNames;
        for (size_t i = 0; i < decoderWithPastSession_->GetOutputCount(); ++i) {
            outputNamePtrs.push_back(decoderWithPastSession_->GetOutputNameAllocated(i, allocator));
            outputNames.push_back(outputNamePtrs.back().get());
        }

        auto outputTensors = decoderWithPastSession_->Run(
                Ort::RunOptions{nullptr},
                inputNames.data(), inputTensors.data(), inputTensors.size(),
                outputNames.data(), outputNames.size()
        );

        // 디버그: 출력 이름 로깅
        AIDEO_LOGI(LOG_TAG_ONNX, "DecoderWithPast output count: %zu", outputTensors.size());
        for (size_t i = 0; i < std::min(outputNames.size(), (size_t)5); ++i) {
            AIDEO_LOGI(LOG_TAG_ONNX, "  Output[%zu]: %s", i, outputNames[i]);
        }

        // logits 추출
        if (!outputTensors.empty() && outputTensors[0].IsTensor()) {
            const float* logitsData = outputTensors[0].GetTensorData<float>();
            auto tensorInfo = outputTensors[0].GetTensorTypeAndShapeInfo();
            auto shape = tensorInfo.GetShape();

            // 디버그: logits shape과 통계
            std::string shapeStr;
            for (auto dim : shape) {
                shapeStr += std::to_string(dim) + " ";
            }
            size_t totalElements = tensorInfo.GetElementCount();
            float minVal = logitsData[0], maxVal = logitsData[0];
            for (size_t i = 0; i < totalElements; i++) {
                if (logitsData[i] < minVal) minVal = logitsData[i];
                if (logitsData[i] > maxVal) maxVal = logitsData[i];
            }
            AIDEO_LOGI(LOG_TAG_ONNX, "DecoderWithPast logits shape: [%s], min: %f, max: %f",
                       shapeStr.c_str(), minVal, maxVal);

            output.logits.assign(logitsData, logitsData + totalElements);
        }

        // present key values 추출 - 출력 이름 기반으로 정렬
        // 출력 이름 형식: present.X.decoder.key, present.X.decoder.value, present.X.encoder.key, present.X.encoder.value
        // 또는: present.X.self_attn.key, present.X.self_attn.value, present.X.encoder_attn.key, ...
        output.presentKeyValues.resize(outputTensors.size() - 1);
        output.presentKeyValueShapes.resize(outputTensors.size() - 1);
        output.kvOutputNames.clear();

        for (size_t i = 1; i < outputTensors.size(); ++i) {
            if (outputTensors[i].IsTensor()) {
                const float* data = outputTensors[i].GetTensorData<float>();
                auto tensorInfo = outputTensors[i].GetTensorTypeAndShapeInfo();
                auto shape = tensorInfo.GetShape();

                output.kvOutputNames.push_back(outputNames[i]);
                output.presentKeyValues[i-1].assign(data, data + tensorInfo.GetElementCount());
                output.presentKeyValueShapes[i-1] = shape;
            }
        }

        AIDEO_LOGI(LOG_TAG_ONNX, "DecoderWithPast KV cache tensors: %zu", output.presentKeyValues.size());

    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Decoder with past inference failed: %s", e.what());
    }
    return output;
}

void OnnxInference::release() {
    session_.reset();
    encoderSession_.reset();
    decoderSession_.reset();
    decoderWithPastSession_.reset();
    AIDEO_LOGI(LOG_TAG_ONNX, "OnnxInference released");
}