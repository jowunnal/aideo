package jinproject.aideo.core.lite

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.lite.LiteRT.Companion.MODEL_PATH
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.inject.Inject
import kotlin.math.sin

class LiteRT @Inject constructor(@ApplicationContext private val context: Context) {

    private lateinit var interpreter: InterpreterApi

    var isInitialized = false
        private set

    val whisperUtil = WhisperUtil()

    suspend fun initialize(vocabPath: String) {
        val isVocabLoaded = whisperUtil.loadFiltersAndVocab(true, vocabPath)

        val interpreterOption =
            Interpreter.Options()
                .setNumThreads(Runtime.getRuntime().availableProcessors())
                .setCancellable(true)
                .setUseXNNPACK(false)

        interpreter = Interpreter(
            FileUtil.loadMappedFile(context, MODEL_PATH),
            interpreterOption
        )

        isInitialized = isVocabLoaded
    }

    fun transcribeLang(
        audioData: FloatArray,
        lastTime: Float,
        lastSRTSequence: Int,
        languageCode: String,
    ): String {
        val languageToken = LanguageToken.getIdForLanguage(languageCode)
            ?: throw IllegalArgumentException("지원되지 않는 언어: $languageCode")

        Log.d("test", "getMelSpectrogram")
        val melSpectrogram = getMelSpectrogram(
            samples = audioData,
        )
        Log.d("test", "melSpectrogram result: ${melSpectrogram.size}")

        val signatureKey = "serving_transcribe"
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
            repeat(melSpectrogram.size) { idx ->
                putFloat(melSpectrogram[idx])
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
        val outputBuffer = IntBuffer.allocate(outputShape[1])

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

        return convertToSRT(
            outputBuffer = outputBuffer,
            lastTime = lastTime,
            lastSRTSequence = lastSRTSequence,
        )
    }

    private fun convertToSRT(
        outputBuffer: IntBuffer,
        lastTime: Float,
        lastSRTSequence: Int,
    ): String {
        val segments = mutableListOf<Triple<Double, Double, String>>()
        var currStart = 0.0
        var currText = StringBuilder()
        var hasStart = false

        val size = outputBuffer.capacity()
        for (i in 0 until size) {
            val token = outputBuffer.get(i)

            if (token == whisperUtil.tokenEOT) {
                break
            }

            if (token < whisperUtil.tokenEOT) {
                // 일반 텍스트 토큰
                currText.append(whisperUtil.getWordFromToken(token).toString(Charsets.UTF_8))
            } else if (token >= 50364 && token <= 51864) {
                val ts = TS_STEP * (token - 50364) + lastTime
                if (!hasStart) {
                    // 시작 타임스탬프
                    currStart = ts
                    hasStart = true
                } else {
                    // 끝 타임스탬프
                    if (currText.trim().isNotEmpty()) {
                        segments.add(Triple(currStart, ts, currText.toString()))
                    }
                    currText.clear()
                    currStart = ts
                }
            }
        }

        // 마지막 텍스트 처리
        if (hasStart && currText.trim().isNotEmpty()) {
            val endTs = currStart + 30
            segments.add(Triple(currStart, endTs, currText.toString()))
        }

        // SRT 변환
        val srt = StringBuilder()
        for ((idx, segment) in segments.withIndex()) {
            val (start, end, text) = segment
            srt.append("${idx + 1 + lastSRTSequence}\n")
            srt.append("${formatTimestamp(start)} --> ${formatTimestamp(end)}\n")
            srt.append("${text.trim()}\n\n")
        }

        return srt.toString()
    }

    private fun getMelSpectrogram(samples: FloatArray): FloatArray {
        val cores = Runtime.getRuntime().availableProcessors()
        return whisperUtil.getMelSpectrogram(samples, samples.size, samples.size, cores)
    }

    private fun formatTimestamp(t: Double): String {
        val h = (t / 3600).toInt()
        val m = ((t % 3600) / 60).toInt()
        val s = (t % 60).toInt()
        val ms = ((t - t.toInt()) * 1000).toInt()
        return String.format(
            context.resources.configuration.locales[0],
            "%02d:%02d:%02d,%03d",
            h,
            m,
            s,
            ms
        )
    }

    fun deInitialize() {
        if (::interpreter.isInitialized) {
            interpreter.close()
        }
    }

    companion object {
        const val MODEL_PATH = "models/whisper-tiny.tflite"
        const val SAMPLE_RATE = 16000
        const val CHANNELS = 1
        const val WHISPER_CHUNK_SECONDS = 30
        const val MAX_TOKEN_LENGTH = 450
        const val TS_STEP = 0.02

        const val WHISPER_CHUNK_SAMPLES = SAMPLE_RATE * WHISPER_CHUNK_SECONDS * CHANNELS
    }
}