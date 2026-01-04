package jinproject.aideo.core.runtime.api

/**
 * 오디오(Speech) 를 문자(Text) 로 추론(변환)의 수행을 담당
 */
abstract class SpeechToText(
    protected val modelPath: String,
    protected val vocabPath: String,
) {
    protected abstract val transcribeResult: TranscribeResult
    protected var language: String = "auto"
        private set

    fun updateLanguage(lan: String) {
        language = lan
    }

    protected var isInitialized = false

    abstract fun initialize()
    abstract fun release()

    open suspend fun transcribe(audioData: FloatArray) {
        require(isInitialized) {
            "SpeechToText is not initialized."
        }

        return transcribeByModel(audioData = audioData)
    }

    protected abstract suspend fun transcribeByModel(audioData: FloatArray)

    open fun getResult(): String {
        return transcribeResult.transcription.toString().trim()
    }

    abstract class TranscribeResult {
        abstract val transcription: StringBuilder
    }

    companion object {
        const val MAX_TOKEN_LENGTH = 448

        /**
         * 타임 스탬프 간격: 0.02
         */
        const val TS_STEP = 0.02
    }
}