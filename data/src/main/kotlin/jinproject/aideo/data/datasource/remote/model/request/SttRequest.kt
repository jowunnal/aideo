package jinproject.aideo.data.datasource.remote.model.request

data class RecognitionConfig(
    val encoding: String? = null,
    val sampleRateHertz: Int? = null,
    val audioChannelCount: Int? = null,
    val enableSeparateRecognitionPerChannel: Boolean? = null,
    val languageCode: String,
    val alternativeLanguageCodes: List<String>? = null,
    val maxAlternatives: Int? = null,
    val profanityFilter: Boolean? = null,
    val adaptation: SpeechAdaptation? = null,
    val speechContexts: List<SpeechContext>? = null,
    val enableWordTimeOffsets: Boolean? = null,
    val enableWordConfidence: Boolean? = null,
    val enableAutomaticPunctuation: Boolean? = null,
    val enableSpokenPunctuation: Boolean? = null,
    val enableSpokenEmojis: Boolean? = null,
    val diarizationConfig: SpeakerDiarizationConfig? = null,
    val metadata: RecognitionMetadata? = null,
    val model: String? = null,
    val useEnhanced: Boolean? = null
)

data class SpeechAdaptation(
    val phraseSets: List<PhraseSet>? = null,
    val phraseSetReferences: List<String>? = null,
    val customClasses: List<CustomClass>? = null,
    val abnfGrammar: ABNFGrammar? = null
)

data class PhraseSet(
    val phrases: List<Phrase>? = null,
    val boost: Float? = null
)

data class Phrase(
    val value: String,
    val boost: Float? = null
)

data class CustomClass(
    val customClassId: String? = null,
    val items: List<ClassItem>? = null
)

data class ClassItem(
    val value: String? = null
)

data class ABNFGrammar(
    val abnfStrings: List<String>? = null
)

data class SpeechContext(
    val phrases: List<String>? = null,
    val boost: Float? = null
)

data class SpeakerDiarizationConfig(
    val enableSpeakerDiarization: Boolean? = null,
    val minSpeakerCount: Int? = null,
    val maxSpeakerCount: Int? = null
)

data class RecognitionMetadata(
    val interactionType: String? = null, // Enum
    val industryNaicsCodeOfAudio: Long? = null,
    val microphoneDistance: String? = null, // Enum
    val originalMediaType: String? = null, // Enum
    val recordingDeviceType: String? = null, // Enum
    val recordingDeviceName: String? = null,
    val originalMimeType: String? = null,
    val audioTopic: String? = null
)

data class RecognitionAudio(
    val content: String? = null, // base64 인코딩 오디오 데이터
    val uri: String? = null      // "gs://bucket/audio.flac" 등 GCS URI
)