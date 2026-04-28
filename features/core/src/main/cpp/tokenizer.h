#ifndef TOKENIZER_H
#define TOKENIZER_H

#include <string>
#include <vector>
#include <unordered_map>
#include <memory>
#include "logging.h"
#include "sentencepiece_processor.h"

#define LOG_TAG_TOKENIZER "Tokenizer"

class Tokenizer { //TODO : sentencePiece 에 너무 의존적인 선언 및 정의가 포함됨.
public:
    Tokenizer();

    ~Tokenizer();

    /**
     * SentencePiece 모델, vocab table 로드
     *
     * @param spModelPath : sentencePiece 모델 파일 경로
     * @param vocabPath : vocab 파일 경로
     * @return
     */
    bool load(const char* spModelPath, const char* vocabPath);

    void release();

    // 텍스트 → 토큰 IDs
    std::vector<int64_t> encode(const std::string& text);

    // 토큰 IDs → 텍스트
    std::string decode(const std::vector<int64_t>& tokenIds);

    // 특수 토큰 ID
    int64_t getPadTokenId() const { return padTokenId_; }

    int64_t getEosTokenId() const { return eosTokenId_; }

    int64_t getUnkTokenId() const { return unkTokenId_; }

private:
    bool loadSentencePiece(const char* spModelPath);

    bool loadVocab(const char* vocabPath);

    // SentencePiece processor (텍스트 → pieces 변환용)
    std::unique_ptr<sentencepiece::SentencePieceProcessor> sp_;

    //TODO : 메모리 사용량이 너무 많지 않을까? 이걸 꼭 유지 해야만 하나?
    // vocab 내의 [Token ID: piece string]
    std::unordered_map<std::string, int64_t> vocab_;
    // vocab 내의 [piece string : Token ID]
    std::unordered_map<int64_t, std::string> reverseVocab_;

    // 특수 토큰 IDs
    int64_t bosTokenId_ = 0;
    int64_t padTokenId_ = 1;
    int64_t eosTokenId_ = 2;
    int64_t unkTokenId_ = 3;
    std::string loadedSpModelPath_;
    std::string loadedVocabPath_;
};

#endif
