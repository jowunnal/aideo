package jinproject.aideo.data.datasource.remote.model.response

data class TranslationResponse(
    val translations: List<Translation>,
)

data class Translation(
    val translatedText: String,
    val detectedLanguageCode: String,
    val detectedConfidence: Float
) 