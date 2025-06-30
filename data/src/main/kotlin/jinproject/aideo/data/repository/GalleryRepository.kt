package jinproject.aideo.data.repository

interface GalleryRepository {
    /**
     * srt 자막 파일을 언어 설정에 맞게 번역하여 파일로 저장
     *
     * @exception TranscriptionException
     *
     */
    suspend fun translateSubtitle(
        srtFileAbsolutePath: String,
    )

    sealed class TranscriptionException : Exception() {
        data class TranscriptionFailed(
            override val message: String?,
            override val cause: Throwable?
        ) : TranscriptionException()
    }
}
