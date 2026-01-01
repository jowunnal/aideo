package jinproject.aideo.core.runtime.impl.tfLite

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.whisper.VocabUtils
import jinproject.aideo.core.runtime.api.SpeechToText
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.inject.Inject

class TfLiteSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
    modelPath: String,
    language: String,
) : SpeechToText(modelPath = modelPath, language = language) {
    private lateinit var interpreter: InterpreterApi
    private lateinit var vocabUtils: VocabUtils
    override val transcribeResult: TranscribeResult =
        TfLiteTranscribeResult(tokens = null, transcription = StringBuilder())

    override fun initialize(vocabPath: String) {
        val isVocabLoaded = vocabUtils.loadFiltersAndVocab(vocabPath)

        val interpreterOption =
            Interpreter.Options()
                .setNumThreads(Runtime.getRuntime().availableProcessors())
                .setCancellable(true)
                .setUseXNNPACK(false)

        interpreter = Interpreter(
            FileUtil.loadMappedFile(context, modelPath),
            interpreterOption
        )

        isInitialized = isVocabLoaded
    }

    override suspend fun transcribeByModel(audioData: FloatArray) {
        Log.d("test", "getMelSpectrogram")
        val melSpectrogram = vocabUtils.calMelSpectrogram(audioData)
        Log.d("test", "melSpectrogram result: ${melSpectrogram.size}")

        val signatureKey = "serving_default"
        val inputKeys = interpreter.getSignatureInputs(signatureKey)
        val inputTensor = interpreter.getInputTensorFromSignature(inputKeys[0], signatureKey)
        val inputShape = inputTensor.shape()
        Log.d(
            "test",
            "inputKeys: ${inputKeys.contentToString()} inputShape: ${inputShape.contentToString()}, inputType: ${inputTensor.dataType()}"
        )

        val inputSize = inputShape[0] * inputShape[1] * inputShape[2] * Float.SIZE_BYTES
        val audioBuffer = ByteBuffer.allocateDirect(inputSize).apply {
            order(ByteOrder.nativeOrder())
            for (v in melSpectrogram) {
                putFloat(v)
            }
        }

        /*val langTensor = interpreter.getInputTensorFromSignature(inputKeys[1],signatureKey)
        val langShape = langTensor.shape()
        Log.d("test","langShape: ${langShape.contentToString()}, langType: ${langTensor.dataType()}")
        val languageTokenBuffer = IntBuffer.allocate(langShape[0]).apply {
            put(languageToken)
        }*/

        val inputs = mapOf<String, Any>(
            inputKeys[0] to audioBuffer,
        )

        val outputKeys = interpreter.getSignatureOutputs(signatureKey)
        val outputTensor = interpreter.getOutputTensorFromSignature(outputKeys[0], signatureKey)
        val outputShape = outputTensor.shape()
        Log.d(
            "test",
            "outputKeys: ${outputKeys.contentToString()}  outputShape: ${outputShape.contentToString()}, outputType: ${outputTensor.dataType()}"
        )
        val outputBuffer = FloatBuffer.allocate(outputShape[1])

        val outputs = mapOf<String, Any>(
            outputKeys[0] to outputBuffer
        )
        Log.d("test", "추론 시작")
        runCatching {
            interpreter.runSignature(inputs, outputs, signatureKey)
        }.onFailure {
            Log.d("test", "추론 과정 에러 발생: ${it.message}")
        }
        Log.d(
            "test", "추론 끝: ${
                outputBuffer.run {
                    StringBuilder().apply {
                        repeat(this@run.capacity()) { idx ->
                            append("${this@run.get(idx)} ")
                        }
                    }.toString()
                }
            }"
        )

        (transcribeResult as TfLiteTranscribeResult).apply {
            tokens = FloatArray(outputBuffer.capacity()).apply arr@ {
                outputBuffer.get(this@arr)
            }
        }
    }

    override fun deInitialize() {
        if (::interpreter.isInitialized) {
            interpreter.close()
        }
    }

    data class TfLiteTranscribeResult(
        var tokens: FloatArray?,
        override val transcription: StringBuilder
    ) : TranscribeResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TfLiteTranscribeResult

            if (!tokens.contentEquals(other.tokens)) return false

            return true
        }

        override fun hashCode(): Int {
            return tokens.contentHashCode()
        }
    }
}