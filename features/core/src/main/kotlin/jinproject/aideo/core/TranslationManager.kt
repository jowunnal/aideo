package jinproject.aideo.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import jinproject.aideo.core.inference.translation.M2M100
import jinproject.aideo.core.inference.translation.MlKitTranslation
import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.inference.translation.api.Translation
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

class TranslationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val localFileDataSource: LocalFileDataSource,
    private val localSettingDataSource: LocalSettingDataSource,
) {
    private lateinit var translation: Translation
    var isInitialized: Boolean = false
        private set

    suspend fun initialize() {
        if(isInitialized)
            return

        val translationModel = localSettingDataSource.getSelectedTranslationModel().first()

        translation = when (TranslationAvailableModel.findByName(translationModel)) {
            TranslationAvailableModel.MlKit -> MlKitTranslation(
                localFileDataSource = localFileDataSource,
                localSettingDataSource = localSettingDataSource
            )

            TranslationAvailableModel.M2M100 -> M2M100(
                context = context,
                localSettingDataSource = localSettingDataSource,
                localFileDataSource = localFileDataSource,
            )
        }.apply {
            initialize()
        }

        isInitialized = true
    }

    fun release() {
        if(isInitialized) {
            translation.release()
            isInitialized = false
        }
    }

    suspend fun cancelAndReInitialize() {
        release()
        initialize()
    }

    suspend fun translate(sourceText: String, srcLang: LanguageCode): String {
        if (!isInitialized)
            initialize()

        val targetLanguageISOCode = Locale.getDefault().language

        val transcribedText = translation.translate(
            text = sourceText,
            srcLang = srcLang,
            tgtLang = LanguageCode.findByCode(targetLanguageISOCode)!!,
        )

        return transcribedText
    }

    suspend fun translateSubtitle(id: Long) {
        if (!isInitialized)
            initialize()

        translation.translateSubtitle(id)
    }
}