package jinproject.aideo.core.utils

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import jinproject.aideo.core.SnackBarMessage

val LocalShowSnackBar: ProvidableCompositionLocal<(SnackBarMessage) -> Unit> =
    staticCompositionLocalOf {
        { snackBarMessage: SnackBarMessage ->
            error("showSnackBar is not initialized")
        }
    }

val LocalShowInterstitialAd: ProvidableCompositionLocal<() -> Unit> =
    staticCompositionLocalOf {
        {
            error("showSnackBar is not initialized")
        }
    }