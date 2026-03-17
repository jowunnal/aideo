package jinproject.aideo.core.category.translation

import jinproject.aideo.core.category.translation.api.Translator
import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.inference.translation.api.Translation
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider

class NormalTranslator @Inject constructor(
    translationProvider: Map<TranslationAvailableModel, @JvmSuppressWildcards Provider<Translation>>,
    localSettingDataSource: LocalSettingDataSource,
) : Translator(
    translationProvider = translationProvider,
    localSettingDataSource = localSettingDataSource
) {
    suspend fun translate(sourceText: String, srcLang: LanguageCode): String {
        initialize()

        val targetLanguageCode = LanguageCode.findByCode(Locale.getDefault().language)!!

        if (srcLang == targetLanguageCode)
            return sourceText

        val transcribedText = translation!!.translate(
            text = sourceText,
            srcLang = srcLang,
            tgtLang = targetLanguageCode,
        )

        return transcribedText
    }
}