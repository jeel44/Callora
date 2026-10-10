package callerid.callblocker.spamcall.callerinfo.phonecall

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.AppOpenAdManager
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.MobileAdsInitializer
import callerid.callblocker.spamcall.callerinfo.phonecall.analytics.AnalyticsHelper
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.LanguagePreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.OnboardingRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.RecentCallsSnapshotStore
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.RemoteConfigManager
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.MISSED_CALL_CHANNEL_ID
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ThemeState
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
        StartupTimer.mark("Application.onCreate start")
        super.onCreate()
        applicationScope.launch { OnboardingRepository().isOnboardingComplete(this@CalloraApplication) }
        // Same idea for Home's saved recent-calls snapshot: the disk read starts at process launch
        // (off the main thread) instead of at HomeViewModel creation, so on a cold start — ~2s
        // before Home's first frame on a mid-range device — the rows are already in memory and
        // Home paints them on its first frame instead of an empty RECENT section.
        StartupTimer.section("RecentCallsSnapshotStore.preload") { RecentCallsSnapshotStore.preload(this) }
        StartupTimer.section("applyPersistedLanguage") { applyPersistedLanguage() }
        // Start in the system's light/dark mode — the cold-start splash (Theme.Callora.Starting)
        // is drawn from values/values-night before any app code runs, so this is what keeps the
        // first Compose frame on the same canvas instead of flipping light<->dark after it.
        ThemeState.isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        // No existing crash-reporting/analytics consent toggle exists anywhere in this app (see
        // Settings) to defer to, so this just uses Crashlytics' own default (collection on).
        // Explicit here rather than left implicit so it's obvious this is intentional, and so a
        // future consent toggle has a single call site to wire up instead of relying on the SDK
        // default. Left enabled for debug builds too — Settings' debug-only test-crash trigger
        // (see SettingsScreen.kt) needs a report to actually reach the Crashlytics console.
        StartupTimer.section("Crashlytics.setCollectionEnabled") {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)
        }
        // Same implicit-init story as Crashlytics above — no FirebaseApp.initializeApp() needed.
        // AnalyticsHelper just gives the rest of the app a Context-free way to reach this instance.
        StartupTimer.section("AnalyticsHelper.initialize") {
            AnalyticsHelper.initialize(this)
            if (BuildConfig.DEBUG) {
                // Verification-only: confirms the Analytics pipeline is wired up end to end. Not a
                // real product event — never fires in release builds. Real events (set_default_dialer,
                // call-completed, spam-block, etc.) come later.
                AnalyticsHelper.logEvent("app_analytics_initialized")
            }
        }
        StartupTimer.section("createMissedCallNotificationChannel") { createMissedCallNotificationChannel() }
        // Ads: Remote Config fetch + MobileAds init both start here, fully async (MobileAds on
        // applicationScope's Dispatchers.Default per Google's off-main-thread guidance), so
        // neither delays startup. AppOpenAdManager waits on both before it loads anything, and is
        // a no-op unless Remote Config's ad_app_open_enabled is true.
        StartupTimer.section("RemoteConfigManager.initialize") { RemoteConfigManager.initialize() }
        StartupTimer.section("MobileAdsInitializer.initialize") { MobileAdsInitializer.initialize(this, applicationScope) }
        StartupTimer.section("AppOpenAdManager.initialize") { AppOpenAdManager.initialize(this) }
        StartupTimer.mark("Application.onCreate end")
    }

    // Unlike the OnboardingRepository prewarm above (deliberately fire-and-forget — a few ms of
    // slack before Splash resolves its start destination is invisible), this one is a genuine
    // blocking read: AppCompatDelegate must already reflect the persisted language *before*
    // MainActivity's Configuration/resources are resolved, or the very first launch after
    // picking a non-default language would briefly apply the wrong locale. It reads a
    // SharedPreferences mirror rather than DataStore — see persistedTagForStartup.
    private fun applyPersistedLanguage() {
        LanguagePreferencesRepository.applyLocale(LanguagePreferencesRepository.persistedTagForStartup(this))
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
