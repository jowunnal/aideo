package jinproject.aideo.core.inference.speechRecognition.api

import jinproject.aideo.core.AvailableSoCModel
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel

/**
 * 오디오(Speech) 를 문자(Text) 로 추론(변환)의 수행을 담당
 */
abstract class SpeechRecognition {
    protected abstract val transcribedResult: StringBuilder
    abstract val availableSpeechRecognition: SpeechRecognitionAvailableModel

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
}

interface QnnAccelerator {
    var socModel: AvailableSoCModel

    val isQnn: Boolean

    fun setSoCModel(model: AvailableSoCModel) {
        socModel = model
    }
}