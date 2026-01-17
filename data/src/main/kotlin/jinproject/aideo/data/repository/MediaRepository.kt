package jinproject.aideo.data.repository

interface MediaRepository {
    /**
     * 기존에 존재하는 srt 자막 파일을 현재 언어 설정에 맞게 번역하여 파일로 저장
     *
     * @exception TranscriptionException
     *
     */
    suspend fun translateSubtitle(
        id: Long,
    )

    suspend fun translate(
        sourceText: String
    ): String

    sealed class TranscriptionException : Exception() {
        data class TranscriptionFailed(
            override val message: String?,
            override val cause: Throwable?
        ) : TranscriptionException()

        data class WrongTranslationLanguage(
            override val message: String?,
            override val cause: Throwable? = null
        ) : TranscriptionException()
    }
}
