package jinproject.aideo.core.inference.speechRecognition.api

import jinproject.aideo.core.category.stt.AvailableSoCModel
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel

/**
 * 오디오(Speech) 를 문자(Text) 로 추론(변환)의 수행을 담당
 */
abstract class SpeechRecognition {
    protected abstract val transcribedResult: StringBuilder
    abstract val availableSpeechRecognition: SpeechRecognitionAvailableModel

    var isInitialized = false
    var isUsed = false

    open suspend fun initialize() {
        if (isInitialized)
            return
    }

    open fun release() {
        if (!isInitialized)
            return

        resetState()
    }

    /**
     * 네이티브 인스턴스(recognizer)는 유지한 채 Kotlin 상태만 초기화.
     * 모델 재사용 시 release() + initialize() 대신 호출.
     */
    open fun resetState() {
        transcribedResult.clear()
        isUsed = false
    }

    open suspend fun transcribe(audioData: FloatArray) {
        require(isInitialized) {
            "SpeechToText is not initialized."
        }

        return transcribeByModel(audioData = audioData)
    }

    protected abstract suspend fun transcribeByModel(audioData: FloatArray)

    abstract fun updateLanguageConfig(language: String)

    open fun getResult(): String {
        return transcribedResult.toString().trim()
    }
}

interface QnnAccelerator {
    var socModel: AvailableSoCModel

    val isQnn: Boolean

    fun setSoCModel(model: AvailableSoCModel) {
        socModel = model
    }
}