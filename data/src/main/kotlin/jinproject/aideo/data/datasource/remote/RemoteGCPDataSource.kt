package jinproject.aideo.data.datasource.remote

import jinproject.aideo.data.datasource.remote.model.request.TranslationRequest
import jinproject.aideo.data.datasource.remote.model.response.TranslationResponse
import jinproject.aideo.data.datasource.remote.service.GCPService
import javax.inject.Inject

class RemoteGCPDataSource @Inject constructor(
    private val gcpService: GCPService
) {

    suspend fun translateText(
        request: TranslationRequest
    ): TranslationResponse {
        return gcpService.translateText(request)
    }
}