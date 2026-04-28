#ifndef AIDEO_ONNXRUNTIME_INFERENCE_H
#define AIDEO_ONNXRUNTIME_INFERENCE_H

#include <memory>
#include <string>
#include <unordered_map>
#include "logging.h"
#include "onnxruntime_cxx_api.h"

#define LOG_TAG_ONNX "ONNX_Native"

class OnnxInference {
public:
    OnnxInference();

    ~OnnxInference();

    bool loadSession(
            const std::string& sessionKey,
            const char* modelPath,
            const char* modelName
    );

    bool hasSession(const std::string& sessionKey) const;

    Ort::Session* getSession(const std::string& sessionKey, const char* modelName);

    void release();

private:
    std::unique_ptr<Ort::Session> createSession(
            const char* modelPath,
            const char* modelName
    );

    Ort::Env env_;
    Ort::SessionOptions sessionOptions_;
    std::unordered_map<std::string, std::unique_ptr<Ort::Session>> sessions_;
};

#endif
