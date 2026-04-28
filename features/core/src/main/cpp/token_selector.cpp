#include "token_selector.h"
#include "logging.h"

#define LOG_TAG_TOKEN_SELECTOR "TokenSelector"

//TODO: logits 는 vocab table 전체에 대해 현재의 decode 결과가 얼마나 적합한가를 점수(확률)로 나타낸 값들. 즉, vocab 크기가 크면 연산량이 많아짐.
// 그런데, GreedyTokenSelector::select() 함수 구현을 보면 greedy 하게 탐색하고 있음. 가장 높은 점수를 가진 토큰을 가져옴. 단순하고 빠르지만, 정확도에 의문점이 있음.
// 현재 추론의 정확도가 떨어지는 상황이기 때문에 개선의 여지가 있어 보임.
int64_t GreedyTokenSelector::select(
        const std::vector<float>& logits,
        int64_t vocabSize,
        int64_t fallbackTokenId
) const {
    // Greedy decoding: 가장 높은 확률의 토큰 선택
    // logits shape: [batch_size, decoder의 입력 seq_len, vocab_size]

    if (logits.empty()) {
        return fallbackTokenId;
    }

    size_t logitsSize = logits.size();
    if (vocabSize <= 0) {
        AIDEO_LOGE(LOG_TAG_TOKEN_SELECTOR, "Invalid vocab size: %lld", (long long) vocabSize);
        return fallbackTokenId;
    }

    auto vocabSizeU = static_cast<size_t>(vocabSize);
    if (logitsSize % vocabSizeU != 0) {
        AIDEO_LOGE(LOG_TAG_TOKEN_SELECTOR,
                   "Logits size is not divisible by vocab size: %zu %% %zu",
                   logitsSize, vocabSizeU);
        return fallbackTokenId;
    }

    // decoder, decoder_with_past 의 마지막 입력 토큰의 vocab 만 사용(decoder 의 inputIds 에서 eos 는 무시)
    size_t lastTokenOffset = logitsSize - vocabSizeU;

    int64_t maxIdx = 0;
    float maxVal = logits[lastTokenOffset];

    for (size_t i = 1; i < vocabSizeU; ++i) {
        if (logits[lastTokenOffset + i] > maxVal) {
            maxVal = logits[lastTokenOffset + i];
            maxIdx = static_cast<int64_t>(i);
        }
    }

    return maxIdx;
}
