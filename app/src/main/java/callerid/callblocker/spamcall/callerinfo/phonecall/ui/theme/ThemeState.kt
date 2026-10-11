package callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide dark-mode override, hoisted above [CalloraTheme] so Settings' toggle can
 * flip the whole app's theme. Seeded at process start (CalloraApplication.onCreate) from
 * ThemePreferencesRepository — the saved Settings choice, or light when none is saved yet.
 */
object ThemeState {
    var isDarkMode by mutableStateOf(false)
}
