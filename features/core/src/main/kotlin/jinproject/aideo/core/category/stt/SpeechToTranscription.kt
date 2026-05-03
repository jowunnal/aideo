package jinproject.aideo.core.category.stt

import android.os.Build
import androidx.core.net.toUri
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationSegment
import com.k2fsa.sherpa.onnx.SpeechSegment
import jinproject.aideo.core.common.ForegroundObserver
import jinproject.aideo.core.inference.ProgressReportable
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.diarization.SpeakerDiarization
import jinproject.aideo.core.inference.punctuation.Punctuation
import jinproject.aideo.core.inference.speechRecognition.TimeStampedSR
import jinproject.aideo.core.inference.speechRecognition.api.SpeechRecognition
import jinproject.aideo.core.inference.translation.MlKitTranslation
import jinproject.aideo.core.inference.vad.SileroVad
import jinproject.aideo.core.media.AndroidMediaFileManager
import jinproject.aideo.core.media.AudioSamplingBit
import jinproject.aideo.core.media.MediaFileManager
import jinproject.aideo.core.media.audio.AudioConfig
import jinproject.aideo.core.media.audio.AudioProcessor.normalizeAudioSample
import jinproject.aideo.core.media.audio.AudioProcessor.normalizeAudioSample8Bit
import jinproject.aideo.core.media.audio.ChunkedAudioProcessor
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.data.SubtitleFileConfig
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
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
) : ProgressReportable {
    private var speechRecognition: SpeechRecognition? = null
    private var extractedAudioChannel =
        Channel<FloatArray>(capacity = EXTRACTED_AUDIO_CHANNEL_CAPACITY)
    private var inferenceAudioChannel =
        Channel<SingleSpeechSegment>(capacity = INFERENCE_CHANNEL_CAPACITY)

    private val _progress: MutableStateFlow<Float> = MutableStateFlow(
        0f
    )
    override val progress: StateFlow<Float> = _progress.asStateFlow()

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun initialize() {
        initializeSpeechRecognition()

        vad.initialize()
        speakerDiarization.initialize()

        if (extractedAudioChannel.isClosedForSend)
            extractedAudioChannel = Channel(capacity = EXTRACTED_AUDIO_CHANNEL_CAPACITY)
        if (inferenceAudioChannel.isClosedForSend)
            inferenceAudioChannel =
                Channel(capacity = INFERENCE_CHANNEL_CAPACITY)
    }

    private suspend fun initializeSpeechRecognition() {
        val model = localSettingDataSource.getSelectedSpeechRecognitionModel().first()
        val modelType = SpeechRecognitionAvailableModel.findByName(model)

        if (speechRecognition?.isInitialized == true) {
            if (speechRecognition?.availableSpeechRecognition == modelType) {
                speechRecognition!!.updateLanguageConfig(
                    localSettingDataSource.getInferenceTargetLanguage().first()
                )

                return
            }
            speechRecognition!!.release()
        }

        speechRecognition = speechRecognitionProviders[modelType]!!.get().apply {
            initialize()
        }
    }

    suspend fun getPendingInferenceVideoUri(): String =
        localSettingDataSource.getPendingInferenceVideoUri().first()

    fun release() {
        speechRecognition?.release()
        speechRecognition = null
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

        if (speechRecognition?.isInitialized == true)
            speechRecognition!!.resetState()
        initializeSpeechRecognition()

        extractedAudioChannel = Channel(capacity = EXTRACTED_AUDIO_CHANNEL_CAPACITY)
        inferenceAudioChannel = Channel(capacity = INFERENCE_CHANNEL_CAPACITY)
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    suspend fun transcribe(videoUri: String, videoId: Long): Unit =
        withContext(Dispatchers.Default) {
            localSettingDataSource.setPendingInferenceVideoUri(videoUri)

            try {
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

                        transcribeSingleSegment(singleSpeechSegment)

                        (mediaFileManager as AndroidMediaFileManager).audioInfo?.let {
                            val total =
                                AudioConfig.SAMPLE_RATE * it.channelCount * it.samplingBit.byte * it.duration / Short.SIZE_BYTES

                            _progress.value =
                                processedAudioSize.toFloat() / (total).toFloat()
                        }
                    }

                    _progress.value = 1f

                    speechRecognition!!.getResult()
                }.await()

                if (!foregroundObserver.isForeground) {
                    speechRecognition!!.release()
                } else {
                    speechRecognition!!.resetState()
                }

                val language = localSettingDataSource.getInferenceTargetLanguage().first()
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
            val seconds = 9
            val sdResult =
                if (nextVadSegment.samples.size <= AudioConfig.SAMPLE_RATE * seconds)
                    speakerDiarization.process(nextVadSegment.samples)
                else {
                    val chunkSize = AudioConfig.SAMPLE_RATE * seconds
                    var sp = 0
                    var ep = chunkSize
                    val arrayList = mutableListOf<OfflineSpeakerDiarizationSegment>()
                    val totalSize = nextVadSegment.samples.size
                    while (sp < totalSize) {
                        val chunkEnd = minOf(ep, totalSize)
                        val timeOffset = sp.toFloat() / AudioConfig.SAMPLE_RATE

                        arrayList.addAll(
                            speakerDiarization.process(
                                nextVadSegment.samples.copyOfRange(sp, chunkEnd)
                            ).map { segment ->
                                OfflineSpeakerDiarizationSegment(
                                    speaker = segment.speaker,
                                    start = segment.start + timeOffset,
                                    end = segment.end + timeOffset,
                                )
                            }
                        )

                        sp = ep
                        ep += chunkSize
                    }

                    arrayList.toTypedArray()
                }

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
        singleSpeechSegment: SingleSpeechSegment
    ) {
        val standardTime = singleSpeechSegment.vadResult.start / AudioConfig.SAMPLE_RATE.toFloat()
        (speechRecognition as? TimeStampedSR)?.setStandardTime(standardTime)

        val sampleSize = singleSpeechSegment.vadResult.samples.size
        val sortedSdResult = singleSpeechSegment.sdResult.sortedBy { it.start }

        if (sortedSdResult.isEmpty()) {
            speechRecognition!!.transcribe(singleSpeechSegment.vadResult.samples)
            return
        }

        var pendingStartIdx = 0
        var lastCoveredEndIdx = 0

        sortedSdResult.forEachIndexed { index, sd ->
            val diarizationStartIdx = (AudioConfig.SAMPLE_RATE * floor(sd.start * 10) / 10).toInt()
                .coerceIn(0, sampleSize)
            val diarizationEndIdx = (AudioConfig.SAMPLE_RATE * ceil(sd.end * 10) / 10).toInt()
                .coerceIn(0, sampleSize)
            val startIdx = minOf(pendingStartIdx, diarizationStartIdx)
            var endIdx = maxOf(lastCoveredEndIdx, diarizationEndIdx)

            if (index == sortedSdResult.lastIndex) {
                endIdx = sampleSize
            }

            lastCoveredEndIdx = maxOf(lastCoveredEndIdx, endIdx)
            pendingStartIdx = lastCoveredEndIdx

            (speechRecognition as? TimeStampedSR)?.setTimes(sd.start, sd.end)

            if (startIdx >= endIdx)
                return@forEachIndexed

            yield()

            speechRecognition!!.transcribe(
                audioData = singleSpeechSegment.vadResult.samples.copyOfRange(
                    startIdx,
                    endIdx
                )
            )
        }
    }

    private suspend fun extractAudioFromVideo(videoUri: String) {
        mediaFileManager.extractAudioData(
            videoContentUri = videoUri.toUri(),
            extractedAudioChannel = extractedAudioChannel,
            audioPreProcessor = { audioInfo ->
                val sampleRate = audioInfo.sampleRate

                when (val samplingBit = audioInfo.samplingBit) {
                    is AudioSamplingBit.PCM8Bit -> normalizeAudioSample8Bit(
                        samplingBit.data,
                        sampleRate
                    )

                    is AudioSamplingBit.PCM16Bit -> normalizeAudioSample(
                        samplingBit.data,
                        sampleRate
                    )

                    is AudioSamplingBit.PCM32Bit -> normalizeAudioSample(
                        samplingBit.data,
                        sampleRate
                    )
                }
            }
        )
    }

    suspend fun storeSubtitleFile(subtitleText: String, videoItemId: Long, languageCode: String) {
        val srcLan = if (languageCode == LanguageCode.Auto.code) {
            val extracted = extractDetectableSubtitleContent(
                content = TranslationManager.extractSubtitleContent(subtitleText),
                availableModel = speechRecognition?.availableSpeechRecognition
                    ?: throw IllegalStateException("Speech recognition is not initialized")
            )

            mlKitTranslation.detectLanguage(extracted).code
        } else
            languageCode

        localFileDataSource.createFileAndWriteOnOutputStream(
            fileIdentifier = SubtitleFileConfig.toSubtitleFileIdentifier(
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

    private fun extractDetectableSubtitleContent(
        content: String,
        availableModel: SpeechRecognitionAvailableModel
    ): String {
        val detectableLanguageRegex = createDetectableLanguageRegex(availableModel)

        return content.split("@")
            .firstOrNull { token ->
                token.isNotBlank() && detectableLanguageRegex.containsMatchIn(token)
            } ?: throw UnsupportedTranscriptionLanguageException()
    }

    private fun createDetectableLanguageRegex(
        availableModel: SpeechRecognitionAvailableModel
    ): Regex {
        val pattern = LanguageCode.getLanguageCodesByAvailableModel(availableModel)
            .mapNotNull(::getCharacterPatternByLanguageCode)
            .distinct()
            .joinToString(separator = "|")

        return Regex(pattern)
    }

    private fun getCharacterPatternByLanguageCode(languageCode: LanguageCode): String? =
        when (languageCode) {
            LanguageCode.Auto -> null
            LanguageCode.Korean -> "\\p{IsHangul}"
            LanguageCode.English,
            LanguageCode.German,
            LanguageCode.Indonesian,
            LanguageCode.French,
            LanguageCode.Spanish -> "\\p{IsLatin}"

            LanguageCode.Japanese -> "[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]"
            LanguageCode.Chinese,
            LanguageCode.Cantonese -> "\\p{IsHan}"

            LanguageCode.Russian -> "\\p{IsCyrillic}"
            LanguageCode.Hindi -> "\\p{IsDevanagari}"
        }

    class UnsupportedTranscriptionLanguageException :
        IllegalStateException("Unsupported transcription language")

    companion object {
        const val EXTRACTED_AUDIO_CHANNEL_CAPACITY = 10
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
