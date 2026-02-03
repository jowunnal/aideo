package jinproject.aideo.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
@JvmInline
value class AideoColor private constructor(val color: Color) {
    companion object {

        val primary = AideoColor(Color(0xFF64B5F6))
        val deep_primary = AideoColor(Color(0xFF1976D2))
        val lightBlack = AideoColor(Color(0xFF1F1F1F))
        val mediumBlack = AideoColor(Color(0xFF1A1A1A))
        private val darkBackground = AideoColor(Color(0xFF121212))
        private val darkSurface = AideoColor(Color(0xFF2C2C2C))
        private val white = AideoColor(Color(0xFFFFFFFF))
        private val grey_100 = AideoColor(Color(0xFFF5F5F5))
        private val grey_200 = AideoColor(Color(0xFFEEEEEE))
        val grey_300 = AideoColor(Color(0xFFE0E0E0))
        private val grey_400 = AideoColor(Color(0xFFBDBDBD))
        private val grey_500 = AideoColor(Color(0xFF9E9E9E))
        private val grey_600 = AideoColor(Color(0xFF757575))
        private val grey_700 = AideoColor(Color(0xFF616161))
        private val grey_800 = AideoColor(Color(0xFF424242))
        private val grey_900 = AideoColor(Color(0xFF212121))
        val red = AideoColor(Color(0xFFE0302D))
        val deepRed = AideoColor(Color(0xFF800006))
        val blue = AideoColor(Color(0xFF007AFF))
        val orange_500 = AideoColor(Color(0xFFFF9800))

        val light_primary = primary
        val light_onPrimary = white
        val light_inversePrimary = deep_primary
        val light_secondary = AideoColor(Color(0xFF91E4E1))
        val light_onSecondary = grey_600
        val light_error = red
        val light_onError = AideoColor(Color(0xFF410001))
        val light_background = white
        val light_onBackground = grey_900
        val light_surface = grey_100
        val light_surfaceVariant = grey_700
        val light_onSurface = grey_900
        val light_onSurfaceVariant = grey_400
        val light_surfaceContainer = grey_100
        val light_scrim = grey_600
        val light_outline = grey_600
        val light_outlineVariant = grey_500

        val dark_primary = deep_primary
        val dark_onPrimary = grey_900
        val dark_inversePrimary = primary
        val dark_secondary = AideoColor(Color(0xFFD599E3))
        val dark_onSecondary = grey_300
        val dark_error = AideoColor(Color(0xFFFFB4A9))
        val dark_onError = deepRed
        val dark_background = darkBackground
        val dark_onBackground = grey_200
        val dark_surface = darkSurface
        val dark_surfaceVariant = grey_800
        val dark_onSurface = grey_200
        val dark_onSurfaceVariant = grey_200
        val dark_surfaceContainer = darkSurface
        val dark_scrim = grey_300
        val dark_outline = grey_300
        val dark_outlineVariant = grey_400

        val amber_400 = AideoColor(Color(0xFFFFCA28))
        val amber_300 = AideoColor(Color(0xFFFFD54F))
        val indigo = AideoColor(Color(0xFF4338CA))
        val emerald = AideoColor(Color(0xFF047857))


    }
}
