package jinproject.aideo.data.repository

import jinproject.aideo.data.datasource.remote.model.request.TranslationRequest
import jinproject.aideo.data.datasource.remote.model.response.TranslationResponse

interface PlayerRepository {


    suspend fun translate(translationRequest: TranslationRequest): TranslationResponse

}