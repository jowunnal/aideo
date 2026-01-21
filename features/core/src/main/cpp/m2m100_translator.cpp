//
// Created by PC on 2026-01-18.
//

#include "m2m100_translator.h"
#include "json.hpp"
#include <fstream>
#include <algorithm>
#include <regex>

using json = nlohmann::json;

// KV output 이름에서 레이어 인덱스와 타입을 파싱하는 헬퍼 함수
// 이름 형식: "present.X.decoder.key", "present.X.decoder.value", "present.X.encoder.key", "present.X.encoder.value"
// 반환값: {레이어 인덱스, 타입 오프셋 (0=dec_key, 1=dec_val, 2=enc_key, 3=enc_val)}, 실패시 {-1, -1}
static std::pair<int, int> parseKvOutputName(const std::string& name) {
    // present.X.decoder.key 또는 present.X.encoder.key 등의 형식 파싱
    std::regex pattern(R"(present\.(\d+)\.(decoder|encoder)\.(key|value))");
    std::smatch match;

    if (std::regex_search(name, match, pattern) && match.size() == 4) {
        int layerIdx = std::stoi(match[1].str());
        bool isEncoder = (match[2].str() == "encoder");
        bool isValue = (match[3].str() == "value");

        // 오프셋: dec_key=0, dec_val=1, enc_key=2, enc_val=3
        int typeOffset = (isEncoder ? 2 : 0) + (isValue ? 1 : 0);
        return {layerIdx, typeOffset};
    }

    return {-1, -1};
}

M2M100Translator::M2M100Translator() {
    // 모델 파라미터 설정
    inference_.setNumDecoderLayers(NUM_DECODER_LAYERS);
    inference_.setNumHeads(NUM_HEADS);
    inference_.setHiddenSize(HIDDEN_SIZE);
}

M2M100Translator::~M2M100Translator() {
    release();
}

bool M2M100Translator::load(
        const std::string& encoderPath,
        const std::string& decoderPath,
        const std::string& decoderWithPastPath,
        const std::string& spModelPath,
        const std::string& vocabPath,
        const std::string& tokenizerConfigPath) {

    try {
        // 1. ONNX 모델 로드
        if (!inference_.loadEncoderDecoderModel(encoderPath, decoderPath, decoderWithPastPath)) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Failed to load ONNX models");
            return false;
        }

        // 2. 토크나이저 로드 (SentencePiece model + vocab.json)
        if (!tokenizer_.loadM2M100(spModelPath, vocabPath)) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Failed to load tokenizer");
            return false;
        }

        // 3. 언어 토큰 매핑 로드 (tokenizer_config.json의 added_tokens_decoder에서 추출)
        std::ifstream file(tokenizerConfigPath);
        if (!file.is_open()) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Failed to open tokenizer_config.json");
            return false;
        }

        json config = json::parse(file);
        if (config.contains("added_tokens_decoder")) {
            auto& decoder = config["added_tokens_decoder"];
            for (auto& [idStr, tokenInfo] : decoder.items()) {
                std::string content = tokenInfo["content"].get<std::string>();
                // "__ko__" -> "ko"
                if (content.size() > 4 && content.substr(0, 2) == "__" && content.substr(content.size() - 2) == "__") {
                    std::string langCode = content.substr(2, content.size() - 4);
                    langToTokenId_[langCode] = std::stoll(idStr);
                }
            }
        }

        // 특수 토큰 설정
        eosTokenId_ = tokenizer_.getEosTokenId();
        padTokenId_ = tokenizer_.getPadTokenId();

        isLoaded_ = true;
        return true;

    } catch (const std::exception& e) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Failed to load M2M100: %s", e.what());
        return false;
    }
}

