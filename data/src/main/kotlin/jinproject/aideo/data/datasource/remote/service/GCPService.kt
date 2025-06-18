package jinproject.aideo.data.datasource.remote.service

import jinproject.aideo.data.datasource.remote.model.request.RecognitionAudio
import jinproject.aideo.data.datasource.remote.model.request.RecognitionConfig
import jinproject.aideo.data.datasource.remote.model.response.SpeechRecognizeResponse
import retrofit2.Response
import retrofit2.http.POST

interface GCPService {
    @POST("/v1/speech:recognize")
    suspend fun recognizeSpeech(
        config: RecognitionConfig,
        audio: RecognitionAudio
    ): Response<SpeechRecognizeResponse>
}