package jinproject.aideo.data.datasource.remote.service

import jinproject.aideo.data.datasource.remote.model.request.TranslationRequest
import jinproject.aideo.data.datasource.remote.model.response.TranslationResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface GCPService {
    @POST("/v3/projects/{projectId}:translateText")
    suspend fun translateText(
        @Body request: TranslationRequest
    ): TranslationResponse
}