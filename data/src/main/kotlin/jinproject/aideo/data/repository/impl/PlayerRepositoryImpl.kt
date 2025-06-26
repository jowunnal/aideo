package jinproject.aideo.data.repository.impl

import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.datasource.remote.RemoteGCPDataSource
import jinproject.aideo.data.datasource.remote.model.request.TranslationRequest
import jinproject.aideo.data.datasource.remote.model.response.TranslationResponse
import jinproject.aideo.data.repository.PlayerRepository

class PlayerRepositoryImpl(
    private val remoteGCPDataSource: RemoteGCPDataSource,
    private val localPlayerDataSource: LocalPlayerDataSource,
): PlayerRepository {

    override suspend fun translate(translationRequest: TranslationRequest): TranslationResponse =
        remoteGCPDataSource.translateText(translationRequest)
}