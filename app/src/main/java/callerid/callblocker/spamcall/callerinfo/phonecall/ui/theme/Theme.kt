package callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    // Flat call-log list tokens (Home, Spam Center) and the bottom nav's inactive tabs.
    val callOutgoing: Color,
    val callIncoming: Color,
    val callMissed: Color,
    val listDivider: Color,
    val mutedText: Color,
    // Soft tinted containers + their ink (selected nav tab, Settings icon tiles, count badges).
    val accentContainer: Color,
    val spamTag: Color,
    val spamTagContainer: Color,
    val rating: Color,
    val ratingContainer: Color,
)

private val LightExtendedColors = CalloraExtendedColors(
    accent = CalloraAccent,
    spam = CalloraSpam,
    success = CalloraSuccess,
    warning = CalloraWarning,
    onSurfaceSoft = LightOnSurfaceSoft,
    onSurfaceFaint = LightOnSurfaceFaint,
    callOutgoing = LightCallOutgoing,
    callIncoming = LightCallIncoming,
    callMissed = LightCallMissed,
    listDivider = LightListDivider,
    mutedText = LightMutedText,
    accentContainer = LightAccentContainer,
    spamTag = LightSpamTag,
    spamTagContainer = LightSpamTagContainer,
    rating = LightRating,
    ratingContainer = LightRatingContainer,
)

private val DarkExtendedColors = CalloraExtendedColors(
    accent = CalloraAccent,
    spam = CalloraSpam,
    success = CalloraSuccess,
    warning = CalloraWarning,
    onSurfaceSoft = DarkOnSurfaceSoft,
    onSurfaceFaint = DarkOnSurfaceFaint,
    callOutgoing = DarkCallOutgoing,
    callIncoming = DarkCallIncoming,
    callMissed = DarkCallMissed,
    listDivider = DarkListDivider,
    mutedText = DarkMutedText,
    accentContainer = DarkAccentContainer,
    spamTag = DarkSpamTag,
    spamTagContainer = DarkSpamTagContainer,
    rating = DarkRating,
    ratingContainer = DarkRatingContainer,
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

    // enableEdgeToEdge()'s default SystemBarStyle.auto picks bar icon colors once, from the
    // *system* night mode — not ThemeState, which Settings can flip independently. With the device
    // dark and the app light (or vice versa) that left white icons on the light canvas. Re-applied
    // on every recomposition so a Settings toggle updates the icons without an Activity recreate.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalCalloraExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CalloraTypography,
            shapes = CalloraShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Extends [MaterialTheme] with the tokens that don't have a standard color-scheme slot.
 * Usage mirrors the built-in scheme: `MaterialTheme.extendedColors.spam`.
 */
val MaterialTheme.extendedColors: CalloraExtendedColors
    @Composable
    get() = LocalCalloraExtendedColors.current

/** App-wide [androidx.compose.material3.Switch] colors. Unchecked colors are set explicitly:
 * Material3's defaults draw the unchecked thumb and border in colorScheme.outline, which this theme
 * uses as a pale divider tone (#DCE4EE light / #2A374A dark), leaving the off state almost
 * invisible against a card in either theme. */
@Composable
fun calloraSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
