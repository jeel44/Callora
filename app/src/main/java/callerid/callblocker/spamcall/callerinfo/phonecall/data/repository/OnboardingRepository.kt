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
        (context.onboardingDataStore.data.first()[ONBOARDING_COMPLETE_KEY] ?: false)
            .also { cachedIsOnboardingComplete = it }

    suspend fun markOnboardingComplete(context: Context) {
        context.onboardingDataStore.edit { prefs -> prefs[ONBOARDING_COMPLETE_KEY] = true }
        cachedIsOnboardingComplete = true
    }

    companion object {
        /** Last value read or written this process, null until the first read finishes. Safe to
         * trust synchronously since the flag only ever goes false -> true, and that write goes
         * through [markOnboardingComplete]. Normally already set by CalloraApplication's prewarm
         * by the time CalloraNavHost first composes — see resolveStartDestinationIfReady. */
        @Volatile
        var cachedIsOnboardingComplete: Boolean? = null
            private set
    }
}
