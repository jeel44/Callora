package callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Design tokens that don't have a direct Material3 [MaterialTheme.colorScheme] slot
 * (spam/success/warning semantics, and the two secondary ink tones). Accessed the
 * same way as the built-in scheme, via [CalloraTheme.colors].
 */
data class CalloraExtendedColors(
    val accent: Color,
    val spam: Color,
    val success: Color,
    val warning: Color,
    val onSurfaceSoft: Color,
    val onSurfaceFaint: Color,
)

private val LightExtendedColors = CalloraExtendedColors(
    accent = CalloraAccent,
    spam = CalloraSpam,
    success = CalloraSuccess,
    warning = CalloraWarning,
    onSurfaceSoft = LightOnSurfaceSoft,
    onSurfaceFaint = LightOnSurfaceFaint,
)

private val DarkExtendedColors = CalloraExtendedColors(
    accent = CalloraAccent,
    spam = CalloraSpam,
    success = CalloraSuccess,
    warning = CalloraWarning,
    onSurfaceSoft = DarkOnSurfaceSoft,
    onSurfaceFaint = DarkOnSurfaceFaint,
)

private val LocalCalloraExtendedColors = staticCompositionLocalOf { LightExtendedColors }

private val LightColorScheme = lightColorScheme(
    primary = CalloraPrimary,
    onPrimary = OnCalloraPrimary,
    secondary = CalloraAccent,
    onSecondary = OnCalloraPrimary,
    background = LightCanvas,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceSoft,
    outline = LightOutline,
    error = CalloraSpam,
    onError = OnCalloraPrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = CalloraPrimary,
    onPrimary = OnCalloraPrimary,
    secondary = CalloraAccent,
    onSecondary = OnCalloraPrimary,
    background = DarkCanvas,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceSoft,
    outline = DarkOutline,
    error = CalloraSpam,
    onError = OnCalloraPrimary,
)

@Composable
fun CalloraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalCalloraExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CalloraTypography,
            shapes = CalloraShapes,
            content = content,
        )
    }
}

/**
 * Extends [MaterialTheme] with the tokens that don't have a standard color-scheme slot.
 * Usage mirrors the built-in scheme: `MaterialTheme.extendedColors.spam`.
 */
val MaterialTheme.extendedColors: CalloraExtendedColors
    @Composable
    get() = LocalCalloraExtendedColors.current
