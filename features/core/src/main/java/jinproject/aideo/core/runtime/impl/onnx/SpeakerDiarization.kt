package jinproject.aideo.core.runtime.impl.onnx

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeakerDiarization @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private lateinit var diarization: OfflineSpeakerDiarization
    var isInitialized: Boolean = false
        private set


    fun initialize() {
        if (isInitialized) {
            Log.d("test", "Already OnnxDiarization has been initialized")
            return
        }

        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig("models/segmentation.onnx"),
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = "models/embedding.onnx",
                numThreads = 1,
            ),
            clustering = FastClusteringConfig(numClusters = -1, threshold = 0.8f),
            minDurationOn = 0.05f,
            minDurationOff = 0.3f,
        )

        diarization = OfflineSpeakerDiarization(assetManager = context.assets, config = config)
        isInitialized = true
    }

    fun release() {
        if (isInitialized) {
            diarization.release()
            isInitialized = false
        }
    }

    fun process(audioData: FloatArray): Array<OfflineSpeakerDiarizationSegment> {
        return diarization.process(audioData)
    }
}