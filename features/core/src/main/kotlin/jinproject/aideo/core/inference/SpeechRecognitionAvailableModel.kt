package jinproject.aideo.core.inference

enum class SpeechRecognitionAvailableModel {
    Whisper,
    SenseVoice;

    companion object {
        fun findByName(name: String): SpeechRecognitionAvailableModel = entries.find { it.name == name } ?: SenseVoice
    }
}