package jinproject.aideo.core.inference.translation.api

import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.utils.LanguageCode

abstract class Translation {
    var isInitialized = false
    open fun initialize() {
        if(isInitialized)
            return
    }
    open fun release() {
        if(!isInitialized)
            return
    }
    abstract val availableTranslation: TranslationAvailableModel

    abstract suspend fun translate(
        text: String,
        srcLang: LanguageCode,
        tgtLang: LanguageCode,
        maxLength: Int = 200
    ): String
}