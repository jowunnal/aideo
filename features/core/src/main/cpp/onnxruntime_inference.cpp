#include "onnxruntime_inference.h"
#include "path_utils.h"
#include <algorithm>
#include <thread>
#include <utility>

OnnxInference::OnnxInference()
        : env_(ORT_LOGGING_LEVEL_WARNING, "OnnxInference") {
    // 모바일 최적화: 메모리 Arena 비활성화로 버벅임 해결됨
    // 스레드 수는 성능을 위해 적정 수준 유지 (코어의 절반, 2~4개)
    unsigned int totalCores = std::thread::hardware_concurrency();
    unsigned int numThreads = std::min(4u, std::max(2u, totalCores / 2));
    if (totalCores == 0) numThreads = 2;  // 감지 실패 시 기본값

    // 세션 옵션 설정
    // TODO : JVM 스레드 수와 연동되나? 만약 그렇다면, 스레드 수가 너무 많은 건 좋지 않음.
    sessionOptions_.SetIntraOpNumThreads(
            static_cast<int>(numThreads)); // 하나의 샘플에 대해 병렬 연산할 수 있을 때, 몇개의 스레드로 실행할 것 인지
    sessionOptions_.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

    // 메모리 최적화: Arena allocator 비활성화
    // Arena는 메모리를 미리 할당하고 해제를 지연시켜 메모리 사용량 증가
    // 비활성화하면 즉시 해제되어 메모리 사용량 감소
    sessionOptions_.DisableCpuMemArena();

    // 메모리 패턴 최적화 활성화
    // 실행 중 메모리 할당 패턴을 분석하여 재사용
    sessionOptions_.EnableMemPattern();
}

OnnxInference::~OnnxInference() = default;

bool OnnxInference::loadSession(
        const std::string& sessionKey,
        const char* modelPath,
        const char* modelName) {
    if (sessionKey.empty()) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Invalid session key for %s", modelName);
        return false;
    }

    auto session = createSession(modelPath, modelName);
    if (!session) {
        return false;
    }

    sessions_[sessionKey] = std::move(session);
    return true;
}

bool OnnxInference::hasSession(const std::string& sessionKey) const {
    auto session = sessions_.find(sessionKey);
    return session != sessions_.end() && session->second != nullptr;
}

Ort::Session* OnnxInference::getSession(const std::string& sessionKey, const char* modelName) {
    auto session = sessions_.find(sessionKey);
    if (session == sessions_.end() || !session->second) {
        AIDEO_LOGE(LOG_TAG_ONNX, "%s session not loaded", modelName);
        return nullptr;
    }
    return session->second.get();
}

void OnnxInference::release() {
    sessions_.clear();
}

std::unique_ptr<Ort::Session> OnnxInference::createSession(
        const char* modelPath,
        const char* modelName) {
    if (aideo::isInvalidPath(modelPath)) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Invalid %s path", modelName);
        return nullptr;
    }

    try {
        return std::make_unique<Ort::Session>(env_, modelPath, sessionOptions_);
    } catch (const Ort::Exception& e) {
        AIDEO_LOGE(LOG_TAG_ONNX, "Failed to load %s: %s", modelName, e.what());
        return nullptr;
    }
}
