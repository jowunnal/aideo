package jinproject.aideo.data.datasource.remote.model.response

data class DetectLanguageResponse(
    val languages: List<DetectedLanguage>,
)

data class DetectedLanguage(
    val languageCode: String,
    val confidence: Float,
)
