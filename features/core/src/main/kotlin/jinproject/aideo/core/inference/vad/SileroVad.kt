package jinproject.aideo.core.inference.vad

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeechSegment
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.utils.getPackAssetPath
import jinproject.aideo.data.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SileroVad @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private lateinit var vad: Vad
    var isInitialized: Boolean = false
        private set

    fun initialize() {
        if (isInitialized) {
            Log.d("test", "Already OnnxVad has been initialized")
            return
        }

        vad = Vad(
            assetManager = null,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "${context.getPackAssetPath(AiModelConfig.SPEECH_BASE_PACK)}/$VAD_MODEL_PATH",
                    threshold = 0.01f,
                    minSilenceDuration = 0.1f,
                    minSpeechDuration = 0.1f,
                    maxSpeechDuration = 9.5f,
                    windowSize = 512,
                ),
                sampleRate = AudioConfig.SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = BuildConfig.DEBUG
            )
        )
        isInitialized = true
    }

    fun release() {
        if (isInitialized) {
            vad.clear()
            vad.release()
            isInitialized = false
        }
    }

    fun acceptWaveform(data: FloatArray) {
        vad.acceptWaveform(data)
    }

    fun flush() {
        vad.flush()
    }

    fun getNextSegment(): SpeechSegment = vad.front()

    fun popSegment() {
        vad.pop()
    }

    fun hasSegment(): Boolean = vad.empty().not()

    fun isSpeechDetected(): Boolean {
        return vad.isSpeechDetected()
    }

    fun reset() = vad.reset()

    companion object {
        const val VAD_MODEL_PATH = "${AiModelConfig.MODELS_ROOT_DIR}/silero_vad.onnx"
    }
}