package jinproject.aideo.core.runtime.impl.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeechSegment
import com.k2fsa.sherpa.onnx.TenVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.senseVoice.SenseVoiceManager
import jinproject.aideo.core.inference.whisper.AudioConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SileroVad @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private lateinit var vad: Vad
    var isInitialized: Boolean = false
        private set

    fun initialize(
        threshold: Float,
        minSpeechDuration: Float,
        minSilenceDuration: Float,
        maxSpeechDuration: Float,
    ) {
        if (::vad.isInitialized) {
            Log.d("test", "Already OnnxVad has been initialized")
            return
        }

        vad = Vad(
            assetManager = context.assets,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = SenseVoiceManager.VAD_MODEL_PATH,
                    threshold = threshold,
                    minSilenceDuration = minSilenceDuration,
                    minSpeechDuration = minSpeechDuration,
                    windowSize = 512,
                    maxSpeechDuration = maxSpeechDuration
                ),
                sampleRate = AudioConfig.SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
                debug = true
            )
        )
        isInitialized = true
    }

    fun release() {
        if(!isInitialized) {
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
}