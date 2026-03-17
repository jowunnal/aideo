package jinproject.aideo.core.inference.speechRecognition

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.inference.AiModelConfig.MODELS_ROOT_DIR
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.speechRecognition.api.SpeechRecognition
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.core.utils.copyAssetToInternalStorage
import jinproject.aideo.core.utils.getPackAssetPath
import jinproject.aideo.data.BuildConfig
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Whisper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localSettingDataSource: LocalSettingDataSource,
) : SpeechRecognition(), TimeStampedSR {
    override val transcribedResult: StringBuilder = StringBuilder()
    override val timeInfo: TimeStampedSR.TimeInfo = TimeStampedSR.TimeInfo.getDefault()
    override val availableSpeechRecognition: SpeechRecognitionAvailableModel =
        SpeechRecognitionAvailableModel.Whisper
    private var recognizer: OfflineRecognizer? = null
    private var config: OfflineRecognizerConfig? = null

    override suspend fun initialize() {
        super.initialize()

        val copiedTokensPath = copyAssetToInternalStorage(
            path = WHISPER_TOKEN_PATH,
            context = context
        )

        config = OfflineRecognizerConfig(
            featConfig = getFeatureConfig(AudioConfig.SAMPLE_RATE, 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = "${context.getPackAssetPath(AiModelConfig.SPEECH_WHISPER_PACK)}/$WHISPER_ENCODER_PATH",
                    decoder = "${context.getPackAssetPath(AiModelConfig.SPEECH_WHISPER_PACK)}/$WHISPER_DECODER_PATH",
                    language = localSettingDataSource.getInferenceTargetLanguage().first(),
                ),
                tokens = copiedTokensPath,
                debug = BuildConfig.DEBUG,
            )
        )

        recognizer = OfflineRecognizer(
            assetManager = null,
            config = config!!,
        )

        isInitialized = true
    }

    override fun release() {
        super.release()

        recognizer?.release()
        recognizer = null
        config = null
        isInitialized = false
    }

    override fun resetState() {
        super.resetState()
        timeInfo.apply {
            idx = 0
            startTime = 0f
            endTime = 0f
            standardTime = 0f
        }
    }

    override suspend fun transcribeByModel(audioData: FloatArray) {
        isUsed = true

        val stream = recognizer!!.createStream()

        try {
            stream.acceptWaveform(audioData, AudioConfig.SAMPLE_RATE)
            recognizer!!.decode(stream)

            recognizer!!.getResult(stream).let { result ->
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

    override fun updateLanguageConfig(language: String) {
        if (config == null && recognizer == null && !isInitialized)
            return

        if (config?.modelConfig?.whisper?.language != language) {
            config!!.modelConfig.whisper.language = language
            recognizer!!.setConfig(config!!)
        }
    }

    override fun getResult(): String {
        val result = transcribedResult.toString().trim()
        transcribedResult.clear()

        return result
    }

    companion object {
        const val WHISPER_ENCODER_PATH = "$MODELS_ROOT_DIR/whisper_base-encoder.int8.onnx"
        const val WHISPER_DECODER_PATH = "$MODELS_ROOT_DIR/whisper_base-decoder.int8.onnx"
        const val WHISPER_TOKEN_PATH = "$MODELS_ROOT_DIR/whisper_small-tokens.txt"
    }
}