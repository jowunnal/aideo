package jinproject.aideo.core.inference.translation

enum class TranslationAvailableModel {
    MlKit,
    M2M100;

    companion object {
        fun findByName(name: String): TranslationAvailableModel = entries.find {
            it.name.equals(name, ignoreCase = true)
        } ?: MlKit
    }
}