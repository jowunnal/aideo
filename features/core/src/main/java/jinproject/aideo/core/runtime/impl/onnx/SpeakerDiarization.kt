package jinproject.aideo.core.runtime.impl.onnx

import android.util.Log
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import javax.inject.Inject

class SpeakerDiarization {
    private lateinit var diarization: OfflineSpeakerDiarization

    fun initialize() {
        if (::diarization.isInitialized) {
            Log.d("test", "Already OnnxDiarization has been initialized")
            return
        }

        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig("models/segmentation.onnx"),
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = "models/embedding.onnx",
            ),
            clustering = FastClusteringConfig(threshold = 0.7f),
        )

        diarization = OfflineSpeakerDiarization(config=config)
    }

    fun release() {
        diarization.release()
    }

    fun process(audioData: FloatArray): Array<OfflineSpeakerDiarizationSegment> {
        return diarization.process(audioData)
    }
}