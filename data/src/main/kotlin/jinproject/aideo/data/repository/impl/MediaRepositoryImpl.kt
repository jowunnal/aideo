package jinproject.aideo.data.repository.impl

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import jinproject.aideo.data.TranslationManager.extractSubtitleContent
import jinproject.aideo.data.TranslationManager.restoreMlKitTranslationToSrtFormat
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
            val sourceLanguageCode =
                localFileDataSource.getOriginSubtitleLanguageCode(id)

            val targetLanguageCode = localPlayerDataSource.getSubtitleLanguage().first()

            val srtContent = localFileDataSource.getFileContent(
                getSubtitleFileIdentifier(
                    id = id,
                    languageCode = sourceLanguageCode
                )
            )?.joinToString("\n")
                ?: throw MediaRepository.TranscriptionException.TranscriptionFailed(
                    message = "content is null",
                    cause = null
                )

            val translatedContent = suspendCancellableCoroutine { cont ->
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(
                        TranslateLanguage.fromLanguageTag(sourceLanguageCode)
                            ?: throw MediaRepository.TranscriptionException.WrongTranslationLanguage(
                                message = "source language code is null"
                            )
                    )
                    .setTargetLanguage(
                        TranslateLanguage.fromLanguageTag(targetLanguageCode)
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
                            extractSubtitleContent(srtContent)
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

            val convertedSrtContent = restoreMlKitTranslationToSrtFormat(
                originalSrtContent = srtContent,
                translatedContent = translatedContent
            )

            localFileDataSource.createFileAndWriteOnOutputStream(
                fileIdentifier = getSubtitleFileIdentifier(
                    id = id,
                    languageCode = targetLanguageCode
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
}

fun getSubtitleFileIdentifier(id: Long, languageCode: String): String =
    "${id}_$languageCode.srt"