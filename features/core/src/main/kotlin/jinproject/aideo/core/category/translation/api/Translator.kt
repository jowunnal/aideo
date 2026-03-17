package jinproject.aideo.core.category.translation.api

import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.inference.translation.api.Translation
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Provider

abstract class Translator(
    private val translationProvider: Map<TranslationAvailableModel, @JvmSuppressWildcards Provider<Translation>>,
    private val localSettingDataSource: LocalSettingDataSource,
) {
    protected var translation: Translation? = null

    @OptIn(FlowPreview::class)
    suspend fun initialize() {
        val translationModel = localSettingDataSource.getSelectedTranslationModel().first()
        val modelType = TranslationAvailableModel.findByName(translationModel)
        if (translation?.isInitialized == true) {
            if (translation!!.availableTranslation == modelType)
                return

            translation!!.release()
        }

        translation = translationProvider[modelType]!!.get().apply {
            initialize()
        }
    }

    fun release() {
        translation?.release()
        translation = null
    }
}