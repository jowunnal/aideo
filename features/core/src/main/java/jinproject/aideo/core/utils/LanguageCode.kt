package jinproject.aideo.core.utils

import android.util.Log
import androidx.compose.runtime.Stable

@Stable
enum class LanguageCode(val code: String) {
    Auto("auto"),
    Korean("ko"),
    English("en"),
    Japanese("ja"),
    Chinese("zh");

    companion object {
        fun findByName(name: String): LanguageCode = entries.first { it.name == name }
        fun findByCode(code: String): LanguageCode? = entries.find { it.code == code }
    }
}