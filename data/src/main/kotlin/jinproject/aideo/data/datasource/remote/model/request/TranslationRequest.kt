package jinproject.aideo.data.datasource.remote.model.request

data class TranslationRequest(
    val sourceLanguageCode: String,
    val targetLanguageCode: String,
    val contents: List<String>,
    val mimeType: String = "text/plain"
) 