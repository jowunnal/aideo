#ifndef AIDEO_TRANSLATOR_H
#define AIDEO_TRANSLATOR_H

#include <string>
#include "logging.h"

#define LOG_TAG_TRANSLATOR "Translator"

// Translation Category 추상화
class Translator {
public:
    virtual ~Translator();

    /**
     * 주어진 입력 [text] 를 [tgtLang] 로 번역
     * @param text
     * @param srcLang
     * @param tgtLang
     * @param maxLength
     * @return
     */
    virtual std::string translate(
            const std::string& text,
            const std::string& srcLang,
            const std::string& tgtLang,
            int maxLength = 256
    ) = 0;

    virtual void release();

    bool isLoaded() const { return isLoaded_; }

    // load(...) 는 모델별로 시그니처가 다르므로 base 에 두지 않음.
    //   각 구체 클래스가 자신의 시그니처로 public load 를 가짐.

protected:
    void setLoaded(bool v) { isLoaded_ = v; }

    bool isLoaded_ = false;
};

#endif
