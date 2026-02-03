package jinproject.aideo.data

import java.util.Locale

object TranslationManager {
    /**
     * srt 포맷의 문자열에서 자막 내용을 '@' 문자로 분리하여 추출하는 함수
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

    /**
     * 초 단위를 SRT 포맷에 맞는 [시:분:초:밀리초] 로 변환
     */
    fun formatSrtTime(seconds: Float): String {
        val hours = (seconds / 3600).toInt()
        val minutes = ((seconds % 3600) / 60).toInt()
        val secs = (seconds % 60).toInt()
        val millis = ((seconds % 1) * 1000).toInt()
        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d,%03d",
            hours,
            minutes,
            secs,
            millis
        )
    }

    fun getSubtitleFileIdentifier(id: Long, languageCode: String): String =
        "${id}_$languageCode.srt"
}