#include <jni.h>
#include <string>
#include "m2m100_translator.h"

static M2M100Translator* g_translator = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_inference_native_wrapper_M2M100Native_initialize(
        JNIEnv* env,
        jobject /* this */) {
    if (g_translator != nullptr) {
        return JNI_TRUE;
    }
    g_translator = new M2M100Translator();
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_inference_native_wrapper_M2M100Native_loadModel(
        JNIEnv* env,
        jobject /* this */,
        jstring encoderPath,
        jstring decoderPath,
        jstring decoderWithPastPath,
        jstring spModelPath,
        jstring vocabPath,
        jstring tokenizerConfigPath) {

    if (g_translator == nullptr) {
        return JNI_FALSE;
    }

    const char* encoder = env->GetStringUTFChars(encoderPath, nullptr);
    const char* decoder = env->GetStringUTFChars(decoderPath, nullptr);
    const char* decoderWithPast = env->GetStringUTFChars(decoderWithPastPath, nullptr);
    const char* spModel = env->GetStringUTFChars(spModelPath, nullptr);
    const char* vocab = env->GetStringUTFChars(vocabPath, nullptr);
    const char* tokenizerConfig = env->GetStringUTFChars(tokenizerConfigPath, nullptr);

    bool result = g_translator->load(encoder, decoder, decoderWithPast, spModel, vocab, tokenizerConfig);

    env->ReleaseStringUTFChars(encoderPath, encoder);
    env->ReleaseStringUTFChars(decoderPath, decoder);
    env->ReleaseStringUTFChars(decoderWithPastPath, decoderWithPast);
    env->ReleaseStringUTFChars(spModelPath, spModel);
    env->ReleaseStringUTFChars(vocabPath, vocab);
    env->ReleaseStringUTFChars(tokenizerConfigPath, tokenizerConfig);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_jinproject_aideo_core_inference_native_wrapper_M2M100Native_translateWithBuffer(
        JNIEnv* env,
        jobject /* this */,
        jobject textBuffer,
        jint textLength,
        jstring srcLang,
        jstring tgtLang,
        jint maxLength) {

    if (g_translator == nullptr) {
        return nullptr;
    }

    const char* textStr = static_cast<const char*>(env->GetDirectBufferAddress(textBuffer));
    if (textStr == nullptr) {
        return nullptr;
    }

    std::string text(textStr, textLength);

    const char* srcLangStr = env->GetStringUTFChars(srcLang, nullptr);
    const char* tgtLangStr = env->GetStringUTFChars(tgtLang, nullptr);

    std::string result = g_translator->translate(text, srcLangStr, tgtLangStr, maxLength);

    env->ReleaseStringUTFChars(srcLang, srcLangStr);
    env->ReleaseStringUTFChars(tgtLang, tgtLangStr);

    if (result.empty()) {
        return nullptr;
    }

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jobjectArray JNICALL
Java_jinproject_aideo_core_inference_native_wrapper_M2M100Native_translateBatch(
        JNIEnv* env,
        jobject /* this */,
        jobject textBuffer,
        jstring srcLang,
        jstring tgtLang,
        jint maxLength) {

    if (g_translator == nullptr) {
        return nullptr;
    }

    // DirectByteBuffer에서 포인터 획득 (zero-copy)
    const auto* bufPtr = static_cast<const uint8_t*>(env->GetDirectBufferAddress(textBuffer));
    if (bufPtr == nullptr) {
        return nullptr;
    }

    // 언어 토큰 1회 조회
    const char* srcLangStr = env->GetStringUTFChars(srcLang, nullptr);
    const char* tgtLangStr = env->GetStringUTFChars(tgtLang, nullptr);

    int64_t srcLangId = g_translator->getLanguageTokenId(srcLangStr);
    int64_t tgtLangId = g_translator->getLanguageTokenId(tgtLangStr);

    env->ReleaseStringUTFChars(srcLang, srcLangStr);
    env->ReleaseStringUTFChars(tgtLang, tgtLangStr);

    if (srcLangId == -1 || tgtLangId == -1) {
        return nullptr;
    }

    // Length-Prefixed 포맷 파싱: [textCount(4)][len1(4)][text1(len1)][len2(4)][text2(len2)]...
    int32_t textCount = (bufPtr[0] << 24) | (bufPtr[1] << 16)
                      | (bufPtr[2] << 8) | bufPtr[3];
    size_t offset = 4;

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(textCount, stringClass, nullptr);

    // buffer에서 하나씩 파싱하며 바로 번역 (중간 vector 없이)
    for (int i = 0; i < textCount; ++i) {
        int32_t len = (bufPtr[offset] << 24) | (bufPtr[offset + 1] << 16)
                    | (bufPtr[offset + 2] << 8) | bufPtr[offset + 3];
        offset += 4;

        std::string text(reinterpret_cast<const char*>(bufPtr + offset), len);
        offset += len;

        std::string translated = g_translator->translateSingle(text, srcLangId, tgtLangId, maxLength);
        if (!translated.empty()) {
            jstring resultStr = env->NewStringUTF(translated.c_str());
            env->SetObjectArrayElement(resultArray, static_cast<jsize>(i), resultStr);
            env->DeleteLocalRef(resultStr);
        }
    }

    env->DeleteLocalRef(stringClass);
    return resultArray;
}

JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_inference_native_wrapper_M2M100Native_isLanguageSupported(
        JNIEnv* env,
        jobject /* this */,
        jstring lang) {

    if (g_translator == nullptr) {
        return JNI_FALSE;
    }

    const char* langStr = env->GetStringUTFChars(lang, nullptr);
    bool result = g_translator->isLanguageSupported(langStr);
    env->ReleaseStringUTFChars(lang, langStr);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_jinproject_aideo_core_inference_native_wrapper_M2M100Native_release(
        JNIEnv* env,
        jobject /* this */) {
    if (g_translator != nullptr) {
        g_translator->release();
        delete g_translator;
        g_translator = nullptr;
    }
}

} // extern "C"