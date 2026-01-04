package jinproject.aideo.core.runtime.impl.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.senseVoice.SubtitleFormatter.formatSrtTime
import jinproject.aideo.core.inference.whisper.AudioConfig
import jinproject.aideo.core.runtime.api.SpeechToText
import javax.inject.Inject
import javax.inject.Qualifier
import kotlin.plus

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OnnxSTT

class OnnxSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
    modelPath: String,
    vocabPath: String,
) : SpeechToText(modelPath = modelPath, vocabPath = vocabPath) {
    private lateinit var recognizer: OfflineRecognizer
    override val transcribeResult = InferenceInfo(
        idx = 0,
        transcription = StringBuilder(),
        startTime = 0f,
        standardTime = 0f,
        endTime = 0f,
        speakers = HashMap<Int, String>(),
        currentSpeaker = 0,
    )
    private var config: OfflineRecognizerConfig? = null

    override fun initialize() {
        if (isInitialized) {
            Log.d("test", "Already OnnxSpeechToText has been initialized")
            return
        }

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
        ).also {
            recognizer = OfflineRecognizer(
                assetManager = context.assets,
                config = it,
            )
        }

        isInitialized = true
    }

    override fun release() {
        if (isInitialized) {
            recognizer.release()
            isInitialized = false
        }
    }

    override suspend fun transcribeByModel(audioData: FloatArray) {
        recognizer.setConfig(config!!.apply {
            modelConfig.senseVoice.language = language
        })
        val stream = recognizer.createStream()

        try {
            stream.acceptWaveform(audioData, AudioConfig.SAMPLE_RATE)
            recognizer.decode(stream)

            recognizer.getResult(stream).let { result ->
                Log.d("test", "recognized: ${result.text}")

                if (result.text.isNotEmpty() && result.timestamps.isNotEmpty())
                    with(transcribeResult) {
                        transcription.apply {
                            appendLine(this@with.idx++)
                            appendLine(
                                "${formatSrtTime(startTime + standardTime)} --> ${
                                    formatSrtTime(
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

    class InferenceInfo(
        var idx: Int,
        override val transcription: StringBuilder,
        var standardTime: Float,
        var startTime: Float,
        var endTime: Float,
        var speakers: MutableMap<Int, String>,
        var currentSpeaker: Int,
    ) : TranscribeResult()

    fun setStandardTime(time: Float) {
        transcribeResult.standardTime = time
    }

    fun setTimes(start: Float, end: Float) {
        transcribeResult.apply {
            startTime = start
            endTime = end
        }
    }

    fun setCurrentSpeaker(speaker: Int) {
        transcribeResult.currentSpeaker = speaker
    }

    fun getStartTime(): Float = transcribeResult.startTime

    companion object {
        const val TAG = "OnnxSpeechToText"
    }
}