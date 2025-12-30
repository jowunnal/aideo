package jinproject.aideo.core.runtime.impl

enum class LanguageToken(val languageCode: String, val tokenId: Int) {
    ENGLISH("en", 50259),
    CHINESE("zh", 50260),
    KOREAN("ko", 50264),
    JAPANESE("ja", 50266);

    companion object {
        fun getIdForLanguage(languageCode: String): Int? =
            entries.find { it.languageCode == languageCode }?.tokenId
    }
}