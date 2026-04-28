#include "encoder_decoder_with_past.h"
#include "path_utils.h"
#include <exception>
#include <regex>
#include <utility>

EncoderDecoderWithPast::EncoderDecoderWithPast(
        int numDecoderLayers,
        int numHeads,
        int hiddenSize,
        int64_t vocabSize
) : EncoderDecoderWithPast(
        numDecoderLayers,
        numHeads,
        hiddenSize,
        vocabSize,
        EncoderIoConfig{},
        DecoderIoConfig{},
        DecoderWithPastIoConfig{},
        std::make_unique<GreedyTokenSelector>()) {}

EncoderDecoderWithPast::EncoderDecoderWithPast(
        int numDecoderLayers,
        int numHeads,
        int hiddenSize,
        int64_t vocabSize,
        EncoderIoConfig&& encoderIoConfig,
        DecoderIoConfig&& decoderIoConfig,
        DecoderWithPastIoConfig&& decoderWithPastIoConfig,
        std::unique_ptr<TokenSelector>&& tokenSelector
) : tokenSelector_(std::move(tokenSelector)),
    encoderIoConfig_(std::move(encoderIoConfig)),
    decoderIoConfig_(std::move(decoderIoConfig)),
    decoderWithPastIoConfig_(std::move(decoderWithPastIoConfig)),
    numDecoderLayers_(numDecoderLayers),
    numHeads_(numHeads),
    hiddenSize_(hiddenSize),
    vocabSize_(vocabSize) {}

EncoderDecoderWithPast::~EncoderDecoderWithPast() {
    release();
}

bool EncoderDecoderWithPast::load(
        const char* encoderPath,
        const char* decoderPath,
        const char* decoderWithPastPath
) {
    if (!loadModelSession(
            kEncoderSessionKey, encoderPath, loadedEncoderPath_, "encoder model")) {
        return false;
    }
    if (!loadModelSession(
            kDecoderSessionKey, decoderPath, loadedDecoderPath_, "decoder model")) {
        return false;
    }
    return loadModelSession(
            kDecoderWithPastSessionKey,
            decoderWithPastPath,
            loadedDecoderWithPastPath_,
            "decoder with past model");
}

bool EncoderDecoderWithPast::loadModelSession(
        const char* sessionKey,
        const char* modelPath,
        std::string& loadedPath,
        const char* modelName
) {
    if (!aideo::isInvalidPath(modelPath) &&
        loadedPath == modelPath &&
        inference_.hasSession(sessionKey)) {
        return true;
    }

    if (!inference_.loadSession(sessionKey, modelPath, modelName)) {
        return false;
    }

    loadedPath = modelPath;
    return true;
}

void EncoderDecoderWithPast::release() {
    inference_.release();
    loadedEncoderPath_.clear();
    loadedDecoderPath_.clear();
    loadedDecoderWithPastPath_.clear();
}

std::pair<int, int> EncoderDecoderWithPast::parseKvOutputName(const std::string& name) {
    static const std::regex pattern(R"(present\.(\d+)\.(decoder|encoder)\.(key|value))");
    std::smatch match;
    if (std::regex_search(name, match, pattern) && match.size() == 4) {
        int layerIdx = std::stoi(match[1].str());
        bool isEncoder = (match[2].str() == "encoder");
        bool isValue = (match[3].str() == "value");
        int typeOffset = (isEncoder ? 2 : 0) + (isValue ? 1 : 0);
        return { layerIdx, typeOffset };
    }
    return { -1, -1 };
}

