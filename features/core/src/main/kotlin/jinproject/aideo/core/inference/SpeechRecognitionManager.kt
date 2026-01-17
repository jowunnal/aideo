package jinproject.aideo.core.inference

import jinproject.aideo.core.media.VideoItem
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.TranslationManager.getSubtitleFileIdentifier
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import kotlinx.coroutines.flow.StateFlow

abstract class SpeechRecognitionManager(
    private val localFileDataSource: LocalFileDataSource,
) {
    var isReady: Boolean = false

    /**
     * 0 ~ 1f
     */
    abstract val inferenceProgress: StateFlow<Float>

    abstract fun initialize()
    abstract fun release()

    abstract suspend fun transcribe(videoItem: VideoItem, language: String)

    protected suspend fun storeSubtitleFile(subtitle: String, videoItemId: Long) {
        val languageCode = TranslationManager.detectLanguage(subtitle)

        localFileDataSource.createFileAndWriteOnOutputStream(
            fileIdentifier = getSubtitleFileIdentifier(
                id = videoItemId,
                languageCode = languageCode
            ),
            writeContentOnFile = { outputStream ->
                runCatching {
                    outputStream.bufferedWriter().use { writer ->
                        writer.write(subtitle)
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