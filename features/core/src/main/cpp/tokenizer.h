#ifndef TOKENIZER_H
#define TOKENIZER_H
#include <string>
#include <vector>
#include <unordered_map>
#include "logging.h"

#define LOG_TAG_TOKENIZER "Tokenizer"

class Tokenizer {
public:
    Tokenizer();
    ~Tokenizer() = default;

    // tokenizer.json 파일 로드
    bool load(const std::string& tokenizerPath);

    // 텍스트 → 토큰 IDs
    std::vector<int64_t> encode(const std::string& text, bool addEos = true);

    // 토큰 IDs → 텍스트
    std::string decode(const std::vector<int64_t>& tokenIds);

    // 특수 토큰 ID
    int64_t getPadTokenId() const { return padTokenId_; }
    int64_t getEosTokenId() const { return eosTokenId_; }
    int64_t getUnkTokenId() const { return unkTokenId_; }

    // Vocab 크기 반환
    size_t getVocabSize() const { return vocab_.size() + addedTokens_.size(); }

private:
    // Viterbi 알고리즘용 최적 경로 노드
    struct BestPathNode {
        int64_t id;
        float score;
        size_t starts_at;
    };

    // Vocab: token -> (id, score)
    std::unordered_map<std::string, std::pair<int64_t, float>> vocab_;

    // Reverse vocab: id -> token
    std::unordered_map<int64_t, std::string> idToToken_;

    // Added tokens (special tokens)
    std::unordered_map<std::string, int64_t> addedTokens_;

    // 특수 토큰 IDs
    int64_t padTokenId_ = 0;
    int64_t eosTokenId_ = 1;
    int64_t unkTokenId_ = 2;

    // Unigram encoding (Viterbi)
    std::vector<int64_t> encodeUnigram(const std::string& text);

    // SentencePiece 전처리: 공백을 ▁로 변환
    std::string preprocess(const std::string& text);

    // 후처리: ▁를 공백으로 변환
    std::string postprocess(const std::string& text);
};
#endif
