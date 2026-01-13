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

val LocalShowRewardAd: ProvidableCompositionLocal<(() -> Unit) -> Unit> =
    staticCompositionLocalOf {
        {
            error("showSnackBar is not initialized")
        }
    }