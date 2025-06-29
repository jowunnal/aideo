package jinproject.aideo.data.repository.impl

import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.datasource.remote.RemoteGCPDataSource
import jinproject.aideo.data.datasource.remote.model.request.DetectLanguageRequest
import jinproject.aideo.data.datasource.remote.model.request.TranslationRequest
import jinproject.aideo.data.repository.GalleryRepository
import kotlinx.coroutines.flow.first
import kotlin.math.abs

class GalleryRepositoryImpl(
    private val remoteGCPDataSource: RemoteGCPDataSource,
    private val localFileDataSource: LocalFileDataSource,
    private val localPlayerDataSource: LocalPlayerDataSource,
) : GalleryRepository {
    override suspend fun translateSubtitle(audioFileAbsolutePath: String) {
        val audioFileContent = localFileDataSource.getFileContent(audioFileAbsolutePath)
            ?: throw GalleryRepository.TranscriptionException.TranscriptionFailed(
                message = "audio file content is null",
                cause = null
            )
        val sourceLanguageCode = remoteGCPDataSource.detectLanguage(
            DetectLanguageRequest(
                content = audioFileContent.getOrNull(1)
                    ?: throw GalleryRepository.TranscriptionException.TranscriptionFailed(
                        message = "audio file content size < 1",
                        cause = null,
                    )
            )
        ).languages.first().languageCode
        val targetLanguageCode = localPlayerDataSource.getLanguageSetting().first()

        remoteGCPDataSource.translateText(
            TranslationRequest(
                sourceLanguageCode = sourceLanguageCode,
                targetLanguageCode = targetLanguageCode,
                contents = audioFileContent,
            )
        )

    }
}