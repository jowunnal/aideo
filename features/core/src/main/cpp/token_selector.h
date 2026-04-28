#ifndef AIDEO_TOKEN_SELECTOR_H
#define AIDEO_TOKEN_SELECTOR_H

#include <cstdint>
#include <vector>

// [logits → 다음 토큰] 변환 전략
class TokenSelector {
public:
    virtual ~TokenSelector() = default;

    /**
     * logits 로 부터 Token ID selector
     * @param logits : shape = [batch_size, decoder_seq_len, vocab_size]
     * @param vocabSize : vocab 크기
     * @param fallbackTokenId : logits 가 비었거나 비정상일 때, 반환할 토큰(default = eosTokenId)
     * @return
     */
    virtual int64_t select(
            const std::vector<float>& logits,
            int64_t vocabSize,
            int64_t fallbackTokenId
    ) const = 0;
};

class GreedyTokenSelector : public TokenSelector {
public:
    int64_t select(
            const std::vector<float>& logits,
            int64_t vocabSize,
            int64_t fallbackTokenId
    ) const override;
};

#endif
