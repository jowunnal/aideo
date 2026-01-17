package jinproject.aideo.core.runtime.impl.onnx.wrapper

class T5Native {
    companion object {
        init {
            System.loadLibrary("onnx-inference")
        }
    }

    external fun initialize(): Boolean
    external fun loadModel(modelPath: String): Boolean
    external fun loadTokenizer(tokenizerPath: String): Boolean
    external fun generateText(inputText: String, maxLength: Int): String?
    external fun release()
}
