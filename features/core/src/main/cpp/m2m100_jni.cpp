//
// Created by PC on 2026-01-18.
// JNI bindings for M2M100Translator
//

#include <jni.h>
#include <string>
#include "m2m100_translator.h"

static M2M100Translator* g_translator = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_runtime_impl_onnx_wrapper_M2M100Native_initialize(
        JNIEnv* env,
        jobject /* this */) {
    if (g_translator != nullptr) {
        return JNI_TRUE;
    }
    g_translator = new M2M100Translator();
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_runtime_impl_onnx_wrapper_M2M100Native_loadModel(
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
Java_jinproject_aideo_core_runtime_impl_onnx_wrapper_M2M100Native_translate(
        JNIEnv* env,
        jobject /* this */,
        jstring text,
        jstring srcLang,
        jstring tgtLang,
        jint maxLength) {

    if (g_translator == nullptr) {
        return nullptr;
    }

    const char* textStr = env->GetStringUTFChars(text, nullptr);
    const char* srcLangStr = env->GetStringUTFChars(srcLang, nullptr);
    const char* tgtLangStr = env->GetStringUTFChars(tgtLang, nullptr);

    std::string result = g_translator->translate(textStr, srcLangStr, tgtLangStr, maxLength);

    env->ReleaseStringUTFChars(text, textStr);
    env->ReleaseStringUTFChars(srcLang, srcLangStr);
    env->ReleaseStringUTFChars(tgtLang, tgtLangStr);

    if (result.empty()) {
        return nullptr;
    }

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_runtime_impl_onnx_wrapper_M2M100Native_isLanguageSupported(
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
Java_jinproject_aideo_core_runtime_impl_onnx_wrapper_M2M100Native_release(
        JNIEnv* env,
        jobject /* this */) {
    if (g_translator != nullptr) {
        g_translator->release();
        delete g_translator;
        g_translator = nullptr;
    }
}

} // extern "C"