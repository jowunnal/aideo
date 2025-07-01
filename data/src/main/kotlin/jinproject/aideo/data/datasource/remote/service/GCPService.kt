package jinproject.aideo.data.datasource.remote.service

import jinproject.aideo.data.BuildConfig
import jinproject.aideo.data.datasource.remote.model.request.DetectLanguageRequest
import jinproject.aideo.data.datasource.remote.model.request.TranslationRequest
import jinproject.aideo.data.datasource.remote.model.response.DetectLanguageResponse
import jinproject.aideo.data.datasource.remote.model.response.TranslationResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface GCPService {
    @POST("projects/{projectId}:translateText")
    suspend fun translateText(
        //@Path("projectId") projectId: String = BuildConfig.PROJECT_ID,
        @Body request: TranslationRequest
    ): TranslationResponse

    @POST("projects/{projectId}:detectLanguage")
    suspend fun detectLanguage(
        //@Path("projectId") projectId: String = BuildConfig.PROJECT_ID,
        @Body request: DetectLanguageRequest
    ): DetectLanguageResponse
}