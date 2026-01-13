package jinproject.aideo.core.utils

import androidx.compose.runtime.staticCompositionLocalOf
import com.google.firebase.analytics.ParametersBuilder
import jinproject.aideo.core.BillingModule

val LocalAnalyticsLoggingEvent = staticCompositionLocalOf {
    { event: AnalyticsEvent ->

    }
}

val LocalBillingModule = staticCompositionLocalOf<BillingModule> {
    error("No billing module provided")
}

sealed interface AnalyticsEvent {
    val eventName: String

    fun logEvent(block: ParametersBuilder)
}