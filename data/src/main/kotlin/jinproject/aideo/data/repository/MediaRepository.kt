package jinproject.aideo.data.repository

interface MediaRepository {

    /**
     * 자막 파일이 존재하는지 확인하는 함수
     *
     * @return 자막언어와 일치하는 자막 파일이 있으면 1,
     * 자막언어와 일치하는 자막 파일은 없지만 다른 언어로 번역된 자막 파일이 있으면 0,
     * 어떠한 자막 파일도 없으면 -1
     */
    suspend fun checkSubtitleFileExist(id: Long): Int
    suspend fun checkSubtitleFileExist(id: Long, srcLang: String): Int

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