std::vector<float> EncoderDecoderWithPast::runEncoder(
        const std::vector<int64_t>& inputIds,
        const std::vector<int64_t>& attentionMask,
        int64_t batchSize,
        int64_t seqLength
) {

    std::vector<float> encoderHiddenStates; // [batch_size, seq_len, hidden_size]
    auto* encoderSession = inference_.getSession(kEncoderSessionKey, "Encoder");
    if (!encoderSession) {
        return encoderHiddenStates;
    }

    Ort::AllocatorWithDefaultOptions allocator;

    try {
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeDefault);
        std::vector<int64_t> inputShape = { batchSize, seqLength };

        // 동적으로 모델의 입/출력 구조를 확인하고, 각 입/출력의(Input/Output) 인자 이름들을 가져옴
        // Session::Run() 인자로 inputNames 가 const char* 로 필요하고, 해당 포인터의 수명 주기 연장 용도로 스마트 포인터 객체를 따로 보유
        std::vector<Ort::AllocatedStringPtr> inputNamePtrs;
        std::vector<const char*> inputNames;
        for (size_t i = 0; i < encoderSession->GetInputCount(); ++i) {
            inputNamePtrs.push_back(encoderSession->GetInputNameAllocated(i, allocator));
            inputNames.push_back(inputNamePtrs.back().get());
        }

        std::vector<Ort::AllocatedStringPtr> outputNamePtrs;
        std::vector<const char*> outputNames;
        for (size_t i = 0; i < encoderSession->GetOutputCount(); ++i) {
            outputNamePtrs.push_back(encoderSession->GetOutputNameAllocated(i, allocator));
            outputNames.push_back(outputNamePtrs.back().get());
        }

        std::vector<Ort::Value> inputTensors;
        for (auto& inputName: inputNames) {
            std::string name(inputName);
            if (name == encoderIoConfig_.inputIds) {
                inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                        memoryInfo,
                        const_cast<int64_t*>(inputIds.data()), inputIds.size(),
                        inputShape.data(), inputShape.size()
                ));
            } else if (name == encoderIoConfig_.attentionMask) {
                inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                        memoryInfo,
                        const_cast<int64_t*>(attentionMask.data()), attentionMask.size(),
                        inputShape.data(), inputShape.size()
                ));
            } else {
                AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Unknown encoder input: %s", name.c_str());
                return encoderHiddenStates;
            }
        }

        auto outputTensors = encoderSession->Run(
                Ort::RunOptions{ nullptr },
                inputNames.data(), inputTensors.data(), inputTensors.size(),
                outputNames.data(), outputNames.size()
        );

        for (size_t i = 0; i < outputTensors.size() && i < outputNames.size(); ++i) {
            std::string name(outputNames[i]);
            if (name != encoderIoConfig_.lastHiddenState) {
                continue;
            }

            if (!outputTensors[i].IsTensor()) {
                AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST,
                           "Encoder output is not tensor: %s", name.c_str());
                return encoderHiddenStates;
            }

            const auto* data = outputTensors[i].GetTensorData<float>();
            auto tensorInfo = outputTensors[i].GetTensorTypeAndShapeInfo();
            encoderHiddenStates.assign(data, data + tensorInfo.GetElementCount());
            return encoderHiddenStates;
        }

        AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Missing encoder output: %s",
                   encoderIoConfig_.lastHiddenState.c_str());
    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Encoder inference failed: %s", e.what());
    }
    return encoderHiddenStates;
}

