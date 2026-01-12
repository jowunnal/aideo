package jinproject.aideo.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
@JvmInline
value class AideoColor private constructor(val color: Color) {
    companion object {

        private val primary = AideoColor(Color(0xFF7FD182))
        val deep_primary = AideoColor(Color(0xFF315532))
        val lightBlack = AideoColor(Color(0xFF1F1F1F))
        val mediumBlack = AideoColor(Color(0xFF1A1A1A))
        private val black = AideoColor(Color(0xFF111111))
        private val white = AideoColor(Color(0XFFFFFFFF))
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
        val orange = AideoColor(Color(0xFFFF5722))

        val light_primary = primary
        val light_onPrimary = white
        val light_secondary = AideoColor(Color(0xFF91E4E1))
        val light_onSecondary = grey_600
        val light_error = red
        val light_onError = AideoColor(Color(0xFF410001))
        val light_background = white
        val light_onBackground = lightBlack
        val light_surface = white
        val light_surfaceVariant = grey_700
        val light_onSurface = lightBlack
        val light_onSurfaceVariant = grey_400
        val light_surfaceContainer = white
        val light_scrim = grey_600
        val light_outline = grey_600
        val light_outlineVariant = grey_500

        val dark_primary = deep_primary
        val dark_onPrimary = grey_300
        val dark_secondary = AideoColor(Color(0xFFD599E3))
        val dark_onSecondary = grey_300
        val dark_error = AideoColor(Color(0xFFFFB4A9))
        val dark_onError = deepRed
        val dark_background = black // 컨테이너 색상
        val dark_onBackground = grey_600
        val dark_surface = lightBlack // 상단바 색상
        val dark_surfaceVariant = grey_800
        val dark_onSurface = grey_600
        val dark_onSurfaceVariant = grey_200
        val dark_surfaceContainer = mediumBlack
        val dark_scrim = grey_300
        val dark_outline = grey_300
        val dark_outlineVariant = grey_400

        val itemSpaceColor = AideoColor(Color(0xFFDFEDF2))
        val itemListContentColor = AideoColor(Color(0xFFBFCFD9))
        val itemDetailContentColor = AideoColor(Color(0xFF3E4C59))
        val itemButtonColor = AideoColor(Color(0xFF2393D9))
        val itemNameColor = AideoColor(Color(0xFF35AAF2))
        val itemDescriptionColor = AideoColor(Color(0xFFC5CED9))
        val itemTextColor = AideoColor(Color(0xFF70818C))
    }
}
