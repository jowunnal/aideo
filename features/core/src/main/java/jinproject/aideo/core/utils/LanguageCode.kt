package jinproject.aideo.core.utils

import androidx.compose.runtime.Stable

@Stable
enum class LanguageCode(val code: String) {
    Auto("auto"),
    Korean("ko"),
    English("en"),
    Japanese("ja"),
    Chinese("zh");
}