package jinproject.aideo.data.repository

interface GalleryRepository {
    /**
     * srt 자막 파일을 언어 설정에 맞게 번역하는 함수
     *
     * @exception TranscriptionException
     *
     */
    suspend fun translateSubtitle(
        audioFileAbsolutePath: String,
    )

    sealed class TranscriptionException: Exception() {
        data class AudioExtractionFailed(override val message: String?, override val cause: Throwable?): TranscriptionException()
        data class TranscriptionFailed(override val message: String?, override val cause: Throwable?): TranscriptionException()
        data class SubtitleCreationFailed(override val message: String?, override val cause: Throwable?): TranscriptionException()
    }
}
