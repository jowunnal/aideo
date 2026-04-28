//
// Created by PC on 2026-01-18.
//

#include "m2m100_translator.h"
#include "json.hpp"
#include "path_utils.h"
#include <exception>
#include <fstream>
#include <utility>
#include <vector>

using json = nlohmann::json;

M2M100Translator::M2M100Translator()
        : decoder_(NUM_DECODER_LAYERS, NUM_HEADS, HIDDEN_SIZE, VOCAB_SIZE) {
}

M2M100Translator::~M2M100Translator() {
    release();
}

bool M2M100Translator::load(
        const char* encoderPath,
        const char* decoderPath,
        const char* decoderWithPastPath,
        const char* spModelPath,
        const char* vocabPath,
        const char* tokenizerConfigPath
) {
    setLoaded(false);

    try {
        // 1. ONNX 모델 로드
        if (!decoder_.load(encoderPath, decoderPath, decoderWithPastPath)) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Failed to load ONNX models");
            return false;
        }

        // 2. 토크나이저 로드 (SentencePiece model + vocab.json)
        if (!tokenizer_.load(spModelPath, vocabPath)) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Failed to load tokenizer");
            return false;
        }

        // 3. 언어 토큰 매핑 로드 (tokenizer_config.json의 added_tokens_decoder에서 추출)
        if (!loadLanguageTokens(tokenizerConfigPath)) {
            AIDEO_LOGE(LOG_TAG_M2M100, "Failed to load language token map");
            return false;
        }

        // 특수 토큰 설정 (M2M100 자체 멤버 — base 에는 special token 개념 없음)
        eosTokenId_ = tokenizer_.getEosTokenId();
        setLoaded(true);
        return true;

    } catch (const std::exception& e) {
        // 네이티브 메모리 공간에서 발생한 예외를 JVM 에서 잡을 수 없기 때문에(바로 크래시 발생) 예외 처리가 매우 중요
        AIDEO_LOGE(LOG_TAG_M2M100, "Failed to load M2M100: %s", e.what());
        return false;
    }
}

bool M2M100Translator::loadLanguageTokens(const char* tokenizerConfigPath) {
    if (aideo::isInvalidPath(tokenizerConfigPath)) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Invalid tokenizer_config.json path");
        return false;
    }

    std::string nextPath(tokenizerConfigPath);
    if (!languageTokens_.empty() && loadedTokenizerConfigPath_ == nextPath) {
        return true;
    }

    std::ifstream file(tokenizerConfigPath);
    if (!file.is_open()) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Failed to open tokenizer_config.json");
        return false;
    }

    try {
        json config = json::parse(file);
        if (!config.contains("added_tokens_decoder") ||
            !config["added_tokens_decoder"].is_object()) {
            AIDEO_LOGE(LOG_TAG_M2M100, "tokenizer_config.json has no added_tokens_decoder");
            return false;
        }

        LanguageTokenMap nextLanguageTokens;
        auto& decoder = config["added_tokens_decoder"];
        for (auto& [idStr, tokenInfo]: decoder.items()) {
            if (!tokenInfo.contains("content") || !tokenInfo["content"].is_string()) {
                continue;
            }

            const auto& content = tokenInfo["content"].get_ref<const std::string&>();
            // "__ko__" -> "ko"
            if (content.size() > 4 &&
                content.compare(0, 2, "__") == 0 &&
                content.compare(content.size() - 2, 2, "__") == 0) {
                std::string langCode = content.substr(2, content.size() - 4);
                nextLanguageTokens.registerToken(langCode, std::stoll(idStr));
            }
        }

        if (nextLanguageTokens.empty()) {
            AIDEO_LOGE(LOG_TAG_M2M100, "No language tokens found in tokenizer_config.json");
            return false;
        }

        languageTokens_ = std::move(nextLanguageTokens);
        loadedTokenizerConfigPath_ = std::move(nextPath);
        return true;
    } catch (const std::exception& e) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Failed to parse tokenizer_config.json: %s", e.what());
        return false;
    }
}

std::string M2M100Translator::translate(
        const std::string& text,
        const std::string& srcLang,
        const std::string& tgtLang,
        int maxLength) {

    if (!isLoaded()) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Model not loaded");
        return "";
    }

    int64_t srcLangId;
    int64_t tgtLangId;
    if (!languageTokens_.resolvePair(srcLang, tgtLang, srcLangId, tgtLangId)) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Unsupported language: src=%s, tgt=%s",
                   srcLang.c_str(), tgtLang.c_str());
        return "";
    }

    try {
        // 1. M2M100 형식 encoder input: [srcLangId, ...textTokens, eos]
        auto textTokens = tokenizer_.encode(text);

        std::vector<int64_t> encoderInputIds;
        encoderInputIds.reserve(textTokens.size() + 2);
        encoderInputIds.push_back(srcLangId);
        encoderInputIds.insert(encoderInputIds.end(), textTokens.begin(), textTokens.end());
        encoderInputIds.push_back(eosTokenId_);
        std::vector<int64_t> encoderAttentionMask(encoderInputIds.size(), 1);

        // 2. M2M100 형식 initial decoder input: [eos, tgtLangId]
        std::vector<int64_t> initialDecoderInputIds = { eosTokenId_, tgtLangId };

        // 3. 디코딩
        auto generatedTokens = decoder_.generateSingle(
                encoderInputIds, encoderAttentionMask, initialDecoderInputIds, eosTokenId_,
                maxLength);

        // 4. 토큰 디코딩
        return tokenizer_.decode(generatedTokens);

    } catch (const std::exception& e) {
        AIDEO_LOGE(LOG_TAG_M2M100, "Translation failed: %s", e.what());
        return "";
    }
}

void M2M100Translator::release() {
    decoder_.release();
    tokenizer_.release();
    languageTokens_.clear();
    loadedTokenizerConfigPath_.clear();
    Translator::release();
}
