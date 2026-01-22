package jinproject.aideo.core.inference.speechRecognition

import android.content.Context
import android.util.Log
import com.google.android.datatransport.runtime.scheduling.SchedulingConfigModule_ConfigFactory.config
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.core.inference.speechRecognition.api.SpeechRecognition
import jinproject.aideo.core.inference.OnnxModelConfig.MODELS_ROOT_DIR
import jinproject.aideo.data.BuildConfig
import java.util.Locale
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WhisperModel

class Whisper @Inject constructor(
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
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = WHISPER_ENCODER_PATH,
                    decoder = WHISPER_DECODER_PATH,
                    language = Locale.getDefault().language,
                ),
                tokens = WHISPER_TOKEN_PATH,
                debug = BuildConfig.DEBUG,
            )
        )

        recognizer = OfflineRecognizer(
            assetManager = context.assets,
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
            Log.e("test", "Error while transcribing audio data", e)
        } finally {
            stream.release()
        }
    }

    private fun updateLanguageConfig(language: String) {
        config.modelConfig.whisper.language = language

        recognizer.setConfig(config)
    }

    override fun getResult(): String {
        val result = transcribedResult.toString().trim()
        transcribedResult.clear()

        return result
    }

    companion object {
        const val WHISPER_ENCODER_PATH = "$MODELS_ROOT_DIR/small-encoder.int8.onnx"
        const val WHISPER_DECODER_PATH = "$MODELS_ROOT_DIR/small-decoder.int8.onnx"
        const val WHISPER_TOKEN_PATH = "$MODELS_ROOT_DIR/small-tokens.txt"
    }
}