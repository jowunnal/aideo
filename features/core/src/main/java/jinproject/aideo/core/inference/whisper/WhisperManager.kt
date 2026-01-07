package jinproject.aideo.core.inference.whisper

import android.content.Context
import android.media.AudioFormat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.SpeechRecognitionManager
import jinproject.aideo.core.inference.senseVoice.FixedChunkBuffer
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.media.MediaFileManager
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.runtime.api.SpeechToText
import jinproject.aideo.core.runtime.impl.executorch.ExecutorchSTT
import jinproject.aideo.core.runtime.impl.onnx.SileroVad
import jinproject.aideo.core.utils.AudioProcessor.normalizeAudioSample
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Whisper

/**
 * Whisper AI 모델의 전체 프로세스를 처리하는 Controller 클래스
 */
class WhisperManager @Inject constructor(
    @ApplicationContext private val context: Context,
    localFileDataSource: LocalFileDataSource,
    private val mediaFileManager: MediaFileManager,
    @ExecutorchSTT private val speechToText: SpeechToText,
    private val vad: SileroVad,
) : SpeechRecognitionManager(localFileDataSource) {
    private val extractedAudioChannel =
        Channel<FloatArray>(capacity = Channel.BUFFERED)
    private val inferenceAudioChannel = Channel<FloatArray>(capacity = Channel.BUFFERED)

    /**
     * 0 ~ 1f
     */
    override val inferenceProgress: StateFlow<Float> field: MutableStateFlow<Float> = MutableStateFlow(
        0f
    )

    override fun initialize() {
        copyBinaryDataFromAssets()
        loadBaseModel()
        vad.initialize()

        isReady = true
    }

    private fun copyBinaryDataFromAssets() {
        context.assets.list("models/") ?: return

        val modelsPath = File(context.filesDir, "models")

        if (!modelsPath.exists())
            modelsPath.mkdirs()

        val vocab = File(modelsPath, VOCAB_FILE_NAME)
        context.assets.open(VOCAB_FILE_PATH).use { input ->
            vocab.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val model = File(modelsPath, MODEL_FILE_NAME)
        context.assets.open(MODEL_FILE_PATH).use { input ->
            model.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun loadBaseModel() {
        speechToText.initialize()
    }

    /**
     * [오디오 추출 - 정규화 - 추론 - 후처리 - 자막파일 저장] 까지의 전체 프로세스를 처리
     *
     * @param videoItem : videoItem
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun transcribe(
        videoItem: VideoItem,
        language: String,
    ) {
        withContext(Dispatchers.IO) {

            /**
             * 미디어로 부터 음성 트랙을 추출
             */
            launch {
                mediaFileManager.extractAudioData(
                    videoContentUri = videoItem.uri.toUri(),
                    extractedAudioChannel = extractedAudioChannel,
                    audioPreProcessor = { audioInfo ->
                        when (audioInfo.mediaInfo.encodingBytes) {
                            AudioFormat.ENCODING_PCM_16BIT -> {
                                normalizeAudioSample(
                                    audioChunk = audioInfo.audioData,
                                    sampleRate = audioInfo.mediaInfo.sampleRate,
                                    channelCount = audioInfo.mediaInfo.channelCount
                                )
                            }

                            AudioFormat.ENCODING_PCM_FLOAT -> {
                                val floatBuffer =
                                    ByteBuffer.wrap(audioInfo.audioData)
                                        .order(ByteOrder.nativeOrder())
                                        .asFloatBuffer()

                                FloatArray(floatBuffer.remaining()).apply {
                                    floatBuffer.get(this)
                                }
                            }

                            else -> {
                                throw IllegalArgumentException("Unsupported encoding type")
                            }
                        }
                    }
                )
            }

            launch {
                val windowSize = 512
                val buffer = FixedChunkBuffer(windowSize)
                val resumeSignal = Channel<Unit>(capacity = 1)

                val job = launch {
                    for (i in extractedAudioChannel) {
                        buffer.write(i)
                        if (buffer.totalSamples >= windowSize)
                            resumeSignal.send(Unit)
                    }

                    resumeSignal.close()
                }

                launch {
                    while (job.isActive || buffer.totalSamples >= windowSize) {
                        val chunk = buffer.readChunk()
                        if (chunk != null) {
                            inferenceAudioChannel.send(chunk)
                        } else {
                            try {
                                resumeSignal.receive()
                            } catch (e: Exception) {
                                if (e is ClosedReceiveChannelException)
                                    break
                            }
                        }
                    }

                    inferenceAudioChannel.send(buffer.flush())
                    inferenceAudioChannel.close()
                }
            }

            val srtText = async {
                var processedAudioSize = 0

                for (i in inferenceAudioChannel) {
                    vad.acceptWaveform(i)
                    processedAudioSize += i.size

                    if (vad.isSpeechDetected()) {
                        while (vad.hasSegment()) {
                            vad.getNextSegment().also { nextVadSegment ->
                                val paddedSamples =
                                    FloatArray(AudioConfig.SAMPLE_RATE * AudioConfig.AUDIO_CHUNK_SECONDS).apply {
                                        repeat(nextVadSegment.samples.size) { idx ->
                                            this[idx] = nextVadSegment.samples[idx]
                                        }
                                    }

                                speechToText.transcribe(
                                    audioData = paddedSamples,
                                    language = language
                                )

                                (mediaFileManager as AndroidMediaFileManager).mediaInfo?.let {
                                    val total =
                                        AudioConfig.SAMPLE_RATE * it.channelCount * it.encodingBytes * it.duration / Short.SIZE_BYTES

                                    inferenceProgress.value =
                                        processedAudioSize.toFloat() / (total).toFloat()
                                }
                            }
                            vad.popSegment()
                        }
                    }
                }

                vad.flush()
                while (vad.hasSegment()) {
                    vad.getNextSegment().also { nextVadSegment ->
                        val paddedSamples =
                            FloatArray(AudioConfig.SAMPLE_RATE * AudioConfig.AUDIO_CHUNK_SECONDS).apply {
                                repeat(nextVadSegment.samples.size) { idx ->
                                    this[idx] = nextVadSegment.samples[idx]
                                }
                            }

                        speechToText.transcribe(
                            audioData = paddedSamples,
                            language = language
                        )
                    }
                    vad.popSegment()
                }

                inferenceProgress.value = 1f

                speechToText.getResult()
            }.await()

            storeSubtitleFile(
                subtitle = srtText,
                videoItemId = videoItem.id
            )
        }
    }

    override fun release() {
        speechToText.release()
        vad.release()
        extractedAudioChannel.cancel()
        inferenceAudioChannel.cancel()

        isReady = false
    }

    companion object {
        const val VOCAB_FILE_NAME = "filters_vocab_multilingual.bin"
        const val VOCAB_FILE_PATH = "models/$VOCAB_FILE_NAME"
        const val MODEL_FILE_NAME = "model.pte"
        const val MODEL_FILE_PATH = "models/$MODEL_FILE_NAME"
    }
}