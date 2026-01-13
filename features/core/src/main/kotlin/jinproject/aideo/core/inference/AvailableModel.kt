package jinproject.aideo.core.inference

enum class AvailableModel {
    Whisper,
    SenseVoice;

    companion object {
        fun findByName(name: String): AvailableModel = entries.find { it.name == name } ?: SenseVoice
    }
}