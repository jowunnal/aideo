package jinproject.aideo.core.inference.whisper

import android.content.Context
import android.media.AudioFormat
import android.util.Log
import androidx.core.net.toUri
import com.k2fsa.sherpa.onnx.SpeechSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.SpeechRecognitionManager
import jinproject.aideo.core.inference.senseVoice.FixedChunkBuffer
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.media.MediaFileManager
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.core.media.audio.AudioProcessor.normalizeAudioSample
import jinproject.aideo.core.runtime.api.SpeechToText
import jinproject.aideo.core.runtime.impl.onnx.OnnxSTT
import jinproject.aideo.core.runtime.impl.onnx.OnnxSpeechToText
import jinproject.aideo.core.runtime.impl.onnx.SileroVad
import jinproject.aideo.core.runtime.impl.onnx.SpeakerDiarization
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import kotlinx.coroutines.DelicateCoroutinesApi
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
import kotlin.math.ceil
import kotlin.math.floor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Whisper

/**
 * Whisper AI 모델의 전체 프로세스를 처리하는 Controller 클래스
 */
class WhisperManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaFileManager: MediaFileManager,
    @OnnxSTT private val speechToText: SpeechToText,
    private val vad: SileroVad,
    private val speakerDiarization: SpeakerDiarization,
    localFileDataSource: LocalFileDataSource,
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
        //copyBinaryDataFromAssets()
        initOnnxWhisper()
        vad.initialize()
        speakerDiarization.initialize()

        isReady = true
    }

    private fun initOnnxWhisper() {
        speechToText.initialize(
            OnnxSpeechToText.OnnxRequirement(
                modelPath = WHISPER_ENCODER_PATH_ONNX,
                vocabPath = WHISPER_VOCAB_PATH_ONNX,
                availableModel = OnnxSpeechToText.AvailableModel.Whisper(decoderPath = WHISPER_DECODER_PATH_ONNX)
            )
        )
    }

    /**
     * Whisper Executorch 모델의 binary file 을 load
     */
    private fun copyBinaryDataFromAssets() {
        context.assets.list("models/") ?: return

        val modelsPath = File(context.filesDir, "models")

        if (!modelsPath.exists())
            modelsPath.mkdirs()

        val vocab = File(modelsPath, WHISPER_VOCAB_NAME_PTE)
        context.assets.open(WHISPER_VOCAB_PATH_PTE).use { input ->
            vocab.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val model = File(modelsPath, WHISPER_MODEL_NAME_PTE)
        context.assets.open(WHISPER_MODEL_PATH_PTE).use { input ->
            model.outputStream().use { output ->
                input.copyTo(output)
            }
        }
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
            launch {
                extractAudioFromVideo(videoItem.uri)
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

                                transcribeSingleSegment(
                                    nextVadSegment = nextVadSegment,
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
                        /*val paddedSamples =
                            FloatArray(AudioConfig.SAMPLE_RATE * AudioConfig.AUDIO_CHUNK_SECONDS).apply {
                                repeat(nextVadSegment.samples.size) { idx ->
                                    this[idx] = nextVadSegment.samples[idx]
                                }
                            }*/

                        transcribeSingleSegment(
                            nextVadSegment = nextVadSegment,
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

    private suspend fun transcribeSingleSegment(nextVadSegment: SpeechSegment, language: String) {
        val standardTime =
            nextVadSegment.start / AudioConfig.SAMPLE_RATE.toFloat()

        val sdResult = speakerDiarization.process(nextVadSegment.samples)

        Log.d(
            "test",
            "sdResult: ${sdResult.joinToString("\n") { "[${it.speaker}] : ${it.start} ~ ${it.end}" }}"
        )

        with(speechToText as OnnxSpeechToText) {
            setStandardTime(standardTime)
        }

        var lastEndIdx = 0

        sdResult.forEach { sd ->
            val startIdx = (AudioConfig.SAMPLE_RATE * floor(sd.start * 10) / 10).toInt()
                .coerceIn(lastEndIdx, nextVadSegment.samples.size)
            val endIdx = (AudioConfig.SAMPLE_RATE * ceil(sd.end * 10) / 10).toInt()
                .coerceIn(lastEndIdx, nextVadSegment.samples.size)
            lastEndIdx = endIdx

            Log.d("test", "전체샘플수: ${nextVadSegment.samples.size}, 시작인덱스: $startIdx, 끝인덱스: $endIdx")

            speechToText.setTimes(sd.start, sd.end)
            speechToText.transcribe(
                audioData = nextVadSegment.samples.copyOfRange(
                    startIdx,
                    endIdx
                ), language = language
            )
        }
    }

    private suspend fun extractAudioFromVideo(videoUri: String) {
        mediaFileManager.extractAudioData(
            videoContentUri = videoUri.toUri(),
            extractedAudioChannel = extractedAudioChannel,
            audioPreProcessor = { audioInfo ->
                when (audioInfo.mediaInfo.encodingBytes) {
                    AudioFormat.ENCODING_PCM_16BIT -> {
                        normalizeAudioSample(
                            audioChunk = audioInfo.audioData,
                            sampleRate = audioInfo.mediaInfo.sampleRate,
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

    @OptIn(DelicateCoroutinesApi::class)
    override fun release() {
        speechToText.release()
        vad.release()
        extractedAudioChannel.cancel()
        if (inferenceAudioChannel.isClosedForReceive) // 추론 중에 cancel 되면 native error 발생
            speakerDiarization.release()
        inferenceAudioChannel.cancel()

        isReady = false
    }

    companion object {
        const val WHISPER_VOCAB_NAME_PTE = "filters_vocab_multilingual.bin"
        const val WHISPER_VOCAB_PATH_PTE = "models/$WHISPER_VOCAB_NAME_PTE"
        const val WHISPER_MODEL_NAME_PTE = "model.pte"
        const val WHISPER_MODEL_PATH_PTE = "models/$WHISPER_MODEL_NAME_PTE"

        const val WHISPER_ENCODER_PATH_ONNX = "models/small-encoder.int8.onnx"
        const val WHISPER_DECODER_PATH_ONNX = "models/small-decoder.int8.onnx"
        const val WHISPER_VOCAB_PATH_ONNX = "models/small-tokens.txt"
    }
}