package jinproject.aideo.core.utils

import androidx.compose.runtime.Stable
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel

@Stable
enum class LanguageCode(val code: String) {
    Auto("auto"),
    Korean("ko"),
    English("en"),
    Japanese("ja"),
    Chinese("zh"),
    German("de"),
    Indonesian("id"),
    French("fr"),
    Spanish("es"),
    Russian("ru"),
    Hindi("hi"),
    Cantonese("yue");

    companion object {
        fun findByName(name: String): LanguageCode = entries.first { it.name == name }
        fun findByCode(code: String): LanguageCode? = entries.find { it.code == code }

        /**
         * STT 모델에 맞는 추론 언어 코드들을 반환
         */
        fun getLanguageCodesByAvailableModel(model: SpeechRecognitionAvailableModel): Array<LanguageCode> {
            return when(model) {
                SpeechRecognitionAvailableModel.Whisper -> arrayOf(
                    Korean,
                    English,
                    Japanese,
                    Chinese,
                    Cantonese,
                    German,
                    Indonesian,
                    French,
                    Spanish,
                    Russian,
                    Hindi,
                )
                SpeechRecognitionAvailableModel.SenseVoice -> arrayOf(
                    Auto,
                    Korean,
                    English,
                    Japanese,
                    Chinese,
                    Cantonese,
                )
            }
        }
    }
}