package jinproject.aideo.core.runtime.impl.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeechSegment
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.getOfflineModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.senseVoice.SenseVoiceManager
import jinproject.aideo.core.inference.senseVoice.SubtitleFormatter.formatSrtTime
import jinproject.aideo.core.inference.whisper.AudioConfig
import jinproject.aideo.core.runtime.api.SpeechToText
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnnxSTT

class OnnxSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
    modelPath: String,
    language: String,
) : SpeechToText(modelPath = modelPath, language = language) {
    private lateinit var recognizer: OfflineRecognizer
    override val transcribeResult = InferenceInfo(idx = 0, transcription = StringBuilder(), startTime = 0f)

    override fun initialize(vocabPath: String) {
        if (::recognizer.isInitialized) {
            Log.d("test", "Already OnnxSpeechToText has been initialized")
            return
        }

        recognizer = OfflineRecognizer(
            assetManager = context.assets,
            config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = modelPath,
                        language = language,
                        useInverseTextNormalization = true,
                    ),
                    tokens = vocabPath,
                    numThreads = 1,
                    provider = "cpu",
                    debug = true,
                ),
                featConfig = getFeatureConfig(AudioConfig.SAMPLE_RATE, 80)
            ),
        )

        isInitialized = true
    }

    override fun deInitialize() {
        recognizer.release()
    }

    override suspend fun transcribeByModel(audioData: FloatArray) {
        val stream = recognizer.createStream()

        try {
            Log.d("test", "OnnxSpeechToText transcribeByModel: ${audioData.size}")
            stream.acceptWaveform(audioData, AudioConfig.SAMPLE_RATE)
            Log.d("test", "waveform accepted")
            recognizer.decode(stream)
            Log.d("test", "decoded")

            recognizer.getResult(stream).let { result ->
                Log.d("test", "rrecognized: ${result.text}")
                if (result.text.isNotEmpty() && result.timestamps.isNotEmpty())
                    with(transcribeResult) {
                        transcription.apply {
                            appendLine(this@with.idx++)
                            appendLine(
                                "${formatSrtTime(result.timestamps.first() + transcribeResult.startTime)} --> ${
                                    formatSrtTime(
                                        result.timestamps.last() + transcribeResult.startTime
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



    data class InferenceInfo(
        var idx: Int,
        override val transcription: StringBuilder,
        var startTime: Float,
    ) : TranscribeResult()

    fun setStartTime(startTime: Float) {
        transcribeResult.startTime = startTime
    }

    companion object {
        const val TAG = "OnnxSpeechToText"
    }
}