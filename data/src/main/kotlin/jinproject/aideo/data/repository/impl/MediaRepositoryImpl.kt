package jinproject.aideo.data.repository.impl

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import jinproject.aideo.data.TranslationManager
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import jinproject.aideo.data.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MediaRepositoryImpl @Inject constructor(
    private val localFileDataSource: LocalFileDataSource,
    private val localPlayerDataSource: LocalPlayerDataSource,
) : MediaRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun translateSubtitle(id: Long, languageCode: String) {
        val srtFileContent =
            localFileDataSource.getFileContent(
                getSubtitleFileIdentifier(
                    id = id,
                    languageCode = languageCode
                )
            )?.joinToString("\n")
                ?: throw MediaRepository.TranscriptionException.TranscriptionFailed(
                    message = "${
                        getSubtitleFileIdentifier(
                            id = id,
                            languageCode = languageCode
                        )
                    } content is null",
                    cause = null
                )

        val sourceLanguageCode = TranslationManager.detectLanguage(srtFileContent)

        val targetLanguageCode = localPlayerDataSource.getLanguageSetting().first()

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
                    translator.translate(srtFileContent)
                        .addOnSuccessListener { result ->
                            cont.resume(result)
                        }.addOnFailureListener { e ->
                            cont.resumeWithException(e)
                        }.addOnCompleteListener {
                            translator.close()
                        }
                }.addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }

            cont.invokeOnCancellation {
                translator.close()
            }
        }

        val convertedSrtContent = convertMlKitOutputToSrt(translatedContent)

        localFileDataSource.createFileAndWriteOnOutputStream(
            fileIdentifier = getSubtitleFileIdentifier(id = id, languageCode = targetLanguageCode),
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

    private fun convertMlKitOutputToSrt(mlKitOutput: String): String {
        val regex =
            Regex("""(\d+)\s+(\d{2})\s*:\s*(\d{2})\s*:\s*(\d{2}),(\d{3})\s*->\s*(\d{2})\s*:\s*(\d{2})\s*:\s*(\d{2}),(\d{3})\s+(.*)""")
        val lines = mlKitOutput.lines()
        val srtBuilder = StringBuilder()

        for (line in lines) {
            val match = regex.matchEntire(line.trim())
            if (match != null) {
                val (
                    index, sh, sm, ss, sms, eh, em, es, ems, text,
                ) = match.destructured

                val start =
                    "%02d:%02d:%02d,%03d".format(sh.toInt(), sm.toInt(), ss.toInt(), sms.toInt())
                val end =
                    "%02d:%02d:%02d,%03d".format(eh.toInt(), em.toInt(), es.toInt(), ems.toInt())

                srtBuilder.appendLine(index)
                srtBuilder.appendLine("$start --> $end")
                srtBuilder.appendLine(text.trim())
                srtBuilder.appendLine()
            }
        }
        return srtBuilder.toString()
    }
}

fun getSubtitleFileIdentifier(id: Long, languageCode: String): String =
    "${id}_$languageCode.srt"