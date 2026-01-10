package jinproject.aideo.core.inference.senseVoice

import android.media.AudioFormat
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.k2fsa.sherpa.onnx.SpeechSegment
import jinproject.aideo.core.inference.SpeechRecognitionManager
import jinproject.aideo.core.inference.senseVoice.SenseVoiceManager.Companion.SENSE_VOICE_MODEL_NAME
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.media.MediaFileManager
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.core.media.audio.AudioProcessor.normalizeAudioSample
import jinproject.aideo.core.runtime.api.SpeechToText
import jinproject.aideo.core.runtime.impl.onnx.OnnxSTT
import jinproject.aideo.core.runtime.impl.onnx.OnnxSpeechToText
import jinproject.aideo.core.runtime.impl.onnx.Punctuation
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Qualifier
import kotlin.math.ceil
import kotlin.math.floor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SenseVoice

class SenseVoiceManager @Inject constructor(
    val mediaFileManager: MediaFileManager,
    @param:OnnxSTT private val speechToText: SpeechToText,
    private val vad: SileroVad,
    private val speakerDiarization: SpeakerDiarization,
    localFileDataSource: LocalFileDataSource,
    private val punctuation: Punctuation,
) : SpeechRecognitionManager(localFileDataSource) {
    private val extractedAudioChannel = Channel<FloatArray>(capacity = Channel.BUFFERED)
    private val inferenceAudioChannel = Channel<FloatArray>(capacity = Channel.BUFFERED)

    override val inferenceProgress: StateFlow<Float> field: MutableStateFlow<Float> = MutableStateFlow(
        0f
    )

    override fun initialize() {
        isReady = true

        val availableSoCModel = getAvailableSoCModel()
        speechToText.initialize(
            OnnxSpeechToText.OnnxRequirement(
                modelPath = availableSoCModel.path,
                vocabPath = SENSE_VOICE_VOCAB_PATH,
                availableModel = OnnxSpeechToText.AvailableModel.SenseVoice,
                isQnn = availableSoCModel.isQnnModel()
            )
        )

        vad.initialize()
        speakerDiarization.initialize()
    }

    private fun getAvailableSoCModel(): AvailableSoCModel {
        return if (Build.VERSION.SDK_INT > 31)
            AvailableSoCModel.findByName(Build.SOC_MODEL.uppercase())
        else
            AvailableSoCModel.findByName(Build.HARDWARE.uppercase())

    }

    override fun release() {
        speechToText.release()
        vad.release()
        punctuation.release()
        extractedAudioChannel.cancel()
        inferenceAudioChannel.cancel()
        isReady = false
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    override suspend fun transcribe(
        videoItem: VideoItem,
        language: String,
    ) {
        withContext(Dispatchers.Default) {
            launch(Dispatchers.IO) {
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

            val transcribedSrtText = async {
                var processedAudioSize = 0
                if (punctuation.isAvailableLanguage(language))
                    punctuation.initialize()

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

            Log.d("test", "Transcribed Result:\n$transcribedSrtText")

            val punctuatedSrtText =
                if (punctuation.isAvailableLanguage(language))
                    punctuation.addPunctuationOnSrt(transcribedSrtText)
                else
                    transcribedSrtText

            Log.d("test", "Punctuated Result:\n$punctuatedSrtText")

            storeSubtitleFile(
                subtitle = punctuatedSrtText,
                videoItemId = videoItem.id,
            )

            inferenceProgress.value = 1f
            vad.reset()
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
        /*val (samples, sampleRate) = WaveReader.readWave(
        assetManager = context.assets,
        filename = "ja.wav"
    )
    Log.d("test", "sampleRate: $sampleRate")

    extractedAudioChannel.send(samples)
    extractedAudioChannel.close()*/
    }

    companion object {
        const val SENSE_VOICE_MODEL_NAME = "model.int8.onnx"
        const val SENSE_VOICE_VOCAB_PATH = "models/tokens.txt"
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

const val QNN_MODELS_ROOT_DIR = "qnn_models"

enum class AvailableSoCModel(val path: String) {
    SM8450("$QNN_MODELS_ROOT_DIR/model_sm8450.bin"),
    SM8475("$QNN_MODELS_ROOT_DIR/model_sm8475.bin"),
    SM8550("$QNN_MODELS_ROOT_DIR/model_sm8550.bin"),
    SM8650("$QNN_MODELS_ROOT_DIR/model_sm8650.bin"),
    SM8750("$QNN_MODELS_ROOT_DIR/model_sm8750.bin"),
    SM8850("$QNN_MODELS_ROOT_DIR/model_sm8850.bin"),
    QCS9100("$QNN_MODELS_ROOT_DIR/model_qcs9100.bin"),
    Default("$QNN_MODELS_ROOT_DIR/$SENSE_VOICE_MODEL_NAME");

    fun isQnnModel(): Boolean = this != Default

    companion object {
        fun findByName(name: String): AvailableSoCModel {
            return entries.find { it.name == name } ?: Default
        }
    }
}