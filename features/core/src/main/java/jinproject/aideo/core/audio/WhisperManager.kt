package jinproject.aideo.core.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.lite.LiteRT
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
    private val liteRT: LiteRT by lazy { LiteRT(context) }

    var isReady: Boolean by mutableStateOf(false)
        private set

    suspend fun load() {
        copyBinaryDataFromAssets()
        loadBaseModel()
        isReady = true
    }

    private suspend fun copyBinaryDataFromAssets() = withContext(Dispatchers.IO) {
        val models = context.assets.list("models/")

        if (models == null)
            return@withContext

        val modelsPath = File(context.filesDir, "models")

        if (!modelsPath.exists())
            modelsPath.mkdirs()

        val vocab = File(modelsPath, VOCAB_FILE_NAME)
        context.assets.open(VOCAB_FILE_PATH).use { input ->
            vocab.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private suspend fun loadBaseModel() = liteRT.initialize(File(context.filesDir, VOCAB_FILE_PATH).absolutePath)

    /**
     * 오디오 파일을 텍스트로 변환하여 자막 파일(Srt) 생성
     *
     * @param videoItem : videoItem
     */
    suspend fun transcribeAudio(
        videoItem: VideoItem
    ) {
        if (!liteRT.isInitialized) {
            return
        }

        val transcribedText = audioBuffer.processFullAudio(
            videoFileUri = videoItem.uri.toUri(),
            transcribe = liteRT::transcribeLang
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
        liteRT.deInitialize()
        audioBuffer.release()
        isReady = false
    }

    companion object {
        const val VOCAB_FILE_NAME = "new_vocab.bin"
        const val VOCAB_FILE_PATH = "models/$VOCAB_FILE_NAME"
    }
}