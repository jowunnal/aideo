package jinproject.aideo.core.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.lite.LiteRTManager
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class WhisperManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
) {
    private val liteRTManager: LiteRTManager by lazy {
        LiteRTManager(
            context = context,
            localFileDataSource = localFileDataSource
        )
    }

    var isReady: Boolean by mutableStateOf(false)
        private set

    suspend fun load() {
        withContext(Dispatchers.IO) {
            copyBinaryDataFromAssets()
            loadBaseModel()
        }
        isReady = true
    }

    private fun copyBinaryDataFromAssets() {
        val models = context.assets.list("models/")

        if (models == null)
            return

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

    private fun loadBaseModel() {
        liteRTManager.initialize(File(context.filesDir, VOCAB_FILE_PATH).absolutePath)
    }

    /**
     * 오디오 파일을 텍스트로 변환하여 자막 파일(Srt) 생성
     *
     * @param videoItem : videoItem
     */
    suspend fun transcribeAudio(
        videoItem: VideoItem
    ) {
        if (!liteRTManager.isInitialized) {
            return
        }

        val transcribedText = liteRTManager.transcribe(videoItem.uri)

        val languageCode = TranslationManager.detectLanguage(transcribedText)

        /*
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
        )*/
    }

    fun release() {
        liteRTManager.deInitialize()
        isReady = false
    }

    companion object {
        const val VOCAB_FILE_NAME = "filters_vocab_multilingual.bin"
        const val VOCAB_FILE_PATH = "models/$VOCAB_FILE_NAME"
    }
}