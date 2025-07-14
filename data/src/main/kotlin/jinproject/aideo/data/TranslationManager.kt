package jinproject.aideo.data

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object TranslationManager {
    /**
     * 자막(srt) 내용물의 언어를 감지하는 함수
     *
     * @param srtContent
     * 1
     * 00:00:00,000 --> 00:00:02,000
     * Hello!
     *
     * @return
     * en
     *
     * @suppress 입력 문자열은 srt 포맷 이어야 함. 그렇지 않으면 언어를 감지하지 못하고, IllegalStateException 발생 가능
     * @throws IllegalStateException 입력된 srt 포맷의 문자열에서 자막 내용물을 추출하지 못했거나, 추출된 자막 내용물이 지원하지 않는 언어
     */
    suspend fun detectLanguage(srtContent: String): String = suspendCancellableCoroutine { cont ->
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.2f)
                .build()
        ).identifyLanguage(extractSubtitleContent(srtContent))
            .addOnSuccessListener { languageCode ->
                if (languageCode == "und") {
                    cont.resumeWithException(
                        IllegalStateException(
                            "[$srtContent]'s language couldn't be identified\noutput: ${
                                extractSubtitleContent(
                                    srtContent
                                )
                            }"
                        )
                    )
                } else {
                    cont.resume(languageCode)
                }
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    /**
     * srt 포맷의 문자열에서 자막 내용만 추출하는 함수
     *
     * @param srtContent
     * 1
     * 00:00:00,000 --> 00:00:02,000
     * Hello!
     *
     * @return
     * Hello!
     */
    fun extractSubtitleContent(srtContent: String): String {
        val srtBlockPattern = Regex(
            """\d+\s*\n\d{2}:\d{2}:\d{2},\d{3}\s*-->\s*\d{2}:\d{2}:\d{2},\d{3}\s*\n([\s\S]*?)(?=\n{2,}\d+\n|\z)"""
        )

        return srtBlockPattern.findAll(srtContent)
            .map { it.groupValues[1].replace("\n", " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("@")
    }

    /**
     * mlKit-Translation 의 번역 결과물을 원본 srt 문자열에 자막 내용물만 대체하는 함수
     *
     * @param originalSrtContent
     * 1
     * 00:00:00,000 --> 00:00:02,000
     * 안녕하세요!
     *
     * @param translatedContent
     * Hello!
     *
     * @return
     * 1
     * 00:00:00,000 --> 00:00:02,000
     * Hello!
     */
    fun restoreMlKitTranslationToSrtFormat(
        originalSrtContent: String,
        translatedContent: String
    ): String {
        val srtBlockPattern = Regex(
            """(\d+\s*\n\d{2}:\d{2}:\d{2},\d{3}\s*-->\s*\d{2}:\d{2}:\d{2},\d{3}\s*\n)([\s\S]*?)(?=\n{2,}\d+\n|\z)"""
        )

        val texts = translatedContent.split("@").iterator()
        return srtBlockPattern.replace(originalSrtContent) { match ->
            val (header, _) = match.destructured
            val nextText = if (texts.hasNext()) texts.next() else ""
            "$header$nextText"
        }
    }
}