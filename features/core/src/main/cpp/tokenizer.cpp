#include "tokenizer.h"
#include "json.hpp"
#include "path_utils.h"
#include <fstream>
#include <utility>

using json = nlohmann::json;

Tokenizer::Tokenizer() : sp_(std::make_unique<sentencepiece::SentencePieceProcessor>()) {}

Tokenizer::~Tokenizer() = default;

bool Tokenizer::load(const char* spModelPath, const char* vocabPath) {
    if (!loadSentencePiece(spModelPath)) {
        return false;
    }
    return loadVocab(vocabPath);
}

bool Tokenizer::loadSentencePiece(const char* spModelPath) {
    if (aideo::isInvalidPath(spModelPath)) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "Invalid SentencePiece model path");
        return false;
    }

    std::string nextPath(spModelPath);
    if (sp_ && loadedSpModelPath_ == nextPath) {
        return true;
    }

    auto nextProcessor = std::make_unique<sentencepiece::SentencePieceProcessor>();
    auto status = nextProcessor->Load(spModelPath);
    if (!status.ok()) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "Failed to load SentencePiece model: %s",
                   status.ToString().c_str());
        return false;
    }

    sp_ = std::move(nextProcessor);
    loadedSpModelPath_ = std::move(nextPath);
    return true;
}

bool Tokenizer::loadVocab(const char* vocabPath) {
    if (aideo::isInvalidPath(vocabPath)) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "Invalid vocab path");
        return false;
    }

    std::string nextPath(vocabPath);
    if (!vocab_.empty() && loadedVocabPath_ == nextPath) {
        return true;
    }

    try {
        std::ifstream vocabFile(vocabPath);
        if (!vocabFile.is_open()) {
            AIDEO_LOGE(LOG_TAG_TOKENIZER, "Failed to open vocab.json: %s", vocabPath);
            return false;
        }

        json vocabJson = json::parse(vocabFile);

        std::unordered_map<std::string, int64_t> nextVocab;
        std::unordered_map<int64_t, std::string> nextReverseVocab;
        int64_t nextBosTokenId = 0;
        int64_t nextPadTokenId = 1;
        int64_t nextEosTokenId = 2;
        int64_t nextUnkTokenId = 3;

        for (auto& [piece, id]: vocabJson.items()) {
            int64_t tokenId = id.get<int64_t>();
            nextVocab[piece] = tokenId;
            nextReverseVocab[tokenId] = piece;

            // 특수 토큰 ID 설정
            if (piece == "<s>") nextBosTokenId = tokenId;
            else if (piece == "<pad>") nextPadTokenId = tokenId;
            else if (piece == "</s>") nextEosTokenId = tokenId;
            else if (piece == "<unk>") nextUnkTokenId = tokenId;
        }

        vocab_ = std::move(nextVocab);
        reverseVocab_ = std::move(nextReverseVocab);
        bosTokenId_ = nextBosTokenId;
        padTokenId_ = nextPadTokenId;
        eosTokenId_ = nextEosTokenId;
        unkTokenId_ = nextUnkTokenId;
        loadedVocabPath_ = std::move(nextPath);
        return true;

    } catch (const std::exception& e) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "Failed to load tokenizer: %s", e.what());
        return false;
    }
}

void Tokenizer::release() {
    sp_.reset();
    vocab_.clear();
    reverseVocab_.clear();
    bosTokenId_ = 0;
    padTokenId_ = 1;
    eosTokenId_ = 2;
    unkTokenId_ = 3;
    loadedSpModelPath_.clear();
    loadedVocabPath_.clear();
}

std::vector<int64_t> Tokenizer::encode(const std::string& text) {
    if (!sp_) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "SentencePiece model not loaded");
        return {};
    }

    // 1. SentencePiece로 텍스트를 pieces로 변환
    std::vector<std::string> pieces;
    auto status = sp_->Encode(text, &pieces);

    if (!status.ok()) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "SentencePiece encode failed: %s", status.ToString().c_str());
        return {};
    }

    // 2. vocab.json을 사용하여 pieces → model IDs 변환
    std::vector<int64_t> result;
    result.reserve(pieces.size());

    for (const std::string& piece: pieces) {
        auto it = vocab_.find(piece);
        if (it != vocab_.end()) {
            result.push_back(it->second);
        } else {
            // vocab에 없는 piece는 <unk>로 처리
            result.push_back(unkTokenId_);
        }
    }

    return result;
}

std::string Tokenizer::decode(const std::vector<int64_t>& tokenIds) {
    if (!sp_) {
        AIDEO_LOGE(LOG_TAG_TOKENIZER, "SentencePiece model not loaded");
        return "";
    }

    // 1. 토큰 ID → piece 문자열 변환 (reverseVocab 사용)
    std::vector<std::string> pieces;
    pieces.reserve(tokenIds.size());

    for (int64_t id: tokenIds) {
        // 특수 토큰 스킵
        if (id == padTokenId_ || id == eosTokenId_ || id == bosTokenId_) {
            continue;
        }

        // 언어 토큰 스킵 (__xx__ 형태)
        auto it = reverseVocab_.find(id);
        if (it != reverseVocab_.end()) {
            const std::string& piece = it->second;
            if (piece.size() > 4 && piece.substr(0, 2) == "__" &&
                piece.substr(piece.size() - 2) == "__") {
                continue;
            }
            pieces.push_back(piece);
        } else {
            AIDEO_LOGE(LOG_TAG_TOKENIZER, "Token %lld not found in reverseVocab!", (long long) id);
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
