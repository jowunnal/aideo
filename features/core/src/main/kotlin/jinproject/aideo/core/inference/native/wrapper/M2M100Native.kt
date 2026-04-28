package jinproject.aideo.core.inference.native.wrapper

import java.nio.ByteBuffer

class M2M100Native {
    external fun initialize(): Boolean
    external fun loadModel(
        encoderPath: String,
        decoderPath: String,
        decoderWithPastPath: String,
        spModelPath: String,
        vocabPath: String,
        tokenizerConfigPath: String
    ): Boolean

    external fun translateWithBuffer(
        textBuffer: ByteBuffer,
        textLength: Int,
        srcLang: String,
        tgtLang: String,
        maxLength: Int
    ): String?

    external fun release()

    companion object {
        init {
            System.loadLibrary("onnx-inference")
        }
    }
}
