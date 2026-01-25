package jinproject.aideo.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import jinproject.aideo.design.theme.AideoColor.Companion.dark_background
import jinproject.aideo.design.theme.AideoColor.Companion.dark_error
import jinproject.aideo.design.theme.AideoColor.Companion.dark_inversePrimary
import jinproject.aideo.design.theme.AideoColor.Companion.dark_onBackground
import jinproject.aideo.design.theme.AideoColor.Companion.dark_onError
import jinproject.aideo.design.theme.AideoColor.Companion.dark_onPrimary
import jinproject.aideo.design.theme.AideoColor.Companion.dark_onSecondary
import jinproject.aideo.design.theme.AideoColor.Companion.dark_onSurface
import jinproject.aideo.design.theme.AideoColor.Companion.dark_onSurfaceVariant
import jinproject.aideo.design.theme.AideoColor.Companion.dark_outline
import jinproject.aideo.design.theme.AideoColor.Companion.dark_outlineVariant
import jinproject.aideo.design.theme.AideoColor.Companion.dark_primary
import jinproject.aideo.design.theme.AideoColor.Companion.dark_scrim
import jinproject.aideo.design.theme.AideoColor.Companion.dark_secondary
import jinproject.aideo.design.theme.AideoColor.Companion.dark_surface
import jinproject.aideo.design.theme.AideoColor.Companion.dark_surfaceContainer
import jinproject.aideo.design.theme.AideoColor.Companion.dark_surfaceVariant
import jinproject.aideo.design.theme.AideoColor.Companion.light_background
import jinproject.aideo.design.theme.AideoColor.Companion.light_error
import jinproject.aideo.design.theme.AideoColor.Companion.light_inversePrimary
import jinproject.aideo.design.theme.AideoColor.Companion.light_onBackground
import jinproject.aideo.design.theme.AideoColor.Companion.light_onError
import jinproject.aideo.design.theme.AideoColor.Companion.light_onPrimary
import jinproject.aideo.design.theme.AideoColor.Companion.light_onSecondary
import jinproject.aideo.design.theme.AideoColor.Companion.light_onSurface
import jinproject.aideo.design.theme.AideoColor.Companion.light_onSurfaceVariant
import jinproject.aideo.design.theme.AideoColor.Companion.light_outline
import jinproject.aideo.design.theme.AideoColor.Companion.light_outlineVariant
import jinproject.aideo.design.theme.AideoColor.Companion.light_primary
import jinproject.aideo.design.theme.AideoColor.Companion.light_scrim
import jinproject.aideo.design.theme.AideoColor.Companion.light_secondary
import jinproject.aideo.design.theme.AideoColor.Companion.light_surface
import jinproject.aideo.design.theme.AideoColor.Companion.light_surfaceContainer
import jinproject.aideo.design.theme.AideoColor.Companion.light_surfaceVariant
import jinproject.aideo.design.theme.AideoColor.Companion.red

@Stable
private val DarkColorPalette = darkColorScheme(
    primary = dark_primary.color,
    onPrimary = dark_onPrimary.color,
    primaryContainer = red.color,
    onPrimaryContainer = red.color,
    inversePrimary = dark_inversePrimary.color,
    secondary = dark_secondary.color,
    onSecondary = dark_onSecondary.color,
    secondaryContainer = red.color,
    onSecondaryContainer = red.color,
    tertiary = red.color,
    onTertiary = red.color,
    tertiaryContainer = red.color,
    onTertiaryContainer = red.color,
    background = dark_background.color,
    onBackground = dark_onBackground.color,
    surface = dark_surface.color,
    onSurface = dark_onSurface.color,
    surfaceVariant = dark_surfaceVariant.color,
    onSurfaceVariant = dark_onSurfaceVariant.color,
    inverseSurface = red.color,
    inverseOnSurface = red.color,
    surfaceContainer = dark_surfaceContainer.color,
    error = dark_error.color,
    errorContainer = red.color,
    onError = dark_onError.color,
    onErrorContainer = red.color,
    scrim = dark_scrim.color,
    outline = dark_outline.color,
    outlineVariant = dark_outlineVariant.color,
)

@Stable
private val LightColorPalette = lightColorScheme(
    primary = light_primary.color,
    onPrimary = light_onPrimary.color,
    inversePrimary = light_inversePrimary.color,
    secondary = light_secondary.color,
    onSecondary = light_onSecondary.color,
    background = light_background.color,
    onBackground = light_onBackground.color,
    surface = light_surface.color,
    surfaceVariant = light_surfaceVariant.color,
    onSurface = light_onSurface.color,
    onSurfaceVariant = light_onSurfaceVariant.color,
    surfaceContainer = light_surfaceContainer.color,
    error = light_error.color,
    onError = light_onError.color,
    scrim = light_scrim.color,
    outline = light_outline.color,
    outlineVariant = light_outlineVariant.color,
)

@Composable
fun AideoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
