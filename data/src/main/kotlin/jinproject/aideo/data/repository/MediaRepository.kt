package jinproject.aideo.data.repository

interface MediaRepository {

    suspend fun checkSubtitleFileExist(id: Long): Int

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