EncoderDecoderWithPast::DecoderOutput EncoderDecoderWithPast::runDecoder(
        const std::vector<int64_t>& inputIds,
        const std::vector<int64_t>& encoderAttentionMask,
        const std::vector<float>& encoderHiddenStates,
        int64_t batchSize,
        int64_t decoderSeqLength,
        int64_t encoderSeqLength) {

    DecoderOutput output;
    auto* decoderSession = inference_.getSession(kDecoderSessionKey, "Decoder");
    if (!decoderSession) {
        return output;
    }

    Ort::AllocatorWithDefaultOptions allocator;

    try {
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeDefault);

        std::vector<Ort::AllocatedStringPtr> inputNamePtrs;
        std::vector<const char*> inputNames;
        for (size_t i = 0; i < decoderSession->GetInputCount(); ++i) {
            inputNamePtrs.push_back(decoderSession->GetInputNameAllocated(i, allocator));
            inputNames.push_back(inputNamePtrs.back().get());
        }

        std::vector<int64_t> inputIdsShape = { batchSize, decoderSeqLength };
        std::vector<int64_t> attentionMaskShape = { batchSize, encoderSeqLength };
        std::vector<int64_t> encoderHiddenShape = {
                batchSize, encoderSeqLength, static_cast<int64_t>(hiddenSize_) };

        std::vector<Ort::Value> inputTensors;
        for (auto& inputName: inputNames) {
            std::string name(inputName);
            if (name == decoderIoConfig_.inputIds) {
                inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                        memoryInfo,
                        const_cast<int64_t*>(inputIds.data()), inputIds.size(),
                        inputIdsShape.data(), inputIdsShape.size()
                ));
            } else if (name == decoderIoConfig_.encoderAttentionMask) {
                inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                        memoryInfo,
                        const_cast<int64_t*>(encoderAttentionMask.data()),
                        encoderAttentionMask.size(),
                        attentionMaskShape.data(), attentionMaskShape.size()
                ));
            } else if (name == decoderIoConfig_.encoderHiddenStates) {
                inputTensors.push_back(Ort::Value::CreateTensor<float>(
                        memoryInfo,
                        const_cast<float*>(encoderHiddenStates.data()), encoderHiddenStates.size(),
                        encoderHiddenShape.data(), encoderHiddenShape.size()
                ));
            } else {
                AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Unknown decoder input: %s", name.c_str());
                return output;
            }
        }

        std::vector<Ort::AllocatedStringPtr> outputNamePtrs;
        std::vector<const char*> outputNames;
        for (size_t i = 0; i < decoderSession->GetOutputCount(); ++i) {
            outputNamePtrs.push_back(decoderSession->GetOutputNameAllocated(i, allocator));
            outputNames.push_back(outputNamePtrs.back().get());
        }

        auto outputTensors = decoderSession->Run(
                Ort::RunOptions{ nullptr },
                inputNames.data(), inputTensors.data(), inputTensors.size(),
                outputNames.data(), outputNames.size()
        );

        bool hasLogits = false;
        for (size_t i = 0; i < outputTensors.size() && i < outputNames.size(); ++i) {
            std::string name(outputNames[i]);
            if (name == decoderIoConfig_.logits) {
                if (!outputTensors[i].IsTensor()) {
                    AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST,
                               "Decoder output is not tensor: %s", name.c_str());
                    return output;
                }

                const auto* logitsData = outputTensors[i].GetTensorData<float>();
                auto tensorInfo = outputTensors[i].GetTensorTypeAndShapeInfo();
                output.logits.assign(logitsData, logitsData + tensorInfo.GetElementCount());
                hasLogits = true;
            } else if (name.compare(
                    0, decoderIoConfig_.presentPrefix.size(), decoderIoConfig_.presentPrefix) ==
                       0) {
                if (!outputTensors[i].IsTensor()) {
                    AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST,
                               "Decoder KV output is not tensor: %s", name.c_str());
                    continue;
                }

                const auto* data = outputTensors[i].GetTensorData<float>();
                auto tensorInfo = outputTensors[i].GetTensorTypeAndShapeInfo();
                output.presentKeyValues.emplace_back(data, data + tensorInfo.GetElementCount());
                output.presentKeyValueShapes.push_back(tensorInfo.GetShape());
                output.kvOutputNames.push_back(name);
            }
        }

        if (!hasLogits) {
            AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Missing decoder output: %s",
                       decoderIoConfig_.logits.c_str());
        }
    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Decoder inference failed: %s", e.what());
    }
    return output;
}

