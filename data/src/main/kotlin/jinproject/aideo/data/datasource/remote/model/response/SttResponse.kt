package jinproject.aideo.data.datasource.remote.model.response

data class SpeechRecognizeResponse(
    val results: List<SpeechRecognitionResult>? = null,
    val totalBilledTime: String? = null,
    val speechAdaptationInfo: SpeechAdaptationInfo? = null,
    val requestId: String? = null
)

data class SpeechRecognitionResult(
    val alternatives: List<SpeechRecognitionAlternative>? = null,
    val channelTag: Int? = null,
    val resultEndTime: String? = null,
    val languageCode: String? = null
)

data class SpeechRecognitionAlternative(
    val transcript: String? = null,
    val confidence: Float? = null,
    val words: List<WordInfo>? = null
)

data class WordInfo(
    val startTime: String? = null,
    val endTime: String? = null,
    val word: String? = null,
    val confidence: Float? = null,
    val speakerTag: Int? = null
)

data class SpeechAdaptationInfo(
    val adaptationTimeout: Boolean? = null,
    val timeoutMessage: String? = null
)
