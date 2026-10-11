package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.afterCallPrefsDataStore by preferencesDataStore(name = "after_call_prefs")

private val AFTER_CALL_SCREEN_KEY = booleanPreferencesKey("after_call_screen_enabled")

/**
 * Settings' "After call screen" toggle. Backed by DataStore Preferences, same mechanism as
 * [NotificationPreferencesRepository]. Unlike those toggles this one is actually read downstream:
 * InCallServiceImpl checks it before launching CallEndedActivity (the post-call popup).
 */
class AfterCallPreferencesRepository {

    fun afterCallScreenEnabled(context: Context): Flow<Boolean> =
        context.afterCallPrefsDataStore.data.map { prefs -> prefs[AFTER_CALL_SCREEN_KEY] ?: true }

    suspend fun setAfterCallScreenEnabled(context: Context, enabled: Boolean) {
        context.afterCallPrefsDataStore.edit { prefs -> prefs[AFTER_CALL_SCREEN_KEY] = enabled }
    }
}
