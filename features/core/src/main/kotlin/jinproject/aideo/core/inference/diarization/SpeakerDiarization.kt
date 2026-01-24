package jinproject.aideo.core.inference.diarization

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
import jinproject.aideo.core.inference.AiModelConfig
import jinproject.aideo.core.utils.getPackAssetPath
import jinproject.aideo.data.BuildConfig
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
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                    "${context.getPackAssetPath(AiModelConfig.SPEECH_BASE_PACK)}/$SEGMENTATION_MODEL_PATH"
                ),
                debug = BuildConfig.DEBUG
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = "${context.getPackAssetPath(AiModelConfig.SPEECH_BASE_PACK)}/$EMBEDDING_MODEL_PATH",
                numThreads = 1,
                debug = BuildConfig.DEBUG
            ),
            clustering = FastClusteringConfig(numClusters = -1, threshold = 0.75f),
            minDurationOn = 0.1f,
            minDurationOff = 0.05f,
        )

        diarization = OfflineSpeakerDiarization(
            assetManager = null,
            config = config
        )
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

    companion object {
        const val SEGMENTATION_MODEL_PATH = "${AiModelConfig.MODELS_ROOT_DIR}/segmentation.onnx"
        const val EMBEDDING_MODEL_PATH = "${AiModelConfig.MODELS_ROOT_DIR}/embedding.onnx"
    }
}