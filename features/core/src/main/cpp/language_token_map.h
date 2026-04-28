#ifndef AIDEO_LANGUAGE_TOKEN_MAP_H
#define AIDEO_LANGUAGE_TOKEN_MAP_H

#include <cstdint>
#include <string>
#include <unordered_map>

// 언어 코드 문자열을 모델 내부 언어 토큰 ID 로 매핑
class LanguageTokenMap {
public:
    void clear();

    void registerToken(const std::string& lang, int64_t tokenId);

    bool empty() const;

    /**
     * @param lang
     * @return tokenId or -1 (존재하지 않는 경우)
     */
    int64_t getTokenId(const std::string& lang) const;

    bool resolvePair(
            const std::string& srcLang,
            const std::string& tgtLang,
            int64_t& srcLangId,
            int64_t& tgtLangId
    ) const;

private:
    std::unordered_map<std::string, int64_t> langToTokenId_;
};

#endif
