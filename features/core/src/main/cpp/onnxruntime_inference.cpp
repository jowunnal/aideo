#include "onnxruntime_inference.h"
#include <thread>
#include <algorithm>
#include <map>

OnnxInference::OnnxInference()
        : env_(ORT_LOGGING_LEVEL_WARNING, "OnnxInference") {
    // 모바일 최적화: 메모리 Arena 비활성화로 버벅임 해결됨
    // 스레드 수는 성능을 위해 적정 수준 유지 (코어의 절반, 2~4개)
    unsigned int totalCores = std::thread::hardware_concurrency();
    unsigned int numThreads = std::min(4u, std::max(2u, totalCores / 2));
    if (totalCores == 0) numThreads = 2;  // 감지 실패 시 기본값

    // 세션 옵션 설정
    sessionOptions_.SetIntraOpNumThreads(static_cast<int>(numThreads));
    sessionOptions_.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

    // 메모리 최적화: Arena allocator 비활성화
    // Arena는 메모리를 미리 할당하고 해제를 지연시켜 메모리 사용량 증가
    // 비활성화하면 즉시 해제되어 메모리 사용량 감소
    sessionOptions_.DisableCpuMemArena();

    // 메모리 패턴 최적화 활성화
    // 실행 중 메모리 할당 패턴을 분석하여 재사용
    sessionOptions_.EnableMemPattern();
}

OnnxInference::~OnnxInference() {
    release();
}

bool OnnxInference::loadEncoderDecoderModel(
        const std::string& encoderPath,
        const std::string& decoderPath,
        const std::string& decoderWithPastPath) {
    try {
        encoderSession_ = std::make_unique<Ort::Session>(
                env_, encoderPath.c_str(), sessionOptions_
        );
        decoderSession_ = std::make_unique<Ort::Session>(
                env_, decoderPath.c_str(), sessionOptions_
        );
        decoderWithPastSession_ = std::make_unique<Ort::Session>(
                env_, decoderWithPastPath.c_str(), sessionOptions_
        );
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
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeDefault);
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

        auto outputTensors = encoderSession_->Run(
                Ort::RunOptions{nullptr},
                inputNames.data(), inputTensors.data(), inputTensors.size(),
                outputNames.data(), outputNames.size()
        );

        if (!outputTensors.empty() && outputTensors[0].IsTensor()) {
            const float* data = outputTensors[0].GetTensorData<float>();
            auto tensorInfo = outputTensors[0].GetTensorTypeAndShapeInfo();
            encoderHiddenStates.assign(data, data + tensorInfo.GetElementCount());
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
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeDefault);

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
        for (auto & inputName : inputNames) {
            std::string name(inputName);
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
            const auto* logitsData = outputTensors[0].GetTensorData<float>();
            auto tensorInfo = outputTensors[0].GetTensorTypeAndShapeInfo();
            output.logits.assign(logitsData, logitsData + tensorInfo.GetElementCount());
        }

        for (size_t i = 1; i < outputTensors.size(); ++i) {
            if (outputTensors[i].IsTensor()) {
                const auto* data = outputTensors[i].GetTensorData<float>();
                auto tensorInfo = outputTensors[i].GetTensorTypeAndShapeInfo();
                output.presentKeyValues.emplace_back(data, data + tensorInfo.GetElementCount());
                output.presentKeyValueShapes.push_back(tensorInfo.GetShape());
                output.kvOutputNames.push_back(outputNames[i]);
            }
        }
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
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeDefault);

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

        // logits 추출
        if (!outputTensors.empty() && outputTensors[0].IsTensor()) {
            const float* logitsData = outputTensors[0].GetTensorData<float>();
            auto tensorInfo = outputTensors[0].GetTensorTypeAndShapeInfo();
            output.logits.assign(logitsData, logitsData + tensorInfo.GetElementCount());
        }

        // present key values 추출
        output.presentKeyValues.resize(outputTensors.size() - 1);
        output.presentKeyValueShapes.resize(outputTensors.size() - 1);
        output.kvOutputNames.clear();

        for (size_t i = 1; i < outputTensors.size(); ++i) {
            if (outputTensors[i].IsTensor()) {
                const float* data = outputTensors[i].GetTensorData<float>();
                auto tensorInfo = outputTensors[i].GetTensorTypeAndShapeInfo();

                output.kvOutputNames.push_back(outputNames[i]);
                output.presentKeyValues[i-1].assign(data, data + tensorInfo.GetElementCount());
                output.presentKeyValueShapes[i-1] = tensorInfo.GetShape();
            }
        }

    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Decoder with past inference failed: %s", e.what());
    }
    return output;
}

void OnnxInference::release() {
    encoderSession_.reset();
    decoderSession_.reset();
    decoderWithPastSession_.reset();
}