std::string M2M100Translator::translate(
        const std::string& text,
        const std::string& srcLang,
        const std::string& tgtLang,
        int maxLength) {

    if (!isLoaded_) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Model not loaded");
        return "";
    }

    int64_t srcLangId = getLanguageTokenId(srcLang);
    int64_t tgtLangId = getLanguageTokenId(tgtLang);

    if (srcLangId == -1 || tgtLangId == -1) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Unsupported language: src=%s, tgt=%s",
                   srcLang.c_str(), tgtLang.c_str());
        return "";
    }

    try {
        // 1. 입력 텍스트 토큰화
        // M2M100 형식: __src_lang__ text </s>
        std::vector<int64_t> inputIds;
        inputIds.push_back(srcLangId);  // 소스 언어 토큰이 먼저
        auto textTokens = tokenizer_.encode(text, false);  // EOS 없이
        inputIds.insert(inputIds.end(), textTokens.begin(), textTokens.end());
        inputIds.push_back(eosTokenId_);

        int64_t encoderSeqLen = static_cast<int64_t>(inputIds.size());
        std::vector<int64_t> attentionMask(encoderSeqLen, 1);

        // 2. Encoder 실행
        auto encoderHiddenStates = inference_.runEncoder(
                inputIds, attentionMask, 1, encoderSeqLen
        );

        if (encoderHiddenStates.empty()) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Encoder returned empty output");
            return "";
        }

        // 3. Decoder 시작 토큰 설정
        // M2M100 형식: </s> __tgt_lang__
        std::vector<int64_t> decoderInputIds = {eosTokenId_, tgtLangId};
        std::vector<int64_t> generatedTokens;

        // 4. 첫 번째 Decoder 실행 (KV 캐시 초기화)
        auto decoderOutput = inference_.runDecoder(
                decoderInputIds,
                attentionMask,
                encoderHiddenStates,
                1,  // batch_size
                static_cast<int64_t>(decoderInputIds.size()),  // decoder_seq_len
                encoderSeqLen
        );

        if (decoderOutput.logits.empty()) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Decoder returned empty logits");
            return "";
        }

        // 5. 다음 토큰 선택 (마지막 위치의 logits 사용)
        int64_t nextToken = selectNextToken(decoderOutput.logits, VOCAB_SIZE);
        generatedTokens.push_back(nextToken);

        // 6. Autoregressive generation with KV cache
        // 모든 KV 캐시를 하나의 벡터로 유지 (48 tensors)
        // 순서: [layer0_dec_key, layer0_dec_val, layer0_enc_key, layer0_enc_val, ...]
        std::vector<std::vector<float>> allKVCache;
        std::vector<std::vector<int64_t>> allKVShapes;
        allKVCache.resize(NUM_DECODER_LAYERS * 4);
        allKVShapes.resize(NUM_DECODER_LAYERS * 4);

        // 초기 decoder output에서 KV 캐시를 이름 기반으로 정렬하여 저장
        if (decoderOutput.kvOutputNames.size() == decoderOutput.presentKeyValues.size()) {
            // 이름 기반 매핑 사용
            for (size_t i = 0; i < decoderOutput.presentKeyValues.size(); ++i) {
                auto [layerIdx, typeOffset] = parseKvOutputName(decoderOutput.kvOutputNames[i]);
                if (layerIdx >= 0 && layerIdx < NUM_DECODER_LAYERS && typeOffset >= 0) {
                    int targetIdx = layerIdx * 4 + typeOffset;
                    allKVCache[targetIdx] = std::move(decoderOutput.presentKeyValues[i]);
                    allKVShapes[targetIdx] = std::move(decoderOutput.presentKeyValueShapes[i]);
                } else {
                    AIDEO_LOGW(LOG_TAG_M2M100, "Could not parse KV name: %s", decoderOutput.kvOutputNames[i].c_str());
                }
            }

            // 빈 슬롯 확인
            int emptySlots = 0;
            for (int i = 0; i < NUM_DECODER_LAYERS * 4; ++i) {
                if (allKVCache[i].empty()) {
                    emptySlots++;
                }
            }
            if (emptySlots > 0) {
                AIDEO_LOGE(LOG_TAG_M2M100, "WARNING: %d KV cache slots are empty after initial setup!", emptySlots);
            }
        } else {
            // 이름 없으면 순서대로 (기존 방식 - fallback)
            for (size_t i = 0; i < decoderOutput.presentKeyValues.size(); ++i) {
                allKVCache[i] = std::move(decoderOutput.presentKeyValues[i]);
                allKVShapes[i] = std::move(decoderOutput.presentKeyValueShapes[i]);
            }
        }

        // 다음 토큰 입력용 벡터 (재사용)
        std::vector<int64_t> nextInputIds(1);

        for (int step = 0; step < maxLength - 1; ++step) {
            if (nextToken == eosTokenId_) {
                break;
            }

            nextInputIds[0] = nextToken;

            auto nextOutput = inference_.runDecoderWithPast(
                    nextInputIds,
                    attentionMask,
                    encoderHiddenStates,
                    allKVCache,
                    allKVShapes,
                    1,  // batch_size
                    encoderSeqLen
            );

            if (nextOutput.logits.empty()) {
                AIDEO_LOGE(LOG_TAG_M2M100, "DecoderWithPast returned empty logits at step %d", step);
                break;
            }

            // 다음 토큰 선택
            nextToken = selectNextToken(nextOutput.logits, VOCAB_SIZE);
            generatedTokens.push_back(nextToken);

            // KV 캐시 업데이트 - 이름 기반 매핑 사용
            if (nextOutput.kvOutputNames.size() == nextOutput.presentKeyValues.size()) {
                // 이름 기반으로 업데이트
                for (size_t i = 0; i < nextOutput.presentKeyValues.size(); ++i) {
                    auto [layerIdx, typeOffset] = parseKvOutputName(nextOutput.kvOutputNames[i]);
                    if (layerIdx >= 0 && layerIdx < NUM_DECODER_LAYERS && typeOffset >= 0) {
                        int targetIdx = layerIdx * 4 + typeOffset;
                        allKVCache[targetIdx] = std::move(nextOutput.presentKeyValues[i]);
                        allKVShapes[targetIdx] = std::move(nextOutput.presentKeyValueShapes[i]);
                    } else {
                        if (step == 0) {
                            AIDEO_LOGW(LOG_TAG_M2M100, "Update: could not parse KV name: %s", nextOutput.kvOutputNames[i].c_str());
                        }
                    }
                }
            } else {
                // Fallback: 기존 인덱스 기반 방식
                size_t kvOutputSize = nextOutput.presentKeyValues.size();

                if (kvOutputSize == 48) {
                    // 전체 KV 캐시 출력 -> move로 교체
                    allKVCache = std::move(nextOutput.presentKeyValues);
                    allKVShapes = std::move(nextOutput.presentKeyValueShapes);
                } else if (kvOutputSize == 24) {
                    // decoder KV만 출력 -> decoder 부분만 업데이트
                    for (int i = 0; i < NUM_DECODER_LAYERS; ++i) {
                        int allIdx = i * 4;      // 전체 캐시에서의 인덱스
                        int decIdx = i * 2;      // decoder 출력에서의 인덱스
                        allKVCache[allIdx] = std::move(nextOutput.presentKeyValues[decIdx]);
                        allKVCache[allIdx + 1] = std::move(nextOutput.presentKeyValues[decIdx + 1]);
                        allKVShapes[allIdx] = std::move(nextOutput.presentKeyValueShapes[decIdx]);
                        allKVShapes[allIdx + 1] = std::move(nextOutput.presentKeyValueShapes[decIdx + 1]);
                        // encoder KV (allIdx+2, allIdx+3)는 그대로 유지
                    }
                } else {
                    AIDEO_LOGE(LOG_TAG_M2M100, "Unexpected KV cache size: %zu (expected 24 or 48)", kvOutputSize);
                    break;
                }
            }
        }

        // 7. 토큰 디코딩
        std::string result = tokenizer_.decode(generatedTokens);
        return result;

    } catch (const std::exception& e) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Translation failed: %s", e.what());
        return "";
    }
}

