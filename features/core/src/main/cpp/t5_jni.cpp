#include <jni.h>
#include <string>
#include <memory>
#include "t5_inference.h"

// 전역 싱글톤 인스턴스 (unique_ptr 사용)
static std::unique_ptr<T5Inference> gT5 = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_runtime_impl_onnx_wrapper_T5Native_initialize(JNIEnv* env, jobject thiz) {
    if (gT5 == nullptr) {
        gT5 = std::make_unique<T5Inference>();
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_runtime_impl_onnx_wrapper_T5Native_loadModel(
        JNIEnv* env, jobject thiz, jstring modelPath) {
    if (gT5 == nullptr) return JNI_FALSE;

    const char* pathChars = env->GetStringUTFChars(modelPath, nullptr);
    std::string path(pathChars);
    env->ReleaseStringUTFChars(modelPath, pathChars);

    return gT5->loadModel(path) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_jinproject_aideo_core_runtime_impl_onnx_wrapper_T5Native_loadTokenizer(
        JNIEnv* env, jobject thiz, jstring tokenizerPath) {
    if (gT5 == nullptr) return JNI_FALSE;

    const char* pathChars = env->GetStringUTFChars(tokenizerPath, nullptr);
    std::string path(pathChars);
    env->ReleaseStringUTFChars(tokenizerPath, pathChars);

    return gT5->loadTokenizer(path) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_jinproject_aideo_core_runtime_impl_onnx_wrapper_T5Native_generateText(
        JNIEnv* env, jobject thiz, jstring inputText, jint maxLength) {
    if (gT5 == nullptr) return nullptr;

    const char* textChars = env->GetStringUTFChars(inputText, nullptr);
    std::string text(textChars);
    env->ReleaseStringUTFChars(inputText, textChars);

    // Controller 호출: Enc -> Gen -> Dec
    std::string output = gT5->generateText(text, maxLength);

    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL
Java_jinproject_aideo_core_runtime_impl_onnx_wrapper_T5Native_release(JNIEnv* env, jobject thiz) {
    gT5.reset();
}

} // extern "C"
