#ifndef TOKENIZER_H
#define TOKENIZER_H
#include <string>
#include <vector>
#include <unordered_map>
#include <memory>
#include "logging.h"
#include "sentencepiece_processor.h"

#define LOG_TAG_TOKENIZER "Tokenizer"

class Tokenizer {
public:
    Tokenizer();
    ~Tokenizer();

    // SentencePiece 모델 + vocab.json 로드
    // spModelPath: sentencepiece.bpe.model
    // vocabPath: vocab.json (piece -> model ID 매핑)
    bool loadM2M100(const std::string& spModelPath, const std::string& vocabPath);

    // 텍스트 → 토큰 IDs
    std::vector<int64_t> encode(const std::string& text, bool addEos = true);

    // 토큰 IDs → 텍스트
    std::string decode(const std::vector<int64_t>& tokenIds);

    // 특수 토큰 ID
    int64_t getPadTokenId() const { return padTokenId_; }
    int64_t getEosTokenId() const { return eosTokenId_; }
    int64_t getUnkTokenId() const { return unkTokenId_; }
    int64_t getBosTokenId() const { return bosTokenId_; }

    // 언어 토큰 ID 조회
    int64_t getLanguageTokenId(const std::string& langCode) const;

    // Vocab 크기 반환
    size_t getVocabSize() const;

private:
    // SentencePiece processor (텍스트 → pieces 변환용)
    std::unique_ptr<sentencepiece::SentencePieceProcessor> sp_;

    // vocab.json: piece string → model ID
    std::unordered_map<std::string, int64_t> vocab_;
    // reverse vocab: model ID → piece string
    std::unordered_map<int64_t, std::string> reverseVocab_;

    // 특수 토큰 IDs
    int64_t bosTokenId_ = 0;
    int64_t padTokenId_ = 1;
    int64_t eosTokenId_ = 2;
    int64_t unkTokenId_ = 3;
};
#endif
