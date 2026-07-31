package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationPrefsDataStore by preferencesDataStore(name = "notification_prefs")

private val MISSED_CALLS_KEY = booleanPreferencesKey("missed_calls_enabled")
private val SPAM_ALERTS_KEY = booleanPreferencesKey("spam_alerts_enabled")
private val APP_UPDATES_KEY = booleanPreferencesKey("app_updates_enabled")

data class NotificationPreferences(
    val missedCallsEnabled: Boolean = true,
    val spamAlertsEnabled: Boolean = true,
    val appUpdatesEnabled: Boolean = true,
)

/**
 * Visual/state-only toggle preferences for Settings' Notifications sub-page. Backed by DataStore
 * Preferences, same mechanism as [OnboardingRepository] — but unlike that flag, nothing downstream
 * reads these yet, since there's no real notification-sending logic for these categories currently.
 * This only makes the toggles remember their on/off state across app restarts.
 */
class NotificationPreferencesRepository {

    fun preferences(context: Context): Flow<NotificationPreferences> =
        context.notificationPrefsDataStore.data.map { prefs ->
            NotificationPreferences(
                missedCallsEnabled = prefs[MISSED_CALLS_KEY] ?: true,
                spamAlertsEnabled = prefs[SPAM_ALERTS_KEY] ?: true,
                appUpdatesEnabled = prefs[APP_UPDATES_KEY] ?: true,
            )
        }

    suspend fun setMissedCallsEnabled(context: Context, enabled: Boolean) {
        context.notificationPrefsDataStore.edit { prefs -> prefs[MISSED_CALLS_KEY] = enabled }
    }

    suspend fun setSpamAlertsEnabled(context: Context, enabled: Boolean) {
        context.notificationPrefsDataStore.edit { prefs -> prefs[SPAM_ALERTS_KEY] = enabled }
    }

    suspend fun setAppUpdatesEnabled(context: Context, enabled: Boolean) {
        context.notificationPrefsDataStore.edit { prefs -> prefs[APP_UPDATES_KEY] = enabled }
    }
}
