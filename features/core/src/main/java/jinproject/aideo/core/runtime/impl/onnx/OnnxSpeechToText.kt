package jinproject.aideo.core.runtime.impl.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.whisper.AudioConfig
import jinproject.aideo.core.runtime.api.SpeechToText
import jinproject.aideo.data.BuildConfig
import jinproject.aideo.data.TranslationManager
import java.util.Locale
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnnxSTT

class OnnxSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechToText() {
    private lateinit var recognizer: OfflineRecognizer
    override val transcribeResult = InferenceInfo(
        idx = 0,
        transcription = StringBuilder(),
        startTime = 0f,
        standardTime = 0f,
        endTime = 0f,
    )
    private lateinit var config: OfflineRecognizerConfig

    override fun initialize(r: InitRequirement) {
        if (isInitialized) {
            Log.d("test", "Already OnnxSpeechToText has been initialized")
            return
        }

        val requirements =
            r as? OnnxRequirement ?: throw IllegalArgumentException("[$r] is not OnnxRequirements")

        config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                tokens = r.vocabPath,
                numThreads = 1,
                provider = "cpu",
                debug = true,
            ),
            featConfig = getFeatureConfig(AudioConfig.SAMPLE_RATE, 80)
        )

        when (requirements.availableModel) {
            is AvailableModel.Whisper -> setWhisperModelConfig(
                encoderPath = r.modelPath,
                decoderPath = (r.availableModel as AvailableModel.Whisper).decoderPath,
                vocabPath = r.vocabPath,
                language = Locale.getDefault().language
            )

            is AvailableModel.SenseVoice -> setSenseVoiceModelConfig(
                modelPath = r.modelPath,
                language = "auto",
                vocabPath = r.vocabPath
            )
        }

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
                Log.d("test", "recognized: ${result.text}")

                if (result.text.isNotEmpty())
                    with(transcribeResult) {
                        transcription.apply {
                            appendLine(this@with.idx++)
                            appendLine(
                                "${TranslationManager.formatSrtTime(startTime + standardTime)} --> ${
                                    TranslationManager.formatSrtTime(
                                        endTime + standardTime
                                    )
                                }"
                            )
                            appendLine(result.text)
                            appendLine()
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("test", "Error while transcribing audio data", e)
        } finally {
            stream.release()
        }
    }

    override fun getResult(): String {
        return with(transcribeResult) {
            val result = transcription.toString().trim()
            transcription.clear()
            idx = 0
            startTime = 0f

            result
        }
    }

    fun setStandardTime(time: Float) {
        transcribeResult.standardTime = time
    }

    fun setTimes(start: Float, end: Float) {
        transcribeResult.apply {
            startTime = start
            endTime = end
        }
    }

    private fun setModelConfig(modelConfig: OfflineModelConfig) {
        config = config.apply {
            this.modelConfig = modelConfig
        }

        if (isInitialized)
            recognizer.setConfig(config)
    }

    fun setSenseVoiceModelConfig(
        modelPath: String,
        language: String,
        vocabPath: String,
    ) {
        setModelConfig(
            OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = modelPath,
                    language = language,
                    useInverseTextNormalization = true,
                ),
                whisper = OfflineWhisperModelConfig(),
                tokens = vocabPath,
                debug = BuildConfig.DEBUG,
            )
        )
    }

    fun setWhisperModelConfig(
        encoderPath: String,
        decoderPath: String,
        vocabPath: String,
        language: String,
    ) {
        setModelConfig(
            OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(),
                whisper = OfflineWhisperModelConfig(
                    encoder = encoderPath,
                    decoder = decoderPath,
                    language = language,
                ),
                tokens = vocabPath,
                debug = BuildConfig.DEBUG,
            )
        )
    }

    private fun updateLanguageConfig(language: String) {
        config = when (getAvailableModel()) {
            is AvailableModel.SenseVoice -> config.apply {
                modelConfig.senseVoice.language = language
            }

            is AvailableModel.Whisper -> config.apply { modelConfig.whisper.language = language }
        }
        recognizer.setConfig(config)
    }

    private fun getAvailableModel(): AvailableModel {
        return if (config.modelConfig.senseVoice.model.isEmpty())
            AvailableModel.Whisper(decoderPath = config.modelConfig.whisper.decoder)
        else
            AvailableModel.SenseVoice
    }

    class InferenceInfo(
        var idx: Int,
        override val transcription: StringBuilder,
        var standardTime: Float,
        var startTime: Float,
        var endTime: Float,
    ) : TranscribeResult()

    sealed class AvailableModel {
        data object SenseVoice : AvailableModel()
        data class Whisper(val decoderPath: String) : AvailableModel()
    }

    class OnnxRequirement(
        modelPath: String,
        vocabPath: String,
        val availableModel: AvailableModel,
    ) : InitRequirement(modelPath = modelPath, vocabPath = vocabPath)
}