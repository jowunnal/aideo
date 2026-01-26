package jinproject.aideo.core

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioFormat
import android.os.Build
import androidx.core.net.toUri
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment
import com.k2fsa.sherpa.onnx.SpeechSegment
import com.k2fsa.sherpa.onnx.WaveReader
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.AvailableSoCModel.Companion.getAvailableSoCModel
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.diarization.SpeakerDiarization
import jinproject.aideo.core.inference.punctuation.Punctuation
import jinproject.aideo.core.inference.speechRecognition.SenseVoice
import jinproject.aideo.core.inference.speechRecognition.TimeStampedSR
import jinproject.aideo.core.inference.speechRecognition.Whisper
import jinproject.aideo.core.inference.speechRecognition.api.SpeechRecognition
import jinproject.aideo.core.inference.vad.SileroVad
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.media.MediaFileManager
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.core.media.audio.AudioProcessor.normalizeAudioSample
import jinproject.aideo.data.TranslationManager.getSubtitleFileIdentifier
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor

class SpeechToTranscription @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaFileManager: MediaFileManager,
    private val vad: SileroVad,
    private val speakerDiarization: SpeakerDiarization,
    private val punctuation: Punctuation,
    private val localFileDataSource: LocalFileDataSource,
    private val localSettingDataSource: LocalSettingDataSource,
) {
    private lateinit var speechRecognition: SpeechRecognition
    private val extractedAudioChannel = Channel<FloatArray>(capacity = Channel.BUFFERED)
    private val inferenceAudioChannel = Channel<FloatArray>(capacity = Channel.BUFFERED)

    val inferenceProgress: StateFlow<Float> field: MutableStateFlow<Float> = MutableStateFlow(
        0f
    )
    var isReady: Boolean = false
        private set

    suspend fun initialize() {
        isReady = true

        val model = localSettingDataSource.getSelectedSpeechRecognitionModel().first()
        speechRecognition = when (SpeechRecognitionAvailableModel.findByName(model)) {
            SpeechRecognitionAvailableModel.Whisper -> Whisper(context)
            SpeechRecognitionAvailableModel.SenseVoice -> SenseVoice(context).apply {
                setQnn(getAvailableSoCModel().isQnnModel())
            }
        }.apply {
            initialize()
        }

        vad.initialize()
        speakerDiarization.initialize()
    }

    fun release() {
        if (::speechRecognition.isInitialized)
            speechRecognition.release()

        vad.release()
        punctuation.release()
        extractedAudioChannel.cancel()
        inferenceAudioChannel.cancel()
        isReady = false
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    suspend fun transcribe(videoItem: VideoItem) {
        withContext(Dispatchers.Default) {
            val language = localSettingDataSource.getInferenceTargetLanguage().first()

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

                speechRecognition.getResult()
            }.await()

            val punctuatedSrtText =
                if (punctuation.isAvailableLanguage(language)) {
                    punctuation.initialize()
                    punctuation.addPunctuationOnSrt(transcribedSrtText)
                } else
                    transcribedSrtText

            storeSubtitleFile(
                subtitleText = punctuatedSrtText,
                videoItemId = videoItem.id,
                languageCode = language,
            )

            inferenceProgress.value = 1f
            vad.reset()
        }
    }

    private suspend fun transcribeSingleSegment(nextVadSegment: SpeechSegment, language: String) {
        val standardTime =
            nextVadSegment.start / AudioConfig.SAMPLE_RATE.toFloat()

        val sdResult =
            if (nextVadSegment.samples.size <= AudioConfig.SAMPLE_RATE * 10)
                speakerDiarization.process(nextVadSegment.samples)
            else
                arrayOf(
                    OfflineSpeakerDiarizationSegment(
                        speaker = 0,
                        start = 0f,
                        end = 10f
                    )
                )

        (speechRecognition as? TimeStampedSR)?.setStandardTime(standardTime)

        var lastEndIdx = 0

        sdResult.forEach { sd ->
            val startIdx = (AudioConfig.SAMPLE_RATE * floor(sd.start * 10) / 10).toInt()
                .coerceIn(lastEndIdx, nextVadSegment.samples.size)
            val endIdx = (AudioConfig.SAMPLE_RATE * ceil(sd.end * 10) / 10).toInt()
                .coerceIn(lastEndIdx, nextVadSegment.samples.size)
            lastEndIdx = endIdx

            (speechRecognition as? TimeStampedSR)?.setTimes(sd.start, sd.end)
            speechRecognition.transcribe(
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

    fun storeSubtitleFile(subtitleText: String, videoItemId: Long, languageCode: String) {
        localFileDataSource.createFileAndWriteOnOutputStream(
            fileIdentifier = getSubtitleFileIdentifier(
                id = videoItemId,
                languageCode = languageCode
            ),
            writeContentOnFile = { outputStream ->
                runCatching {
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(subtitleText)
                    }
                }.map {
                    true
                }.getOrElse {
                    false
                }
            }
        )
    }

    private suspend fun extractTestAudioFile(assetManager: AssetManager) {
        val (samples, sampleRate) = WaveReader.readWave(
            assetManager = assetManager,
            filename = "ja.wav"
        )

        extractedAudioChannel.send(samples)
        extractedAudioChannel.close()
    }
}

private class FixedChunkBuffer(private val chunkSize: Int = 512) {
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

enum class AvailableSoCModel {
    SM8450,
    SM8475,
    SM8550,
    SM8650,
    SM8750,
    SM8850,
    QCS9100,
    Default;

    fun isQnnModel(): Boolean = this != Default

    companion object {
        fun findByName(name: String): AvailableSoCModel {
            return entries.find { it.name == name } ?: Default
        }

        fun getAvailableSoCModel(): AvailableSoCModel {
            return if (Build.VERSION.SDK_INT >= 31)
                AvailableSoCModel.findByName(Build.SOC_MODEL.uppercase())
            else
                AvailableSoCModel.findByName(Build.HARDWARE.uppercase())

        }
    }
}