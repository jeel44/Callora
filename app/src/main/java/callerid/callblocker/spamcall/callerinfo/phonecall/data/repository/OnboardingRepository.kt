package callerid.callblocker.spamcall.callerinfo.phonecall.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")

private val ONBOARDING_COMPLETE_KEY = booleanPreferencesKey("onboarding_complete")

/**
 * Tracks whether the first-run Splash → Language Select → Onboarding sequence has ever
 * completed. Backed by DataStore Preferences, same mechanism as [SpamMarkRepository] — once set,
 * this flag is never cleared except by uninstalling the app (which removes the preferences file
 * along with the rest of app data).
 */
class OnboardingRepository {

    suspend fun isOnboardingComplete(context: Context): Boolean =
        context.onboardingDataStore.data.first()[ONBOARDING_COMPLETE_KEY] ?: false

    suspend fun markOnboardingComplete(context: Context) {
        context.onboardingDataStore.edit { prefs -> prefs[ONBOARDING_COMPLETE_KEY] = true }
    }
}
