package jinproject.aideo.core.runtime.impl.onnx.wrapper

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

    external fun translate(text: String, srcLang: String, tgtLang: String, maxLength: Int): String?
    external fun translateWithBuffer(
        textBuffer: ByteBuffer,
        textLength: Int,
        srcLang: String,
        tgtLang: String,
        maxLength: Int
    ): String?
    external fun isLanguageSupported(lang: String): Boolean
    external fun release()

    companion object {
        init {
            System.loadLibrary("onnx-inference")
        }
    }
}
