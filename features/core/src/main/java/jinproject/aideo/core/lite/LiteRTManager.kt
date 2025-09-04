package jinproject.aideo.core.lite

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.audio.AudioBuffer
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.inject.Inject
import kotlin.math.min
import kotlin.text.Charsets.UTF_8

class LiteRTManager @Inject constructor(@ApplicationContext private val context: Context) {

    private lateinit var interpreter: InterpreterApi

    var isInitialized = false
        private set

    val whisperUtil = WhisperUtil()

    fun initialize(vocabPath: String) {
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
        val segments = mutableListOf<Triple<Double, Double, ByteArray>>()
        val currTextTokens = mutableListOf<Byte>()
        var currStart = 0.0
        var hasStart = false

        for (i in 0 until outputBuffer.limit()) {
            val token = outputBuffer.get(i)

            when(token) {
                in 0 until whisperUtil.tokenEOT -> currTextTokens.addAll(whisperUtil.getWordFromToken(token).toList())
                whisperUtil.tokenEOT -> break
                in whisperUtil.tokenStart..whisperUtil.tokenEnd -> {
                    val ts = TS_STEP * (token - whisperUtil.tokenStart) + lastTime
                    if (!hasStart) {
                        // 시작 타임스탬프
                        currStart = ts
                        hasStart = true
                    } else {
                        // 끝 타임스탬프
                        if (currTextTokens.isNotEmpty()) {
                            segments.add(Triple(currStart, ts, currTextTokens.toByteArray()))
                        }
                        currTextTokens.clear()
                        currStart = ts
                    }
                }
            }
        }

        // 마지막 텍스트 처리
        if (hasStart && currTextTokens.isNotEmpty()) {
            val endTs = currStart + 30
            segments.add(Triple(currStart, endTs, currTextTokens.toByteArray()))
        }

        // SRT 변환
        val srt = StringBuilder()
        for ((idx, segment) in segments.withIndex()) {
            val (start, end, text) = segment
            srt.append("${idx + 1 + lastSRTSequence}\n")
            srt.append("${formatTimestamp(start)} --> ${formatTimestamp(end)}\n")
            srt.append("${String(text, UTF_8).trim()}\n\n")
        }

        return srt.toString()
    }

    private fun getMelSpectrogram(samples: FloatArray): FloatArray {
        val cores = Runtime.getRuntime().availableProcessors()
        val meaningFul = min(samples.size, AudioBuffer.PROCESSING_CHUNK_SAMPLES)
        return whisperUtil.getMelSpectrogram(samples, samples.size, meaningFul, cores)
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
        const val MAX_TOKEN_LENGTH = 450


        /**
         * 타임 스탬프 간격: 0.02
         */
        const val TS_STEP = 0.02
    }
}