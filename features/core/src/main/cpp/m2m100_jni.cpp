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
    g_translator = new M2M100Translator(); //TODO isLoaded_ 값과 g_translator 가 SSOT 를 만족하지 않음
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

    bool result = g_translator->load(encoder, decoder, decoderWithPast, spModel, vocab,
                                     tokenizerConfig);

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