EncoderDecoderWithPast::DecoderOutput EncoderDecoderWithPast::runDecoderWithPast(
        const std::vector<int64_t>& decoderInputIds,
        const std::vector<int64_t>& encoderAttentionMask,
        const std::vector<float>& encoderHiddenStates,
        const std::vector<std::vector<float>>& pastKeyValues,
        const std::vector<std::vector<int64_t>>& pastKeyValueShapes,
        int64_t batchSize,
        int64_t encoderSeqLength
) {

    DecoderOutput output;
    auto* decoderWithPastSession = inference_.getSession(
            kDecoderWithPastSessionKey, "Decoder with past");
    if (!decoderWithPastSession) {
        return output;
    }

    Ort::AllocatorWithDefaultOptions allocator;

    try {
        auto memoryInfo = Ort::MemoryInfo::CreateCpu(OrtDeviceAllocator, OrtMemTypeDefault);

        std::vector<Ort::AllocatedStringPtr> modelInputNamePtrs;
        std::vector<std::string> modelInputNames;
        for (size_t i = 0; i < decoderWithPastSession->GetInputCount(); ++i) {
            modelInputNamePtrs.push_back(
                    decoderWithPastSession->GetInputNameAllocated(i, allocator));
            modelInputNames.emplace_back(modelInputNamePtrs.back().get());
        }

        std::vector<int64_t> inputIdsShape = { batchSize, 1 };
        std::vector<int64_t> encoderAttentionMaskShape = { batchSize, encoderSeqLength };
        std::vector<int64_t> encoderHiddenShape = { batchSize, encoderSeqLength,
                                                    static_cast<int64_t>(hiddenSize_) };

        const size_t requiredKvCacheCount = static_cast<size_t>(numDecoderLayers_) * 4;
        if (pastKeyValues.size() < requiredKvCacheCount ||
            pastKeyValueShapes.size() < requiredKvCacheCount) {
            AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "KV cache index out of bounds");
            return output;
        }

        std::vector<Ort::Value> inputTensors;
        std::vector<const char*> inputNames;
        for (const auto& name: modelInputNames) {
            if (name == decoderWithPastIoConfig_.inputIds) {
                inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                        memoryInfo,
                        const_cast<int64_t*>(decoderInputIds.data()), decoderInputIds.size(),
                        inputIdsShape.data(), inputIdsShape.size()
                ));
                inputNames.push_back(name.c_str());
            } else if (name == decoderWithPastIoConfig_.encoderAttentionMask) {
                inputTensors.push_back(Ort::Value::CreateTensor<int64_t>(
                        memoryInfo,
                        const_cast<int64_t*>(encoderAttentionMask.data()),
                        encoderAttentionMask.size(),
                        encoderAttentionMaskShape.data(), encoderAttentionMaskShape.size()
                ));
                inputNames.push_back(name.c_str());
            } else if (name == decoderWithPastIoConfig_.encoderHiddenStates) {
                inputTensors.push_back(Ort::Value::CreateTensor<float>(
                        memoryInfo,
                        const_cast<float*>(encoderHiddenStates.data()), encoderHiddenStates.size(),
                        encoderHiddenShape.data(), encoderHiddenShape.size()
                ));
                inputNames.push_back(name.c_str());
            } else {
                bool matchedPastKeyValue = false;
                for (int i = 0; i < numDecoderLayers_ && !matchedPastKeyValue; ++i) {
                    const size_t baseIdx = static_cast<size_t>(i) * 4;
                    const std::string pastLayerPrefix =
                            decoderWithPastIoConfig_.pastKeyValuesPrefix + std::to_string(i) + ".";

                    int valueOffset = -1;
                    if (name == pastLayerPrefix + decoderWithPastIoConfig_.decoderKey) {
                        valueOffset = 0;
                    } else if (name == pastLayerPrefix + decoderWithPastIoConfig_.decoderValue) {
                        valueOffset = 1;
                    } else if (name == pastLayerPrefix + decoderWithPastIoConfig_.encoderKey) {
                        valueOffset = 2;
                    } else if (name == pastLayerPrefix + decoderWithPastIoConfig_.encoderValue) {
                        valueOffset = 3;
                    }

                    if (valueOffset < 0) {
                        continue;
                    }

                    const size_t pastIdx = baseIdx + static_cast<size_t>(valueOffset);
                    inputTensors.push_back(Ort::Value::CreateTensor<float>(
                            memoryInfo,
                            const_cast<float*>(pastKeyValues[pastIdx].data()),
                            pastKeyValues[pastIdx].size(),
                            pastKeyValueShapes[pastIdx].data(),
                            pastKeyValueShapes[pastIdx].size()
                    ));
                    inputNames.push_back(name.c_str());
                    matchedPastKeyValue = true;
                }

                if (!matchedPastKeyValue) {
                    AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Unknown decoder with past input: %s",
                               name.c_str());
                    return output;
                }
            }
        }

        // 출력 이름: 동적으로 모델에서 가져옴
        std::vector<Ort::AllocatedStringPtr> outputNamePtrs;
        std::vector<const char*> outputNames;
        for (size_t i = 0; i < decoderWithPastSession->GetOutputCount(); ++i) {
            outputNamePtrs.push_back(decoderWithPastSession->GetOutputNameAllocated(i, allocator));
            outputNames.push_back(outputNamePtrs.back().get());
        }

        auto outputTensors = decoderWithPastSession->Run(
                Ort::RunOptions{ nullptr },
                inputNames.data(), inputTensors.data(), inputTensors.size(),
                outputNames.data(), outputNames.size()
        );

        bool hasLogits = false;
        for (size_t i = 0; i < outputTensors.size() && i < outputNames.size(); ++i) {
            std::string name(outputNames[i]);
            if (name == decoderWithPastIoConfig_.logits) {
                if (!outputTensors[i].IsTensor()) {
                    AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST,
                               "Decoder with past output is not tensor: %s", name.c_str());
                    return output;
                }

                const auto* logitsData = outputTensors[i].GetTensorData<float>();
                auto tensorInfo = outputTensors[i].GetTensorTypeAndShapeInfo();
                output.logits.assign(logitsData, logitsData + tensorInfo.GetElementCount());
                hasLogits = true;
            } else if (name.compare(0,
                                    decoderWithPastIoConfig_.presentPrefix.size(),
                                    decoderWithPastIoConfig_.presentPrefix) == 0) {
                if (!outputTensors[i].IsTensor()) {
                    AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST,
                               "Decoder with past KV output is not tensor: %s", name.c_str());
                    continue;
                }

                const auto* data = outputTensors[i].GetTensorData<float>();
                auto tensorInfo = outputTensors[i].GetTensorTypeAndShapeInfo();
                output.presentKeyValues.emplace_back(data, data + tensorInfo.GetElementCount());
                output.presentKeyValueShapes.push_back(tensorInfo.GetShape());
                output.kvOutputNames.push_back(name);
            }
        }

        if (!hasLogits) {
            AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Missing decoder with past output: %s",
                       decoderWithPastIoConfig_.logits.c_str());
        }

    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Decoder with past inference failed: %s", e.what());
    }
    return output;
}

