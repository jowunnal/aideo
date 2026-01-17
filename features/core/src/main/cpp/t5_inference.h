#ifndef T5_INFERENCE_H
#define T5_INFERENCE_H
#include <string>
#include <vector>
#include <memory>
#include "onnxruntime_inference.h"
#include "tokenizer.h"
class T5Inference {
public:
    T5Inference();
    ~T5Inference();

    // 모델 및 토크나이저 초기화
    bool loadModel(const std::string& modelPath);
    bool loadTokenizer(const std::string& tokenizerPath);

    // 메인 로직: Text Input -> Text Output
    std::string generateText(const std::string& inputText, int maxLength = 128);

    void release();
private:
    // 엔진 및 컴포넌트 (Pimpl idiom 처럼 사용)
    std::unique_ptr<OnnxInference> engine_;
    std::unique_ptr<Tokenizer> tokenizer_;

    // 내부 헬퍼 함수
    int64_t argmax(const std::vector<float>& logits, size_t vocabSize);
};
#endif