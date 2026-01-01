package jinproject.aideo.core.inference.senseVoice

import android.media.AudioFormat
import android.util.Log
import androidx.core.net.toUri
import jinproject.aideo.core.media.MediaFileManager
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.inference.whisper.AudioConfig
import jinproject.aideo.core.inference.whisper.WhisperAudioProcessor
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.runtime.api.SpeechToText
import jinproject.aideo.core.runtime.impl.onnx.OnnxSTT
import jinproject.aideo.core.runtime.impl.onnx.OnnxSpeechToText
import jinproject.aideo.core.runtime.impl.onnx.SileroVad
import jinproject.aideo.core.utils.AudioProcessor.normalizeAudioSample
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.repository.impl.getSubtitleFileIdentifier
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.inject.Inject

class SenseVoiceManager @Inject constructor(
    val mediaFileManager: MediaFileManager,
    @OnnxSTT private val speechToText: SpeechToText,
    private val vad: SileroVad,
    private val localFileDataSource: LocalFileDataSource,
) {
    var isReady: Boolean = false

    private val extractedAudioChannel = Channel<FloatArray>(capacity = Channel.BUFFERED)
    private val inferenceAudioChannel = Channel<FloatArray>(capacity = Channel.BUFFERED)

    val inferenceProgress: StateFlow<Float> field: MutableStateFlow<Float> = MutableStateFlow(
        0f
    )

    fun initialize() {
        speechToText.initialize(VOCAB_PATH)
        vad.initialize(
            threshold = 0.2f,
            minSilenceDuration = 0.25f,
            minSpeechDuration = 0.25f,
            maxSpeechDuration = 5.0f
        )

        isReady = true
    }

    fun release() {
        speechToText.deInitialize()
        vad.deInitialize()
        extractedAudioChannel.cancel()
        inferenceAudioChannel.cancel()
        inferenceProgress.value = 0f
        isReady = false
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    suspend fun transcribe(
        videoItem: VideoItem,
    ) = withContext(Dispatchers.Default) {
        launch(Dispatchers.IO) {
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
                                ByteBuffer.wrap(audioInfo.audioData).order(ByteOrder.nativeOrder())
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
            /*val (samples, sampleRate) = WaveReader.readWave(
                assetManager = context.assets,
                filename = "ja.wav"
            )
            Log.d("test", "sampleRate: $sampleRate")

            extractedAudioChannel.send(samples)
            extractedAudioChannel.close()*/
        }

        async {
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

        val transcribedSrtText = async {
            var processedAudioSize = 0

            for (i in inferenceAudioChannel) {
                vad.acceptWaveform(i)
                processedAudioSize += i.size

                if (vad.isSpeechDetected()) {
                    while (vad.hasSegment()) {
                        vad.getNextSegment().also { nextVadSegment ->
                            val startTime =
                                nextVadSegment.start / AudioConfig.SAMPLE_RATE.toFloat()
                            (speechToText as OnnxSpeechToText).setStartTime(startTime) // TODO 제거해야함.
                            speechToText.transcribe(nextVadSegment.samples)

                            (mediaFileManager as AndroidMediaFileManager).mediaInfo?.let {
                                val total = AudioConfig.SAMPLE_RATE * it.channelCount * it.encodingBytes * it.duration / Short.SIZE_BYTES

                                inferenceProgress.value = (processedAudioSize.toFloat() / (total).toFloat()).coerceAtMost(0.9f).also {
                                    Log.d("test","$processedAudioSize / $total")
                                }
                            }
                        }
                        vad.popSegment()
                    }
                }
            }

            vad.flush()
            while (vad.hasSegment()) {
                vad.getNextSegment().also { nextVadSegment ->
                    val startTime =
                        nextVadSegment.start / AudioConfig.SAMPLE_RATE.toFloat()
                    (speechToText as OnnxSpeechToText).setStartTime(startTime) // TODO 제거해야함.
                    speechToText.transcribe(nextVadSegment.samples)
                }
                vad.popSegment()
            }

            speechToText.getResult()
        }.await()

        Log.d("test", "Transcribed Result:\n$transcribedSrtText")

        val languageCode = TranslationManager.detectLanguage(transcribedSrtText)

        localFileDataSource.createFileAndWriteOnOutputStream(
            fileIdentifier = getSubtitleFileIdentifier(
                id = videoItem.id,
                languageCode = languageCode
            ),
            writeContentOnFile = { outputStream ->
                runCatching {
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(transcribedSrtText)
                    }
                }.map {
                    true
                }.getOrElse {
                    false
                }
            }
        )

        inferenceProgress.value = 1f
    }

    companion object {
        const val SENSEVOICE_MODEL_PATH = "models/model.int8.onnx"
        const val VOCAB_PATH = "models/tokens.txt"
        const val VAD_MODEL_PATH = "models/silero_vad.onnx"
    }
}

class FixedChunkBuffer(private val chunkSize: Int = 512) {
    private val deque = ArrayDeque<Float>()
    private val mutex = Mutex()
    var totalSamples = 0
        private set

    suspend fun write(data: FloatArray) {
        mutex.withLock {
            repeat(data.size) { idx ->
                deque.add(data[idx])
            }
            totalSamples += data.size
        }
    }

    suspend fun readChunk(): FloatArray? {
        return mutex.withLock {
            if (totalSamples >= chunkSize) {
                val chunk = FloatArray(chunkSize)
                repeat(chunkSize) { idx ->
                    chunk[idx] = deque.removeFirst()
                }
                totalSamples -= chunkSize
                chunk
            } else null
        }
    }

    fun flush(): FloatArray {
        val chunk = FloatArray(deque.size)
        repeat(deque.size) {
            chunk[it] = deque.removeFirst()
        }
        totalSamples = 0
        return chunk
    }
}

/**
 * SRT 자막 포맷터
 */
object SubtitleFormatter {
    fun formatSrtTime(seconds: Float): String {
        val hours = (seconds / 3600).toInt()
        val minutes = ((seconds % 3600) / 60).toInt()
        val secs = (seconds % 60).toInt()
        val millis = ((seconds % 1) * 1000).toInt()
        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d,%03d",
            hours,
            minutes,
            secs,
            millis
        )
    }
}