std::vector<int64_t> EncoderDecoderWithPast::generateSingle(
        const std::vector<int64_t>& encoderInputIds,
        const std::vector<int64_t>& encoderAttentionMask,
        const std::vector<int64_t>& initialDecoderInputIds,
        int64_t eosTokenId,
        int maxLength
) {

    std::vector<int64_t> generatedTokens;
    if (!inference_.hasSession(kEncoderSessionKey) ||
        !inference_.hasSession(kDecoderSessionKey) ||
        !inference_.hasSession(kDecoderWithPastSessionKey)) {
        AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Encoder-decoder sessions not loaded");
        return generatedTokens;
    }

    try {
        auto encoderSeqLen = static_cast<int64_t>(encoderInputIds.size());
        if (encoderInputIds.size() != encoderAttentionMask.size()) {
            AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST,
                       "Encoder input ids and attention mask size mismatch: %zu != %zu",
                       encoderInputIds.size(), encoderAttentionMask.size());
            return generatedTokens;
        }

        // 단계 2: Encoder 실행
        auto encoderHiddenStates = runEncoder(encoderInputIds, encoderAttentionMask, 1,
                                              encoderSeqLen);
        if (encoderHiddenStates.empty()) {
            AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST, "Encoder returned empty output");
            return generatedTokens;
        }

        // 단계 4: 첫 번째 Decoder 실행 (KV 캐시 초기화)
        auto decoderOutput = runDecoder(
                initialDecoderInputIds,
                encoderAttentionMask,
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
                if (allKVCache[i].empty()) {
                    emptySlots++;
                }
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
            if (nextToken == eosTokenId) {
                break;
            }

            nextInputIds[0] = nextToken;

            auto nextOutput = runDecoderWithPast(
                    nextInputIds,
                    encoderAttentionMask,
                    encoderHiddenStates,
                    allKVCache,
                    allKVShapes,
                    1, // batch_size
                    encoderSeqLen
            );

            if (nextOutput.logits.empty()) {
                AIDEO_LOGE(LOG_TAG_ENC_DEC_WITH_PAST,
                           "DecoderWithPast returned empty logits at step %d", step);
                break;
            }

            nextToken = tokenSelector_->select(nextOutput.logits, vocabSize_, eosTokenId);
            generatedTokens.push_back(nextToken);

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
                    allKVCache = std::move(nextOutput.presentKeyValues);
                    allKVShapes = std::move(nextOutput.presentKeyValueShapes);
                } else if (kvOutputSize == static_cast<size_t>(numDecoderLayers_ * 2)) {
                    for (int i = 0; i < numDecoderLayers_; ++i) {
                        int allIdx = i * 4;
                        int decIdx = i * 2;
                        allKVCache[allIdx] = std::move(nextOutput.presentKeyValues[decIdx]);
                        allKVCache[allIdx + 1] = std::move(nextOutput.presentKeyValues[decIdx + 1]);
                        allKVShapes[allIdx] = std::move(nextOutput.presentKeyValueShapes[decIdx]);
                        allKVShapes[allIdx + 1] = std::move(
                                nextOutput.presentKeyValueShapes[decIdx + 1]);
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
