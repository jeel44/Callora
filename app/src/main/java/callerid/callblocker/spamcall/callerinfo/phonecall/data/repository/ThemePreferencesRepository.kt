package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context

private const val THEME_PREFS = "theme_prefs"
private const val DARK_MODE_KEY = "dark_mode"

/**
 * Persists Settings' dark-theme toggle. Plain SharedPreferences rather than DataStore because
 * it's read synchronously in CalloraApplication.onCreate(), before the first frame — same
 * reasoning as LanguagePreferencesRepository's startup mirror. No saved value (fresh install, or
 * the toggle never touched) means light mode, regardless of the system theme.
 */
object ThemePreferencesRepository {

    fun isDarkModeForStartup(context: Context): Boolean =
        context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE).getBoolean(DARK_MODE_KEY, false)

    fun setDarkMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE).edit().putBoolean(DARK_MODE_KEY, enabled).apply()
    }
}
