package jinproject.aideo.data.repository.impl

import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.remote.RemoteGCPDataSource
import jinproject.aideo.data.datasource.remote.model.request.TranslationRequest
import jinproject.aideo.data.repository.GalleryRepository
import kotlin.math.abs

class GalleryRepositoryImpl(
    private val remoteGCPDataSource: RemoteGCPDataSource,
    private val localFileDataSource: LocalFileDataSource,
): GalleryRepository {
    override suspend fun translateSubtitle(audioFileAbsolutePath: String): Boolean {
        val audioFileContent = localFileDataSource.getFileContent(audioFileAbsolutePath)
        remoteGCPDataSource.translateText(
            TranslationRequest(
                sourceLanguageCode = ,
                targetLanguageCode = ,
                contents = audioFileContent,
                mimeType = "text/plain",
            )
        )

    }
}