package callerid.callblocker.spamcall.callerinfo.phonecall

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.LanguagePreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.OnboardingRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.MISSED_CALL_CHANNEL_ID
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Pre-warms the onboarding-complete DataStore read as early in the process lifecycle as
 * possible — well before MainActivity/CalloraNavHost exist — so the underlying Preferences file
 * is already open and its first value already loaded by the time CalloraNavHost's
 * produceState-based start-destination resolution asks for it, instead of that being the very
 * first (cold) access of this DataStore in the process. DataStore's `preferencesDataStore`
 * delegate always resolves to applicationContext internally regardless of which Context instance
 * requests it, so this warms the exact same singleton instance CalloraNavHost later reads from.
 */
class CalloraApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { OnboardingRepository().isOnboardingComplete(this@CalloraApplication) }
        applyPersistedLanguage()
        // No existing crash-reporting/analytics consent toggle exists anywhere in this app (see
        // Settings) to defer to, so this just uses Crashlytics' own default (collection on).
        // Explicit here rather than left implicit so it's obvious this is intentional, and so a
        // future consent toggle has a single call site to wire up instead of relying on the SDK
        // default. Left enabled for debug builds too — Settings' debug-only test-crash trigger
        // (see SettingsScreen.kt) needs a report to actually reach the Crashlytics console.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        createMissedCallNotificationChannel()
    }

    // Unlike the OnboardingRepository prewarm above (deliberately fire-and-forget — a few ms of
    // slack before Splash resolves its start destination is invisible), this one is a genuine
    // blocking read: AppCompatDelegate must already reflect the persisted language *before*
    // MainActivity's Configuration/resources are resolved, or the very first launch after
    // picking a non-default language would briefly apply the wrong locale. The DataStore file
    // holds a single string key, so this costs a negligible amount of process-startup time.
    private fun applyPersistedLanguage() {
        val languageTag = runBlocking {
            LanguagePreferencesRepository().selectedLanguageTag(this@CalloraApplication).first()
        }
        LanguagePreferencesRepository.applyLocale(languageTag)
    }

    // The app's other notification channel ("active_call_channel") is created lazily by
    // InCallServiceImpl.onCreate(), since it's only ever needed once a call exists. Missed-call
    // notifications can't wait for that — MissedCallNotifier.notifyIfMissed posts on this channel
    // the moment a call ends unanswered, which can be the very first thing that happens after a
    // cold start — so this one is created eagerly here instead.
    private fun createMissedCallNotificationChannel() {
        val channel = NotificationChannel(
            MISSED_CALL_CHANNEL_ID,
            getString(R.string.notification_channel_missed_calls_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.notification_channel_missed_calls_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
