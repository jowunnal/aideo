package jinproject.aideo.core.inference.translation.api

import jinproject.aideo.core.utils.LanguageCode

abstract class Translation {
    abstract fun initialize()
    abstract fun release()

    abstract suspend fun translate(
        text: String,
        srcLang: LanguageCode,
        tgtLang: LanguageCode,
        maxLength: Int = 200
    ): String

    abstract suspend fun translateSubtitle(videoId: Long)
}