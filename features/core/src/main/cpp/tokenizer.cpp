#include "tokenizer.h"
#include "json.hpp"
#include <fstream>
using json = nlohmann::json;

Tokenizer::Tokenizer() : sp_(std::make_unique<sentencepiece::SentencePieceProcessor>()) {}

Tokenizer::~Tokenizer() = default;

bool Tokenizer::loadM2M100(const std::string& spModelPath, const std::string& vocabPath) {
    try {
        // 1. SentencePiece 모델 로드 (텍스트 → pieces 변환용)
        AIDEO_LOGI(LOG_TAG_TOKENIZER, "Loading SentencePiece model from: %s", spModelPath.c_str());

        auto status = sp_->Load(spModelPath);
        if (!status.ok()) {
            AIDEO_LOGE(LOG_TAG_TOKENIZER, "Failed to load SentencePiece model: %s", status.ToString().c_str());
            return false;
        }

        AIDEO_LOGI(LOG_TAG_TOKENIZER, "SentencePiece model loaded, internal vocab size: %d", sp_->GetPieceSize());

        // 2. vocab.json 로드 (piece string → model ID 매핑)
        AIDEO_LOGI(LOG_TAG_TOKENIZER, "Loading vocab from: %s", vocabPath.c_str());
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

        AIDEO_LOGI(LOG_TAG_TOKENIZER, "Vocab loaded: %zu entries", vocab_.size());
        AIDEO_LOGI(LOG_TAG_TOKENIZER, "Special tokens - BOS: %lld, PAD: %lld, EOS: %lld, UNK: %lld",
                   (long long)bosTokenId_, (long long)padTokenId_, (long long)eosTokenId_, (long long)unkTokenId_);

        return true;

    } catch (const std::exception& e) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "Failed to load M2M100 tokenizer: %s", e.what());
        return false;
    }
}

std::vector<int64_t> Tokenizer::encode(const std::string& text, bool addEos) {
    AIDEO_LOGI(LOG_TAG_TOKENIZER, "Encoding text: %s", text.c_str());

    // 1. SentencePiece로 텍스트를 pieces로 변환
    std::vector<std::string> pieces;
    auto status = sp_->Encode(text, &pieces);

    if (!status.ok()) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "SentencePiece encode failed: %s", status.ToString().c_str());
        return {};
    }

    // 디버그: pieces 출력
    std::string piecesStr;
    for (size_t i = 0; i < std::min(pieces.size(), (size_t)10); i++) {
        piecesStr += "'" + pieces[i] + "' ";
    }
    AIDEO_LOGI(LOG_TAG_TOKENIZER, "SentencePiece pieces (first 10): %s", piecesStr.c_str());

    // 2. vocab.json을 사용하여 pieces → model IDs 변환
    std::vector<int64_t> result;
    result.reserve(pieces.size() + (addEos ? 1 : 0));

    for (const std::string& piece : pieces) {
        auto it = vocab_.find(piece);
        if (it != vocab_.end()) {
            result.push_back(it->second);
        } else {
            // vocab에 없는 piece는 <unk>로 처리
            AIDEO_LOGE(LOG_TAG_TOKENIZER, "Unknown piece '%s', using <unk>", piece.c_str());
            result.push_back(unkTokenId_);
        }
    }

    // 디버그: 변환된 IDs 출력
    std::string idsStr;
    for (size_t i = 0; i < std::min(result.size(), (size_t)10); i++) {
        idsStr += std::to_string(result[i]) + " ";
    }
    AIDEO_LOGI(LOG_TAG_TOKENIZER, "Model IDs (first 10): %s", idsStr.c_str());

    if (addEos) {
        result.push_back(eosTokenId_);
    }

    AIDEO_LOGI(LOG_TAG_TOKENIZER, "Final token count: %zu (addEos=%d)", result.size(), addEos);
    return result;
}

std::string Tokenizer::decode(const std::vector<int64_t>& tokenIds) {
    // 디버그: 입력 토큰 출력
    std::string debugTokens;
    for (size_t i = 0; i < std::min(tokenIds.size(), (size_t)20); i++) {
        debugTokens += std::to_string(tokenIds[i]) + " ";
    }
    AIDEO_LOGI(LOG_TAG_TOKENIZER, "Decode input tokens (first 20): %s", debugTokens.c_str());

    // 1. 토큰 ID → piece 문자열 변환 (reverseVocab 사용)
    std::vector<std::string> pieces;
    pieces.reserve(tokenIds.size());

    for (int64_t id : tokenIds) {
        // 특수 토큰 스킵
        if (id == padTokenId_ || id == eosTokenId_ || id == bosTokenId_) {
            AIDEO_LOGI(LOG_TAG_TOKENIZER, "Skipping special token id: %lld", (long long)id);
            continue;
        }

        // 언어 토큰 스킵 (__xx__ 형태)
        auto it = reverseVocab_.find(id);
        if (it != reverseVocab_.end()) {
            const std::string& piece = it->second;
            if (piece.size() > 4 && piece.substr(0, 2) == "__" && piece.substr(piece.size() - 2) == "__") {
                AIDEO_LOGI(LOG_TAG_TOKENIZER, "Skipping language token id: %lld (%s)", (long long)id, piece.c_str());
                continue;
            }
            pieces.push_back(piece);
            AIDEO_LOGI(LOG_TAG_TOKENIZER, "Token %lld -> '%s'", (long long)id, piece.c_str());
        } else {
            AIDEO_LOGE(LOG_TAG_TOKENIZER, "Token %lld not found in reverseVocab!", (long long)id);
        }
    }

    AIDEO_LOGI(LOG_TAG_TOKENIZER, "Pieces count: %zu", pieces.size());

    // 2. SentencePiece를 사용하여 pieces → 텍스트 변환
    std::string result;
    auto status = sp_->Decode(pieces, &result);

    if (!status.ok()) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "SentencePiece decode failed: %s", status.ToString().c_str());
        return "";
    }

    AIDEO_LOGI(LOG_TAG_TOKENIZER, "Decoded result: %s", result.c_str());
    return result;
}

int64_t Tokenizer::getLanguageTokenId(const std::string& langCode) const {
    // langCode: "ko", "en", "ja", etc.
    std::string token = "__" + langCode + "__";
    auto it = vocab_.find(token);
    if (it != vocab_.end()) {
        return it->second;
    }
    return -1;
}

size_t Tokenizer::getVocabSize() const {
    return vocab_.size();
}
