#include "tokenizer.h"
#include "json.hpp"
#include <fstream>
using json = nlohmann::json;

Tokenizer::Tokenizer() : sp_(std::make_unique<sentencepiece::SentencePieceProcessor>()) {}

Tokenizer::~Tokenizer() = default;

bool Tokenizer::loadM2M100(const std::string& spModelPath, const std::string& vocabPath) {
    try {
        // 1. SentencePiece 모델 로드 (텍스트 → pieces 변환용)
        auto status = sp_->Load(spModelPath);
        if (!status.ok()) {
            AIDEO_LOGE(LOG_TAG_TOKENIZER, "Failed to load SentencePiece model: %s", status.ToString().c_str());
            return false;
        }

        // 2. vocab.json 로드 (piece string → model ID 매핑)
        std::ifstream vocabFile(vocabPath);
        if (!vocabFile.is_open()) {
            AIDEO_LOGE(LOG_TAG_TOKENIZER, "Failed to open vocab.json: %s", vocabPath.c_str());
            return false;
        }

        json vocabJson = json::parse(vocabFile);

        for (auto& [piece, id] : vocabJson.items()) {
            int64_t tokenId = id.get<int64_t>();
            vocab_[piece] = tokenId;
            reverseVocab_[tokenId] = piece;

            // 특수 토큰 ID 설정
            if (piece == "<s>") bosTokenId_ = tokenId;
            else if (piece == "<pad>") padTokenId_ = tokenId;
            else if (piece == "</s>") eosTokenId_ = tokenId;
            else if (piece == "<unk>") unkTokenId_ = tokenId;
        }

        return true;

    } catch (const std::exception& e) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "Failed to load M2M100 tokenizer: %s", e.what());
        return false;
    }
}

std::vector<int64_t> Tokenizer::encode(const std::string& text, bool addEos) {
    // 1. SentencePiece로 텍스트를 pieces로 변환
    std::vector<std::string> pieces;
    auto status = sp_->Encode(text, &pieces);

    if (!status.ok()) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "SentencePiece encode failed: %s", status.ToString().c_str());
        return {};
    }

    // 2. vocab.json을 사용하여 pieces → model IDs 변환
    std::vector<int64_t> result;
    result.reserve(pieces.size() + (addEos ? 1 : 0));

    for (const std::string& piece : pieces) {
        auto it = vocab_.find(piece);
        if (it != vocab_.end()) {
            result.push_back(it->second);
        } else {
            // vocab에 없는 piece는 <unk>로 처리
            result.push_back(unkTokenId_);
        }
    }

    if (addEos) {
        result.push_back(eosTokenId_);
    }

    return result;
}

std::string Tokenizer::decode(const std::vector<int64_t>& tokenIds) {
    // 1. 토큰 ID → piece 문자열 변환 (reverseVocab 사용)
    std::vector<std::string> pieces;
    pieces.reserve(tokenIds.size());

    for (int64_t id : tokenIds) {
        // 특수 토큰 스킵
        if (id == padTokenId_ || id == eosTokenId_ || id == bosTokenId_) {
            continue;
        }

        // 언어 토큰 스킵 (__xx__ 형태)
        auto it = reverseVocab_.find(id);
        if (it != reverseVocab_.end()) {
            const std::string& piece = it->second;
            if (piece.size() > 4 && piece.substr(0, 2) == "__" && piece.substr(piece.size() - 2) == "__") {
                continue;
            }
            pieces.push_back(piece);
        } else {
            AIDEO_LOGE(LOG_TAG_TOKENIZER, "Token %lld not found in reverseVocab!", (long long)id);
        }
    }

    // 2. SentencePiece를 사용하여 pieces → 텍스트 변환
    std::string result;
    auto status = sp_->Decode(pieces, &result);

    if (!status.ok()) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "SentencePiece decode failed: %s", status.ToString().c_str());
        return "";
    }

    return result;
}
