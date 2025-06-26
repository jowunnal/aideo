package jinproject.aideo.data.repository

interface GalleryRepository {
    /**
     * srt 자막 파일을 언어 설정에 맞게 번역하는 함수
     *
     * @exception TranscriptionException
     *
     * @return 자막 파일 생성에 성공하면 true or 이미 존재하는 경우 false
     */
    suspend fun translateSubtitle(
        audioFileAbsolutePath: String,
    ): Boolean

    sealed class TranscriptionException: Exception() {
        data class AudioExtractionFailed(override val message: String?, override val cause: Throwable?): TranscriptionException()
        data class TranscriptionFailed(override val message: String?, override val cause: Throwable?): TranscriptionException()
        data class SubtitleCreationFailed(override val message: String?, override val cause: Throwable?): TranscriptionException()
    }
}
