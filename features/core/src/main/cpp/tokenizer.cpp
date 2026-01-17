#include "tokenizer.h"
#include "json.hpp"
#include <fstream>
#include <algorithm>
#include <limits>
#include <cmath>
using json = nlohmann::json;

// SentencePiece 특수 문자 (U+2581)
static const std::string SPIECE_UNDERLINE = "▁";

Tokenizer::Tokenizer() {}

bool Tokenizer::load(const std::string& tokenizerPath) {
    try {
        AIDEO_LOGI(LOG_TAG_TOKENIZER, "Loading tokenizer from: %s", tokenizerPath.c_str());

        std::ifstream file(tokenizerPath);
        if (!file.is_open()) {
            AIDEO_LOGE(LOG_TAG_TOKENIZER, "Failed to open tokenizer file");
            return false;
        }

        json data = json::parse(file);

        // Added tokens (special tokens) 로드
        if (data.contains("added_tokens")) {
            for (const auto& token : data["added_tokens"]) {
                std::string content = token["content"];
                int64_t id = token["id"];
                addedTokens_[content] = id;
                idToToken_[id] = content;

                // 특수 토큰 ID 저장
                if (content == "<pad>") padTokenId_ = id;
                else if (content == "</s>") eosTokenId_ = id;
                else if (content == "<unk>") unkTokenId_ = id;
            }
        }

        // Vocab 로드 (Unigram 형식: [[token, score], ...])
        if (data.contains("model") && data["model"].contains("vocab")) {
            for (const auto& item : data["model"]["vocab"]) {
                std::string token = item[0];
                float score = item[1];
                int64_t id = vocab_.size();

                // added_tokens에 이미 있으면 스킵
                if (addedTokens_.find(token) != addedTokens_.end()) {
                    continue;
                }

                vocab_[token] = {id, score};
                idToToken_[id] = token;
            }
        }

        AIDEO_LOGI(LOG_TAG_TOKENIZER, "Tokenizer loaded: %zu vocab, %zu added tokens",
             vocab_.size(), addedTokens_.size());
        return true;

    } catch (const std::exception& e) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "Failed to load tokenizer: %s", e.what());
        return false;
    }
}

std::string Tokenizer::preprocess(const std::string& text) {
    // 텍스트 앞에 ▁ 추가, 공백을 ▁로 변환
    std::string result = SPIECE_UNDERLINE;
    for (char c : text) {
        if (c == ' ') {
            result += SPIECE_UNDERLINE;
        } else {
            result += c;
        }
    }
    return result;
}

std::string Tokenizer::postprocess(const std::string& text) {
    // ▁를 공백으로 변환, 앞 공백 제거
    std::string result;
    size_t i = 0;

    while (i < text.size()) {
        // UTF-8 ▁ 체크 (3바이트: E2 96 81)
        if (i + 2 < text.size() &&
            (unsigned char)text[i] == 0xE2 &&
            (unsigned char)text[i+1] == 0x96 &&
            (unsigned char)text[i+2] == 0x81) {
            if (!result.empty()) {
                result += ' ';
            }
            i += 3;
        } else {
            result += text[i];
            i++;
        }
    }
    return result;
}

std::vector<int64_t> Tokenizer::encodeUnigram(const std::string& text) {
    size_t len = text.size();
    if (len == 0) {
        return {};
    }

    // best_path_ends_at[i] = position i에서 끝나는 최적 경로 정보
    std::vector<BestPathNode> best_path_ends_at(len + 1);

    // 초기화: 모든 위치를 무효(-1)로 설정
    for (size_t i = 0; i <= len; ++i) {
        best_path_ends_at[i] = {-1, -std::numeric_limits<float>::infinity(), 0};
    }

    // 시작점 초기화
    best_path_ends_at[0] = {-1, 0.0f, 0};

    // Forward pass: 각 위치에서 시작하는 모든 토큰 검사
    for (size_t pos = 0; pos < len; ++pos) {
        // 이 위치에 도달할 수 있는 경로가 없으면 스킵
        if (best_path_ends_at[pos].id == -1 && pos > 0) {
            continue;
        }

        // 현재 위치에서 시작하는 모든 가능한 토큰 검사
        for (size_t end = pos + 1; end <= len && end <= pos + 32; ++end) {
            std::string substr = text.substr(pos, end - pos);

            // Added tokens 먼저 확인
            auto addedIt = addedTokens_.find(substr);
            if (addedIt != addedTokens_.end()) {
                float score = best_path_ends_at[pos].score + 0.0f;  // special tokens have score 0
                if (best_path_ends_at[end].id == -1 || score > best_path_ends_at[end].score) {
                    best_path_ends_at[end] = {addedIt->second, score, pos};
                }
                continue;
            }

            // Vocab에서 찾기
            auto it = vocab_.find(substr);
            if (it != vocab_.end()) {
                float score = best_path_ends_at[pos].score + it->second.second;
                if (best_path_ends_at[end].id == -1 || score > best_path_ends_at[end].score) {
                    best_path_ends_at[end] = {it->second.first, score, pos};
                }
            }
        }
    }

    // 끝에 도달하지 못한 경우 (unknown 문자 처리)
    if (best_path_ends_at[len].id == -1) {
        // Fallback: 단일 문자씩 UNK로 처리
        std::vector<int64_t> tokens;
        size_t i = 0;
        while (i < len) {
            // UTF-8 문자 길이 계산
            unsigned char c = text[i];
            size_t charLen = 1;
            if ((c & 0x80) == 0) charLen = 1;
            else if ((c & 0xE0) == 0xC0) charLen = 2;
            else if ((c & 0xF0) == 0xE0) charLen = 3;
            else if ((c & 0xF8) == 0xF0) charLen = 4;

            // 이 문자에 대한 토큰 찾기
            std::string charStr = text.substr(i, charLen);
            auto it = vocab_.find(charStr);
            if (it != vocab_.end()) {
                tokens.push_back(it->second.first);
            } else {
                tokens.push_back(unkTokenId_);
            }
            i += charLen;
        }
        return tokens;
    }

    // Backtracking: 끝에서부터 시작점까지 역추적
    std::vector<int64_t> tokens;
    size_t pos = len;
    while (pos > 0) {
        tokens.push_back(best_path_ends_at[pos].id);
        pos = best_path_ends_at[pos].starts_at;
    }

    // 역순으로 수집했으므로 뒤집기
    std::reverse(tokens.begin(), tokens.end());

    return tokens;
}

std::vector<int64_t> Tokenizer::encode(const std::string& text, bool addEos) {
    // 전처리
    std::string processed = preprocess(text);

    // Unigram encoding (Viterbi)
    std::vector<int64_t> tokens = encodeUnigram(processed);

    // EOS 토큰 추가
    if (addEos) {
        tokens.push_back(eosTokenId_);
    }

    AIDEO_LOGI(LOG_TAG_TOKENIZER, "Encoded '%s' -> %zu tokens", text.c_str(), tokens.size());
    return tokens;
}

std::string Tokenizer::decode(const std::vector<int64_t>& tokenIds) {
    std::string result;

    for (int64_t id : tokenIds) {
        // 특수 토큰 스킵
        if (id == padTokenId_ || id == eosTokenId_) {
            continue;
        }

        auto it = idToToken_.find(id);
        if (it != idToToken_.end()) {
            result += it->second;
        }
    }

    // 후처리
    result = postprocess(result);

    AIDEO_LOGI(LOG_TAG_TOKENIZER, "Decoded %zu tokens -> '%s'", tokenIds.size(), result.c_str());
    return result;
}
