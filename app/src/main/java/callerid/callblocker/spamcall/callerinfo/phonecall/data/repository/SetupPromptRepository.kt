package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context

private const val SETUP_PROMPT_PREFS = "setup_prompt_prefs"
private const val CALL_SCREENING_ROLE_REQUESTED_KEY = "call_screening_role_requested"
private const val OVERLAY_PERMISSION_REQUESTED_KEY = "overlay_permission_requested"
private const val AUTOSTART_PROMPT_SHOWN_KEY = "autostart_prompt_shown"

/**
 * Whether Set Default's two optional follow-up requests (the CALL_SCREENING role and "display
 * over other apps") have ever been made on this install, and whether its autostart dialog
 * (AutostartPromptDialog) has been shown — see SetDefaultScreen. Plain
 * SharedPreferences, same reasoning as [ThemePreferencesRepository]: read synchronously while
 * CalloraNavHost resolves its start destination. Once set, a user who declined isn't sent back
 * through Set Default on every launch just to be asked again.
 */
object SetupPromptRepository {

    private fun prefs(context: Context) = context.getSharedPreferences(SETUP_PROMPT_PREFS, Context.MODE_PRIVATE)

    fun hasCallScreeningRoleBeenRequested(context: Context): Boolean =
        prefs(context).getBoolean(CALL_SCREENING_ROLE_REQUESTED_KEY, false)

    fun markCallScreeningRoleRequested(context: Context) {
        prefs(context).edit().putBoolean(CALL_SCREENING_ROLE_REQUESTED_KEY, true).apply()
    }

    fun hasOverlayPermissionBeenRequested(context: Context): Boolean =
        prefs(context).getBoolean(OVERLAY_PERMISSION_REQUESTED_KEY, false)

    fun markOverlayPermissionRequested(context: Context) {
        prefs(context).edit().putBoolean(OVERLAY_PERMISSION_REQUESTED_KEY, true).apply()
    }

    fun hasAutostartPromptBeenShown(context: Context): Boolean =
        prefs(context).getBoolean(AUTOSTART_PROMPT_SHOWN_KEY, false)

    fun markAutostartPromptShown(context: Context) {
        prefs(context).edit().putBoolean(AUTOSTART_PROMPT_SHOWN_KEY, true).apply()
    }
}