int64_t M2M100Translator::getLanguageTokenId(const std::string& lang) const {
    auto it = langToTokenId_.find(lang);
    if (it != langToTokenId_.end()) {
        return it->second;
    }
    return -1;
}

int64_t M2M100Translator::selectNextToken(const std::vector<float>& logits, int64_t vocabSize) {
    // Greedy decoding: 가장 높은 확률의 토큰 선택
    // logits shape: [batch_size, seq_len, vocab_size]
    // 마지막 위치의 logits 사용

    if (logits.empty()) {
        return eosTokenId_;
    }

    size_t logitsSize = logits.size();
    size_t vocabSizeU = static_cast<size_t>(vocabSize);

    // 마지막 토큰 위치의 logits 시작점 계산
    size_t lastTokenOffset = 0;
    if (logitsSize >= vocabSizeU) {
        lastTokenOffset = logitsSize - vocabSizeU;
    }

    // 실제 검사할 vocab 크기 (logits 범위 내)
    size_t actualVocabSize = std::min(vocabSizeU, logitsSize - lastTokenOffset);

    if (actualVocabSize == 0) {
        return eosTokenId_;
    }

    int64_t maxIdx = 0;
    float maxVal = logits[lastTokenOffset];

    for (size_t i = 1; i < actualVocabSize; ++i) {
        if (logits[lastTokenOffset + i] > maxVal) {
            maxVal = logits[lastTokenOffset + i];
            maxIdx = static_cast<int64_t>(i);
        }
    }

    return maxIdx;
}

bool M2M100Translator::isLanguageSupported(const std::string& lang) const {
    return langToTokenId_.find(lang) != langToTokenId_.end();
}

void M2M100Translator::release() {
    inference_.release();
    langToTokenId_.clear();
    isLoaded_ = false;
}