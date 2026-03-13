package jinproject.aideo.core

import android.media.AudioFormat
import android.os.Build
import androidx.core.net.toUri
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment
import com.k2fsa.sherpa.onnx.SpeechSegment
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.diarization.SpeakerDiarization
import jinproject.aideo.core.inference.punctuation.Punctuation
import jinproject.aideo.core.inference.speechRecognition.TimeStampedSR
import jinproject.aideo.core.inference.speechRecognition.api.SpeechRecognition
import jinproject.aideo.core.inference.translation.MlKitTranslation
import jinproject.aideo.core.inference.vad.SileroVad
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.media.MediaFileManager
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.core.media.audio.AudioProcessor.normalizeAudioSample
import jinproject.aideo.core.media.audio.ChunkedAudioProcessor
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.TranslationManager.getSubtitleFileIdentifier
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.ceil
import kotlin.math.floor

class SpeechToTranscription @Inject constructor(
    private val speechRecognitionProviders: Map<SpeechRecognitionAvailableModel, @JvmSuppressWildcards Provider<SpeechRecognition>>,
    private val mediaFileManager: MediaFileManager,
    private val vad: SileroVad,
    private val speakerDiarization: SpeakerDiarization,
    private val punctuation: Punctuation,
    private val localFileDataSource: LocalFileDataSource,
    private val localSettingDataSource: LocalSettingDataSource,
    private val mlKitTranslation: MlKitTranslation,
    private val foregroundObserver: ForegroundObserver,
) {
    private lateinit var speechRecognition: SpeechRecognition
    private var extractedAudioChannel =
        Channel<FloatArray>(capacity = EXTRACTED_AUDIO_CHANNEL_CAPACITY)
    private var inferenceAudioChannel =
        Channel<SingleSpeechSegment>(capacity = INFERENCE_CHANNEL_CAPACITY)
    val inferenceProgress: StateFlow<Float> field: MutableStateFlow<Float> = MutableStateFlow(
        0f
    )

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun initialize() {
        initializeSpeechRecognition()

        vad.initialize()
        speakerDiarization.initialize()

        if (extractedAudioChannel.isClosedForSend)
            extractedAudioChannel = Channel<FloatArray>(capacity = EXTRACTED_AUDIO_CHANNEL_CAPACITY)
        if (inferenceAudioChannel.isClosedForSend)
            inferenceAudioChannel =
                Channel<SingleSpeechSegment>(capacity = INFERENCE_CHANNEL_CAPACITY)
    }

    private suspend fun initializeSpeechRecognition() {
        val model = localSettingDataSource.getSelectedSpeechRecognitionModel().first()
        val modelType = SpeechRecognitionAvailableModel.findByName(model)

        if (::speechRecognition.isInitialized) {
            if (speechRecognition.availableSpeechRecognition == modelType) return
            speechRecognition.release()
        }

        speechRecognition = speechRecognitionProviders[modelType]!!.get().apply {
            initialize()
        }
    }

    suspend fun getPendingInferenceVideoUri(): String =
        localSettingDataSource.getPendingInferenceVideoUri().first()

    fun release() {
        if (::speechRecognition.isInitialized)
            speechRecognition.release()

        vad.release()
        punctuation.release()
        extractedAudioChannel.cancel()
        inferenceAudioChannel.cancel()
        speakerDiarization.release()
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun cancelAndReInitialize() {
        extractedAudioChannel.cancel()
        inferenceAudioChannel.cancel()

        vad.initialize()
        vad.reset()
        speakerDiarization.initialize()

        if (::speechRecognition.isInitialized)
            speechRecognition.resetState()
        initializeSpeechRecognition()

        extractedAudioChannel = Channel(capacity = EXTRACTED_AUDIO_CHANNEL_CAPACITY)
        inferenceAudioChannel = Channel(capacity = INFERENCE_CHANNEL_CAPACITY)
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    suspend fun transcribe(videoUri: String, videoId: Long): Unit =
        withContext(Dispatchers.Default) {
            localSettingDataSource.setPendingInferenceVideoUri(videoUri)

            try {
                val language = localSettingDataSource.getInferenceTargetLanguage().first()

                launch(Dispatchers.IO) {
                    extractAudioFromVideo(videoUri)
                }

                launch {
                    processExtractedAudioWithVad()
                }

                val transcribedSrtText = async {
                    var processedAudioSize = 0

                    for (singleSpeechSegment in inferenceAudioChannel) {
                        processedAudioSize += singleSpeechSegment.vadResult.samples.size

                        transcribeSingleSegment(
                            singleSpeechSegment = singleSpeechSegment,
                            language = language
                        )

                        (mediaFileManager as AndroidMediaFileManager).mediaInfo?.let {
                            val total =
                                AudioConfig.SAMPLE_RATE * it.channelCount * it.encodingBytes * it.duration / Short.SIZE_BYTES

                            inferenceProgress.value =
                                processedAudioSize.toFloat() / (total).toFloat()
                        }
                    }

                    inferenceProgress.value = 1f

                    speechRecognition.getResult()
                }.await()

                if (!foregroundObserver.isForeground) {
                    speechRecognition.release()
                } else {
                    speechRecognition.resetState()
                }

                val punctuatedSrtText =
                    if (punctuation.isAvailableLanguage(language)) {
                        punctuation.initialize()
                        val result = punctuation.addPunctuationOnSrt(transcribedSrtText)
                        if (!foregroundObserver.isForeground) {
                            punctuation.release()
                        }
                        result
                    } else
                        transcribedSrtText

                storeSubtitleFile(
                    subtitleText = punctuatedSrtText,
                    videoItemId = videoId,
                    languageCode = language,
                )
            } finally {
                localSettingDataSource.clearPendingInferenceVideoUri()
            }
        }

    private suspend fun processExtractedAudioWithVad() {
        val chunkedProcessor = ChunkedAudioProcessor(windowSize = 512) { chunk ->
            vad.acceptWaveform(chunk)

            if (vad.isSpeechDetected()) {
                inferenceVadAndSd()
            }
        }

        for (samples in extractedAudioChannel) {
            chunkedProcessor.feed(samples)
        }

        val remainder = chunkedProcessor.flush()
        if (remainder.isNotEmpty()) {
            vad.acceptWaveform(remainder)
        }
        vad.flush()
        inferenceVadAndSd()

        if (!foregroundObserver.isForeground) {
            vad.release()
            speakerDiarization.release()
        }

        inferenceAudioChannel.close()
    }

    private suspend fun inferenceVadAndSd() {
        while (vad.hasSegment()) {
            val nextVadSegment = vad.getNextSegment()
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

            inferenceAudioChannel.send(
                SingleSpeechSegment(
                    vadResult = nextVadSegment,
                    sdResult = sdResult,
                )
            )

            vad.popSegment()
        }
    }

    private suspend fun transcribeSingleSegment(
        singleSpeechSegment: SingleSpeechSegment,
        language: String
    ) {
        val standardTime = singleSpeechSegment.vadResult.start / AudioConfig.SAMPLE_RATE.toFloat()
        (speechRecognition as? TimeStampedSR)?.setStandardTime(standardTime)

        var lastEndIdx = 0
        val sampleSize = singleSpeechSegment.vadResult.samples.size

        singleSpeechSegment.sdResult.forEach { sd ->
            val startIdx = (AudioConfig.SAMPLE_RATE * floor(sd.start * 10) / 10).toInt()
                .coerceIn(lastEndIdx, sampleSize)
            val endIdx = (AudioConfig.SAMPLE_RATE * ceil(sd.end * 10) / 10).toInt()
                .coerceIn(lastEndIdx, sampleSize)
            lastEndIdx = endIdx

            (speechRecognition as? TimeStampedSR)?.setTimes(sd.start, sd.end)
            speechRecognition.transcribe(
                audioData = singleSpeechSegment.vadResult.samples.copyOfRange(
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

    suspend fun storeSubtitleFile(subtitleText: String, videoItemId: Long, languageCode: String) {
        val srcLan = if (languageCode == LanguageCode.Auto.code) {
            val extracted = TranslationManager.extractSubtitleContent(subtitleText)
                .split("@")
                .first { it.isNotBlank() }

            mlKitTranslation.detectLanguage(extracted).code
        } else
            languageCode

        localFileDataSource.createFileAndWriteOnOutputStream(
            fileIdentifier = getSubtitleFileIdentifier(
                id = videoItemId,
                languageCode = srcLan
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

    private class SingleSpeechSegment(
        val vadResult: SpeechSegment,
        val sdResult: Array<OfflineSpeakerDiarizationSegment>,
    )

    companion object {
        const val EXTRACTED_AUDIO_CHANNEL_CAPACITY = 5
        const val INFERENCE_CHANNEL_CAPACITY = 10
    }
}

enum class AvailableSoCModel(val assetSubDir: String) {
    SM8450("sm8450"),
    SM8475("sm8475"),
    SM8550("sm8550"),
    SM8650("sm8650"),
    SM8750("sm8750"),
    SM8850("sm8850"),
    Default("");

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