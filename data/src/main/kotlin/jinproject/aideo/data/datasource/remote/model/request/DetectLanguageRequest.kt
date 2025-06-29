package jinproject.aideo.data.datasource.remote.model.request

data class DetectLanguageRequest(
    val model: String? = null,
    val mimeType: String = "text/plain",
    val labels: Map<String, String>? = null,
    val content: String
)