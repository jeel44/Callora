package callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide dark-mode override, hoisted above [CalloraTheme] so Settings' toggle can
 * flip the whole app's theme. Read/written directly (no persistence yet). Seeded from the system
 * night mode at process start (CalloraApplication.onCreate) so it agrees with the cold-start
 * splash, which can only follow the system setting (see values-night/colors.xml).
 */
object ThemeState {
    var isDarkMode by mutableStateOf(false)
}
