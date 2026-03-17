package jinproject.aideo.core.category.translation

import jinproject.aideo.core.category.translation.api.Translator
import jinproject.aideo.core.inference.ProgressReportable
import jinproject.aideo.core.inference.translation.TranslationAvailableModel
import jinproject.aideo.core.inference.translation.api.Translation
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.data.SubtitleFileConfig
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

class SubtitleTranslator @Inject constructor(
    translationProvider: Map<TranslationAvailableModel, @JvmSuppressWildcards Provider<Translation>>,
    private val localFileDataSource: LocalFileDataSource,
    private val localSettingDataSource: LocalSettingDataSource,
) : Translator(
    translationProvider = translationProvider,
    localSettingDataSource = localSettingDataSource
), ProgressReportable {
    private val _progress: MutableStateFlow<Float> = MutableStateFlow(0f)
    override val progress: StateFlow<Float> = _progress.asStateFlow()

    suspend fun translateSubtitle(videoId: Long) {
        if (!isTranslationRequired(videoId))
            return

        withContext(Dispatchers.Default) {
            initialize()

            try {
                val sourceLanguageISOCode =
                    localFileDataSource.getOriginSubtitleLanguageCodeOrNull(videoId)
                        ?: throw IllegalStateException("일치하는 자막 파일이 없습니다.")

                val targetLanguageISOCode = localSettingDataSource.getSubtitleLanguage().first()

                val srtContent = localFileDataSource.getFileContentList(
                    SubtitleFileConfig.toSubtitleFileIdentifier(
                        id = videoId,
                        languageCode = sourceLanguageISOCode
                    )
                ) ?: throw IllegalStateException("content is null")

                val srcLang = LanguageCode.findByCode(sourceLanguageISOCode)!!
                val tgtLang = LanguageCode.findByCode(targetLanguageISOCode)!!

                val totalTextLines = srtContent.indices.count { (it + 1) % 4 == 3 }
                var translatedCount = 0

                val translatedText = srtContent.mapIndexed { idx, lineText ->
                    ensureActive()

                    if ((idx + 1) % 4 == 3) {
                        translation!!.translate(
                            text = lineText,
                            srcLang = srcLang,
                            tgtLang = tgtLang
                        ).also {
                            _progress.value =
                                if (totalTextLines <= 1) 1f else (translatedCount++).toFloat() / (totalTextLines - 1)
                        }
                    } else {
                        lineText
                    }
                }.joinToString("\n")

                localFileDataSource.createFileAndWriteOnOutputStream(
                    fileIdentifier = SubtitleFileConfig.toSubtitleFileIdentifier(
                        id = videoId,
                        languageCode = targetLanguageISOCode
                    ),
                    writeContentOnFile = { outputStream ->
                        runCatching {
                            outputStream.write(translatedText.toByteArray())
                        }.map {
                            true
                        }.getOrElse {
                            false
                        }
                    }
                )
            } finally {
                release()
            }
        }
    }

    private suspend fun isTranslationRequired(videoId: Long): Boolean {
        val subtitleLanguage = localSettingDataSource.getSubtitleLanguage().first()
        val isSubtitleExist = localFileDataSource.isFileExist(
            SubtitleFileConfig.toSubtitleFileIdentifier(
                id = videoId,
                languageCode = subtitleLanguage
            )
        )

        return !isSubtitleExist
    }
}