package jinproject.aideo.core.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.whispertflite.engine.WhisperEngine
import com.whispertflite.engine.WhisperEngineNative
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.repository.impl.getSubtitleFileIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class WhisperManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
    private val audioBuffer: AudioBuffer,
) {
    private val whisperContext: WhisperEngine by lazy { WhisperEngineNative() }

    var isReady: Boolean by mutableStateOf(false)
        private set

    suspend fun load() {
        copyWeightBinaryData()
        loadBaseModel()
        isReady = true
    }

    private suspend fun copyWeightBinaryData() = withContext(Dispatchers.IO) {
        val models = context.assets.list("models/")

        if (models == null)
            return@withContext

        val modelsPath = File(context.filesDir, "models")

        if (!modelsPath.exists())
            modelsPath.mkdirs()

        val destination = File(modelsPath, WEIGHT_FILE_NAME)
        context.assets.open(WEIGHT_FILE_PATH).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        val vocab = File(modelsPath, VOCAB_FILE_NAME)
        context.assets.open(VOCAB_FILE_PATH).use { input ->
            vocab.outputStream().use { output ->
                input.copyTo(output)
            }
        }


    }

    private suspend fun loadBaseModel() = withContext(Dispatchers.IO) {
        val modelsPath = File(context.filesDir, WEIGHT_FILE_PATH).absolutePath
        val vocabPath = File(context.filesDir, VOCAB_FILE_PATH).absolutePath

        whisperContext.initialize(modelsPath, vocabPath, true)
    }

    /**
     * 오디오 파일을 텍스트로 변환하여 자막 파일(Srt) 생성
     *
     * @param videoItem : videoItem
     */
    suspend fun transcribeAudio(
        videoItem: VideoItem
    ) {
        if (!whisperContext.isInitialized) {
            return
        }

        val transcribedText = audioBuffer.processFullAudio(
            videoFileUri = videoItem.uri.toUri(),
            transcribe = whisperContext::transcribeBufferByTime
        )
        //Log.d("test", "translatedText : $transcribedText")
        val languageCode = TranslationManager.detectLanguage(transcribedText)
        //Log.d("test", "languageCode : $languageCode")

        localFileDataSource.createFileAndWriteOnOutputStream(
            fileIdentifier = getSubtitleFileIdentifier(
                id = videoItem.id,
                languageCode = languageCode
            ),
            writeContentOnFile = { outputStream ->
                runCatching {
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(transcribedText)
                    }
                }.map {
                    true
                }.getOrElse {
                    false
                }
            }
        )
    }

    fun release() {
        whisperContext.deinitialize()
        audioBuffer.release()
        isReady = false
    }

    companion object {
        const val WEIGHT_FILE_NAME = "whisper-tiny.tflite"
        const val WEIGHT_FILE_PATH = "models/$WEIGHT_FILE_NAME"
        const val VOCAB_FILE_NAME = "filters_vocab_multilingual.bin"
        const val VOCAB_FILE_PATH = "models/$VOCAB_FILE_NAME"
    }
}