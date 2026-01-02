package jinproject.aideo.core.inference.whisper

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.media.ExtractedAudioInfo
import jinproject.aideo.core.media.MediaFileManager
import jinproject.aideo.core.media.MediaInfo
import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.core.runtime.api.SpeechToText
import jinproject.aideo.core.runtime.impl.executorch.ExecutorchSTT
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.repository.impl.getSubtitleFileIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Whisper AI 모델의 전체 프로세스를 처리하는 Controller 클래스
 */
class WhisperManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
    private val mediaFileManager: MediaFileManager,
    @ExecutorchSTT private val sTTRuntime: SpeechToText,
    private val whisperAudioProcessor: WhisperAudioProcessor,
) {
    var isReady: Boolean by mutableStateOf(false)
        private set

    private val extractedAudioChannel =
        Channel<ExtractedAudioInfo>(capacity = Channel.BUFFERED)
    private val inferenceAudioChannel = Channel<FloatArray>(capacity = Channel.BUFFERED)

    suspend fun load() {
        withContext(Dispatchers.IO) {
            copyBinaryDataFromAssets()
            loadBaseModel()
        }
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
        sTTRuntime.initialize(File(context.filesDir, VOCAB_FILE_PATH).absolutePath)
    }

    /**
     * [오디오 추출 - 정규화 - 추론 - 후처리 - 자막파일 저장] 까지의 전체 프로세스를 처리
     *
     * @param videoItem : videoItem
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun transcribeAudio(
        videoItem: VideoItem,
        languageCode: String,
    ) {
        withContext(Dispatchers.IO) {

            /**
             * 미디어로 부터 음성 트랙을 추출
             */
            launch {
                mediaFileManager.extractAudioData(
                    videoContentUri = videoItem.uri.toUri(),
                    extractedAudioChannel = inferenceAudioChannel,
                    audioPreProcessor = {
                        //TODO Whisper 전처리기 통합
                        floatArrayOf()
                    }
                )
            }

            /**
             *  Whisper 모델의 입력에 맞게 오디오 데이터를 전처리
             *
             * 1. MediaCodec 으로 비디오 파일에서 음성 트랙을 추출 및 디코딩
             * 2. 디코드 된 오디오(Byte)를 16kz sample rate, 모노 채널로 reSampling 후, float32 타입으로 정규화
             * 3. Whisper 모델의 입력에 맞게 전처리된 floatArray 를 추론 및 후처리 코루틴으로 전송
             */
            val producerJob = launch(Dispatchers.Default) {
                var mediaInfo: MediaInfo? = null

                for(extractedAudio in extractedAudioChannel) {
                    if (mediaInfo == null)
                        mediaInfo = extractedAudio.mediaInfo

                    whisperAudioProcessor.preProcessing(
                        extractedAudioInfo = extractedAudio,
                        inferenceAudioChannel = inferenceAudioChannel,
                    )
                }

                if (whisperAudioProcessor.lastProducedAudioIndex != 0) {
                    mediaInfo?.let {
                        whisperAudioProcessor.clearSampledAudio(
                            inferenceAudioChannel = inferenceAudioChannel,
                            mediaFormat = it
                        )
                    }
                }

                inferenceAudioChannel.close()
            }

            /**
             * 전처리된 오디오 데이터로 추론 후 Srt 자막 포맷으로 후처리
             *
             * 1. audioBuffer 로 전송된 샘플링 및 정규화된 오디오 데이터를 수신
             * 2. 오디오 데이터로 추론 실행
             * 3. 추론 결과를 후처리(Srt 포맷의 텍스트)
             */
            val srtText = async(Dispatchers.Default) {
                for(inferenceAudio in inferenceAudioChannel) {
                    sTTRuntime.transcribe(
                        audioData = inferenceAudio,
                    )
                }

                sTTRuntime.getResult()
            }.await()

            val languageCode = TranslationManager.detectLanguage(srtText)

            localFileDataSource.createFileAndWriteOnOutputStream(
                fileIdentifier = getSubtitleFileIdentifier(
                    id = videoItem.id,
                    languageCode = languageCode
                ),
                writeContentOnFile = { outputStream ->
                    runCatching {
                        outputStream.bufferedWriter().use { writer ->
                            writer.write(srtText)
                        }
                    }.map {
                        true
                    }.getOrElse {
                        false
                    }
                }
            )
        }
    }

    fun release() {
        sTTRuntime.release()
        isReady = false
    }

    companion object {
        const val VOCAB_FILE_NAME = "filters_vocab_multilingual.bin"
        const val VOCAB_FILE_PATH = "models/$VOCAB_FILE_NAME"
        const val MODEL_FILE_NAME = "model.pte"
        const val MODEL_FILE_PATH = "models/$MODEL_FILE_NAME"
    }
}