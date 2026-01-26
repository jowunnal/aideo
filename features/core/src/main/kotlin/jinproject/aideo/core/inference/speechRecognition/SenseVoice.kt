package jinproject.aideo.core.inference.speechRecognition

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.QnnConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.inference.speechRecognition.api.SpeechRecognition
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.core.utils.copyAssetToInternalStorage
import jinproject.aideo.core.utils.getPackAssetPath
import jinproject.aideo.data.BuildConfig
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SenseVoiceModel

class SenseVoice @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SpeechRecognition(), TimeStampedSR {
    override val transcribedResult: StringBuilder = StringBuilder()
    override val timeInfo: TimeStampedSR.TimeInfo = TimeStampedSR.TimeInfo.getDefault()
    private lateinit var recognizer: OfflineRecognizer
    private lateinit var config: OfflineRecognizerConfig

    override fun initialize() {
        if (isInitialized) {
            return
        }

        config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(AudioConfig.SAMPLE_RATE, 80),
        ).apply {
            modelConfig = if (isQnn) {
                OfflineRecognizer.prependAdspLibraryPath(context.applicationInfo.nativeLibraryDir)

                val copiedModelPath = copyAssetToInternalStorage(
                    path = MODEL_QUANT_PATH,
                    context = context,
                )
                val copiedBinaryPath = copyAssetToInternalStorage(
                    path = BINARY_QUANT_PATH,
                    context = context,
                )
                val copiedTokensPath = copyAssetToInternalStorage(
                    path = TOKEN_PATH,
                    context = context
                )

                OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = copiedModelPath,
                        language = "auto",
                        useInverseTextNormalization = true,
                        qnnConfig = QnnConfig(
                            backendLib = "libQnnHtp.so",
                            systemLib = "libQnnSystem.so",
                            contextBinary = copiedBinaryPath,
                        )
                    ),
                    provider = "qnn",
                    numThreads = 2,
                    tokens = copiedTokensPath,
                    debug = BuildConfig.DEBUG,
                )
            } else
                OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = "${context.getPackAssetPath(AiModelConfig.SPEECH_BASE_PACK)}/$MODEL_PATH",
                        language = "auto",
                        useInverseTextNormalization = true,
                    ),
                    tokens = "${context.getPackAssetPath(AiModelConfig.SPEECH_BASE_PACK)}/$TOKEN_PATH",
                    debug = BuildConfig.DEBUG,
                )
        }

        recognizer = OfflineRecognizer(
            assetManager = null,
            config = config,
        )

        isInitialized = true
    }

    override fun release() {
        if (isInitialized) {
            recognizer.release()
            isInitialized = false
        }
    }

    override suspend fun transcribeByModel(audioData: FloatArray, language: String) {
        updateLanguageConfig(language)
        val stream = recognizer.createStream()

        try {
            stream.acceptWaveform(audioData, AudioConfig.SAMPLE_RATE)
            recognizer.decode(stream)

            recognizer.getResult(stream).let { result ->
                if (result.text.isNotEmpty())
                    transcribedResult.apply {
                        appendLine(timeInfo.idx++)
                        appendLine(
                            "${getBeginTimeOfSRT()} --> ${getEndTimeOfSRT()}"
                        )
                        appendLine(result.text)
                        appendLine()
                    }
            }
        } catch (e: Exception) {
            Timber.e("Error while transcribing audio data: ${e.stackTraceToString()}")
        } finally {
            stream.release()
        }
    }

    private fun updateLanguageConfig(language: String) {
        config.modelConfig.senseVoice.language = language

        recognizer.setConfig(config)
    }

    override fun getResult(): String {
        val result = transcribedResult.toString().trim()
        transcribedResult.clear()

        return result
    }

    companion object {
        const val MODEL_PATH = "${AiModelConfig.MODELS_ROOT_DIR}/sense_voice_model.int8.onnx"
        const val MODEL_QUANT_PATH = "${AiModelConfig.QNN_MODELS_ROOT_DIR}/sense_voice_libmodel.so"
        const val BINARY_QUANT_PATH = "${AiModelConfig.QNN_MODELS_ROOT_DIR}/sense_voice_model.bin"
        const val TOKEN_PATH = "${AiModelConfig.MODELS_ROOT_DIR}/sense_voice_tokens.txt"
    }
}