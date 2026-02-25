package jinproject.aideo.core.inference.speechRecognition.api

import jinproject.aideo.core.AvailableSoCModel

/**
 * 오디오(Speech) 를 문자(Text) 로 추론(변환)의 수행을 담당
 */
abstract class SpeechRecognition {
    protected abstract val transcribedResult: StringBuilder
    var socModel: AvailableSoCModel = AvailableSoCModel.Default
        private set

    val isQnn: Boolean
        get() = socModel.isQnnModel()

    protected var isInitialized = false
    var isUsed = false

    abstract fun initialize()
    abstract fun release()

    open suspend fun transcribe(audioData: FloatArray, language: String) {
        require(isInitialized) {
            "SpeechToText is not initialized."
        }

        return transcribeByModel(audioData = audioData, language = language)
    }

    protected abstract suspend fun transcribeByModel(audioData: FloatArray, language: String)

    open fun getResult(): String {
        return transcribedResult.toString().trim()
    }

    fun setSoCModel(model: AvailableSoCModel) {
        socModel = model
    }
}