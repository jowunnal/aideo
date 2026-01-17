package jinproject.aideo.data.repository.impl

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.TranslationManager.extractSubtitleContent
import jinproject.aideo.data.TranslationManager.getSubtitleFileIdentifier
import jinproject.aideo.data.TranslationManager.restoreMlKitTranslationToSrtFormat
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MediaRepositoryImpl @Inject constructor(
    private val localFileDataSource: LocalFileDataSource,
    private val localPlayerDataSource: LocalPlayerDataSource,
) : MediaRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun translateSubtitle(id: Long) {
        withContext(Dispatchers.Main) {
            val sourceLanguageISOCode =
                localFileDataSource.getOriginSubtitleLanguageCode(id)

            val targetLanguageISOCode = localPlayerDataSource.getSubtitleLanguage().first()

            val srtContent = localFileDataSource.getFileContent(
                getSubtitleFileIdentifier(
                    id = id,
                    languageCode = sourceLanguageISOCode
                )
            )?.joinToString("\n")
                ?: throw MediaRepository.TranscriptionException.TranscriptionFailed(
                    message = "content is null",
                    cause = null
                )

            val translatedContent = translateTextByMlKit(
                sourceLanguageISOCode = sourceLanguageISOCode,
                targetLanguageISOCode = targetLanguageISOCode,
                sourceText = extractSubtitleContent(srtContent)
            )

            val convertedSrtContent = restoreMlKitTranslationToSrtFormat(
                originalSrtContent = srtContent,
                translatedContent = translatedContent
            )

            localFileDataSource.createFileAndWriteOnOutputStream(
                fileIdentifier = getSubtitleFileIdentifier(
                    id = id,
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

    override suspend fun translate(sourceText: String): String {
        val sourceLanguageISOCode = TranslationManager.detectLanguage(sourceText)
        val targetLanguageISOCode = Locale.getDefault().language

        return translateTextByMlKit(
            sourceLanguageISOCode = sourceLanguageISOCode,
            targetLanguageISOCode = targetLanguageISOCode,
            sourceText = sourceText
        )
    }

    private suspend fun translateTextByMlKit(
        sourceLanguageISOCode: String,
        targetLanguageISOCode: String,
        sourceText: String,
    ): String {
        return suspendCancellableCoroutine { cont ->
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(
                    TranslateLanguage.fromLanguageTag(sourceLanguageISOCode)
                        ?: throw MediaRepository.TranscriptionException.WrongTranslationLanguage(
                            message = "source language code is null"
                        )
                )
                .setTargetLanguage(
                    TranslateLanguage.fromLanguageTag(targetLanguageISOCode)
                        ?: throw MediaRepository.TranscriptionException.WrongTranslationLanguage(
                            message = "target language code is null"
                        )
                )
                .build()

            val translator = Translation.getClient(options)

            val conditions = DownloadConditions.Builder()
                .build()

            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    translator.translate(
                        sourceText
                    ).addOnSuccessListener { result ->
                        translator.close()
                        cont.resume(result)
                    }.addOnFailureListener { e ->
                        translator.close()
                        cont.resumeWithException(e)
                    }
                }.addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }

            cont.invokeOnCancellation {
                translator.close()
            }
        }
    }
}