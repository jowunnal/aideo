#include "language_token_map.h"

void LanguageTokenMap::clear() {
    langToTokenId_.clear();
}

void LanguageTokenMap::registerToken(
        const std::string& lang,
        int64_t tokenId
) {
    langToTokenId_[lang] = tokenId;
}

bool LanguageTokenMap::empty() const {
    return langToTokenId_.empty();
}

int64_t LanguageTokenMap::getTokenId(const std::string& lang) const {
    auto it = langToTokenId_.find(lang);
    if (it != langToTokenId_.end()) {
        return it->second;
    }

    return -1;
}

bool LanguageTokenMap::resolvePair(
        const std::string& srcLang,
        const std::string& tgtLang,
        int64_t& srcLangId,
        int64_t& tgtLangId
) const {
    srcLangId = getTokenId(srcLang);
    tgtLangId = getTokenId(tgtLang);

    return srcLangId != -1 && tgtLangId != -1;
}
