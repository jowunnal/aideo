package jinproject.aideo.core.inference.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import jinproject.aideo.core.utils.LanguageCode
import jinproject.aideo.data.TranslationManager.extractSubtitleContent
import jinproject.aideo.data.TranslationManager.getSubtitleFileIdentifier
import jinproject.aideo.data.TranslationManager.restoreMlKitTranslationToSrtFormat
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTranslation @Inject constructor(
    private val localFileDataSource: LocalFileDataSource,
    private val localSettingDataSource: LocalSettingDataSource
) : jinproject.aideo.core.inference.translation.api.Translation() {

    private var translator: Translator? = null

    override fun initialize() {}

    override fun release() {
        translator?.close()
        translator = null
    }

    override suspend fun translate(
        text: String,
        srcLang: LanguageCode,
        tgtLang: LanguageCode,
        maxLength: Int
    ): String {
        return suspendCancellableCoroutine { cont ->
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(
                    TranslateLanguage.fromLanguageTag(srcLang.code)
                        ?: throw MediaRepository.TranscriptionException.WrongTranslationLanguage(
                            message = "source language code is null"
                        )
                )
                .setTargetLanguage(
                    TranslateLanguage.fromLanguageTag(tgtLang.code)
                        ?: throw MediaRepository.TranscriptionException.WrongTranslationLanguage(
                            message = "target language code is null"
                        )
                )
                .build()

            translator = Translation.getClient(options)

            val conditions = DownloadConditions.Builder()
                .build()

            translator?.downloadModelIfNeeded(conditions)
                ?.addOnSuccessListener {
                    translator?.translate(text)?.addOnSuccessListener { result ->
                        translator?.close()
                        cont.resume(result)
                    }?.addOnFailureListener { e ->
                        translator?.close()
                        cont.resumeWithException(e)
                    }
                }?.addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }

            cont.invokeOnCancellation {
                translator?.close()
                translator = null
            }
        }
    }

    override suspend fun translateSubtitle(videoId: Long) {
        withContext(Dispatchers.Main) {
            val sourceLanguageISOCode =
                localFileDataSource.getOriginSubtitleLanguageCode(videoId)

            val targetLanguageISOCode = localSettingDataSource.getSubtitleLanguage().first()

            val srtContent = localFileDataSource.getFileContentList(
                getSubtitleFileIdentifier(
                    id = videoId,
                    languageCode = sourceLanguageISOCode
                )
            )?.joinToString("\n")
                ?: throw MediaRepository.TranscriptionException.TranscriptionFailed(
                    message = "content is null",
                    cause = null
                )

            val translatedContent = translate(
                srcLang = LanguageCode.findByCode(sourceLanguageISOCode)!!,
                tgtLang = LanguageCode.findByCode(targetLanguageISOCode)!!,
                text = extractSubtitleContent(srtContent),
                maxLength = 0
            )

            val convertedSrtContent = restoreMlKitTranslationToSrtFormat(
                originalSrtContent = srtContent,
                translatedContent = translatedContent
            )

            localFileDataSource.createFileAndWriteOnOutputStream(
                fileIdentifier = getSubtitleFileIdentifier(
                    id = videoId,
                    languageCode = targetLanguageISOCode
                ),
                writeContentOnFile = { outputStream ->
                    runCatching {
                        outputStream.write(convertedSrtContent.toByteArray())
                    }.map {
                        true
                    }.getOrElse {
                        false
                    }
                }
            )
        }
    }

    suspend fun detectLanguage(text: String): LanguageCode =
        suspendCancellableCoroutine { cont ->
            LanguageIdentification.getClient(
                LanguageIdentificationOptions.Builder()
                    .setConfidenceThreshold(0.2f)
                    .build()
            ).identifyLanguage(text)
                .addOnSuccessListener { languageCode ->
                    if (languageCode == "und") {
                        cont.resumeWithException(
                            IllegalStateException(
                                "[$text]'s language couldn't be identified\noutput: ${
                                    extractSubtitleContent(
                                        text
                                    )
                                }"
                            )
                        )
                    } else {
                        cont.resume(
                            LanguageCode.findByCode(languageCode)
                                ?: throw IllegalStateException("$languageCode is not supported")
                        )
                    }
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }
}