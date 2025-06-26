package jinproject.aideo.data.datasource.remote.model.response

data class TranslationResponse(
    val translations: List<Translation>? = null
)

data class Translation(
    val translatedText: String? = null,
    val detectedLanguageCode: String? = null,
    val detectedConfidence: Float? = null
) 