package jinproject.aideo.core.lite

import android.content.Context
import android.util.Log
import dagger.Binds
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.core.audio.WhisperAudioProcessor
import jinproject.aideo.core.audio.MediaFileManager
import jinproject.aideo.core.audio.WhisperManager
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.support.common.FileUtil
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 오디오(Speech) 를 문자(Text) 로 추론(변환)의 수행을 담당
 */
abstract class SpeechToText(
    val modelPath: String,
) {
    @Inject
    lateinit var vocabUtils: VocabUtils

    protected var isInitialized = false

    abstract fun initialize(vocabPath: String)
    abstract fun deInitialize()

    open suspend fun transcribe(
        audioData: FloatArray,
        languageCode: String,
    ): FloatArray {
        require(isInitialized) {
            "ExecutorchSpeechToText is not initialized."
        }

        return transcribeByModel(
            audioData = audioData,
            languageCode = languageCode,
        )
    }

    protected abstract suspend fun transcribeByModel(
        audioData: FloatArray,
        languageCode: String,
    ): FloatArray

    companion object {
        const val MAX_TOKEN_LENGTH = 448

        /**
         * 타임 스탬프 간격: 0.02
         */
        const val TS_STEP = 0.02
    }
}

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ExecutorchWhisper

@dagger.Module
@InstallIn(SingletonComponent::class)
object SpeechToTextModule {

    @Provides
    @Singleton
    @ExecutorchWhisper
    fun providesExecutorchSpeechToText(
        @ApplicationContext context: Context,
    ): SpeechToText {
        return ExecutorchSpeechToText(context = context, modelPath = WhisperManager.MODEL_FILE_PATH)
    }
}

/**
 * Executorch 런타임을 활용하여 [.pte] 포맷의 모델의 추론을 담당
 */
class ExecutorchSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
    modelPath: String,
) : SpeechToText(modelPath = modelPath) {
    lateinit var module: Module

    private fun loadModel(modelPath: String): Module {
        return Module.load(modelPath)
    }

    override fun initialize(vocabPath: String) {
        val isVocabLoaded = vocabUtils.loadFiltersAndVocab(vocabPath)
        module = loadModel(File(context.filesDir, modelPath).absolutePath)

        isInitialized = isVocabLoaded
    }

    override fun deInitialize() {
        module.destroy()
    }

    fun decode(encoderOutputs: EValue): FloatArray {
        val maxSeqLen = module.execute("get_max_seq_len", EValue.optionalNone())[0].toInt().toInt()
        val noTimeStampTokenId = module.execute("no_timestamps_token_id")[0].toInt()
        val forcedTokens = longArrayOf(
            VocabUtils.SOT.toLong(),
            VocabUtils.TRANSCRIBE.toLong(),
            50364L
        )

        Log.d("test","maxSegLen: $maxSeqLen")

        val generatedIds = mutableListOf<Float>()

        for (i in 0 until maxSeqLen) {
            val decoderInputIds = if (i < forcedTokens.size) {
                Tensor.fromBlob(
                    longArrayOf(forcedTokens[i]),
                    longArrayOf(1, 1)
                )
            } else {
                Tensor.fromBlob(
                    longArrayOf(generatedIds.last().toLong()),
                    longArrayOf(1, 1)
                )
            }

            val cachePosition = Tensor.fromBlob(
                longArrayOf(i.toLong()),
                longArrayOf(1)
            )

            val logits = module.execute(
                "text_decoder",
                EValue.from(decoderInputIds),
                encoderOutputs,
                EValue.from(cachePosition)
            )[0].toTensor()

            val nextToken = if (i < forcedTokens.size) {
                forcedTokens[i].toFloat()
            } else {
                argmax(logits, noTimeStampTokenId)
            }

            generatedIds.add(nextToken)

            if (nextToken == VocabUtils.EOT.toFloat()) break
        }

        return generatedIds.toFloatArray()
    }

    private fun argmax(logits: Tensor, suppressTokenId: Long = VocabUtils.NOT.toLong()): Float {
        val shape = logits.shape()
        val vocabSize = shape[2].toInt()
        val data = logits.dataAsFloatArray
        val offset = ((shape[0] * shape[1] - 1) * vocabSize).toInt()

        data[offset + suppressTokenId.toInt()] = Float.NEGATIVE_INFINITY

        var maxIdx = 0
        var maxVal = Float.NEGATIVE_INFINITY

        for (i in 0 until vocabSize) {
            val value = data[offset + i]
            if (value > maxVal) {
                maxVal = value
                maxIdx = i
            }
        }

        return maxIdx.toFloat()
    }

    override suspend fun transcribeByModel(
        audioData: FloatArray,
        languageCode: String,
    ): FloatArray {
        module = loadModel(File(context.filesDir, modelPath).absolutePath)

        val melSpectrogram = vocabUtils.calMelSpectrogram(audioData)
        val tensor1 = Tensor.fromBlob(melSpectrogram, longArrayOf(1, 80, 3000))
        val eValue1 = EValue.from(tensor1)

        val result = runCatching {
            val encoded = module.execute("encoder", eValue1)[0]

            // module.execute("text_decoder", encoded)[0].toTensor().dataAsFloatArray

            decode(encoded)
        }
        Log.d("test","d: ${module.readLogBuffer().joinToString(", ")}")

        result.onSuccess {
            Log.d(
                "test", "추론 끝: ${
                    it.run {
                        StringBuilder().apply {
                            repeat(this@run.size) { idx ->
                                append("${this@run[idx]} ")
                            }
                        }.toString()
                    }
                }"
            )
        }

        module.destroy()
        return result.getOrThrow()
    }
}

class TfLiteSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
    modelPath: String
): SpeechToText(modelPath) {
    private lateinit var interpreter: InterpreterApi

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

    override suspend fun transcribeByModel(
        audioData: FloatArray,
        languageCode: String,
    ): FloatArray {
        val languageToken = LanguageToken.getIdForLanguage(languageCode)
            ?: throw IllegalArgumentException("지원되지 않는 언어: $languageCode")

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

        return FloatArray(outputBuffer.capacity()).apply {
            outputBuffer.get(this@apply)
        }
    }

    override fun deInitialize() {
        if (::interpreter.isInitialized) {
            interpreter.close()
        }
    }
}