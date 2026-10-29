package callerid.callblocker.spamcall.callerinfo.phonecall.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import callerid.callblocker.spamcall.callerinfo.phonecall.MainActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.RemoteConfigManager
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallUiState
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.navigation.Routes
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "AppOpenAdManager"

// Google's recommended App Open ad expiry — an older ad may no longer be valid to show.
private const val AD_EXPIRY_MILLIS = 4 * 60 * 60 * 1000L

// Frequency cap: at most one App Open ad per this long, however often the app is reopened.
private const val MIN_SHOW_INTERVAL_MILLIS = 10 * 60 * 1000L

// How long after a foreground event an ad may still appear. Cold start gets a few seconds since
// Remote Config + SDK init + the ad request all have to happen first; a warm resume only shows an
// ad that is already loaded (the window just covers ON_START -> MainActivity.onResume), so an ad
// never pops up seconds after the user has already started interacting.
private const val COLD_START_SHOW_WINDOW_MILLIS = 5_000L
private const val WARM_START_SHOW_WINDOW_MILLIS = 1_500L

// Upper bound on how long ad setup waits for the startup Remote Config fetch before re-deciding.
private const val REMOTE_CONFIG_WAIT_MILLIS = 10_000L

/**
 * App Open ad: preloaded at process start, shown when the app comes to the foreground (cold start
 * and resume-from-background, via [ProcessLifecycleOwner]) — gated by Remote Config's
 * `ad_app_open_enabled`. With that flag false (its in-app default) nothing here ever loads or
 * shows anything.
 *
 * Call-safety is the overriding rule: an ad must never cover a call-related screen. A show only
 * happens when ALL of these hold at that moment:
 *  - the resumed Activity is [MainActivity] (never CallEndedActivity or anything else),
 *  - [CallRepository] reports no call in progress,
 *  - the current nav route isn't a call screen (Incoming/Active/Outgoing Dialing) or a first-run
 *    screen (Splash/Language/Onboarding/Set Default) — see [BLOCKED_ROUTES],
 *  - the foreground event wasn't caused by a call-related intent (see [suppressCurrentForeground]).
 * Additionally, a call starting at any point cancels any pending show outright rather than just
 * deferring it, so a short call can't end with an ad popping up right after it.
 *
 * Only ever touched from the main thread (lifecycle callbacks, ad SDK callbacks, [scope]).
 */
object AppOpenAdManager : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    // Route constants as registered in CalloraNavHost (i.e. the patterns, not filled-in routes) —
    // NavDestination.route reports the pattern, which is what [onRouteChanged] receives.
    private val BLOCKED_ROUTES = setOf(
        Routes.SPLASH,
        Routes.LANGUAGE_SELECT,
        Routes.ONBOARDING_1,
        Routes.ONBOARDING_2,
        Routes.ONBOARDING_3,
        Routes.SET_DEFAULT,
        Routes.INCOMING_CALL,
        Routes.ACTIVE_CALL,
        Routes.OUTGOING_DIALING,
    )

    private lateinit var application: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var isSdkReady = false

    // Re-read live on every use: the startup fetch activates mid-session, so a remote "off" takes
    // effect immediately even if this process started out with "on".
    private val isEnabled: Boolean
        get() = isSdkReady && RemoteConfigManager.isAppOpenAdEnabled

    private var appOpenAd: AppOpenAd? = null
    private var adLoadedAtMillis = 0L
    private var isLoadingAd = false
    private var isShowingAd = false

    private var resumedActivity: Activity? = null
    private var currentRoute: String? = null
    private var hasHadFirstForeground = false

    // elapsedRealtime() deadline for the current foreground event's ad; 0 = no ad wanted now.
    private var showDeadlineMillis = 0L

    // Set when a call-related intent arrives before the process-level ON_START it belongs to
    // (cold start: MainActivity.onCreate runs before ON_START) — consumed by that ON_START.
    private var skipNextForeground = false

    // elapsedRealtime() of the last ad shown this process; 0 = none yet. See MIN_SHOW_INTERVAL_MILLIS.
    private var lastShownAtMillis = 0L

    /** Called once from CalloraApplication.onCreate() (main thread). */
    fun initialize(app: Application) {
        application = app
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        scope.launch {
            CallRepository.callState.collect { info ->
                if (!isCallIdle(info.state)) showDeadlineMillis = 0L
            }
        }
        scope.launch {
            MobileAdsInitializer.awaitReady()
            isSdkReady = true
            // First decision uses the last-activated values (disk, no network) so a cold start
            // can preload in time; a first-ever launch has only the in-app default (off) here,
            // which is fine since first-run never shows an ad anyway.
            RemoteConfigManager.awaitActivatedValues()
            loadAd()
            // Then again once this launch's fetch settles — it may have turned ads on (loads
            // now) or off (loadAd/tryShowAd become no-ops via isEnabled).
            RemoteConfigManager.awaitInitialFetch(REMOTE_CONFIG_WAIT_MILLIS)
            Log.d(TAG, "ad_app_open_enabled=${RemoteConfigManager.isAppOpenAdEnabled} after startup fetch")
            loadAd()
        }
    }

    /** Fed by CalloraNavHost's destination listener. */
    fun onRouteChanged(route: String) {
        currentRoute = route
        if (route in BLOCKED_ROUTES) {
            showDeadlineMillis = 0L
        } else {
            tryShowAd()
        }
    }

    /** Called by MainActivity for any intent that opens a call/post-call flow (incoming call,
     * ongoing-call notification, place-call, dial, contact-from-call-ended popup) — the foreground
     * event that intent causes (or already caused) must not get an ad. Handles both orderings:
     * on a warm resume ON_START has already fired by onNewIntent, on a cold start it hasn't yet. */
    fun suppressCurrentForeground() {
        showDeadlineMillis = 0L
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            skipNextForeground = true
        }
    }

    /**
     * Called just before this app itself sends the user to another app (SMS, email, share,
     * contact editor, a web link — see Context.startExternalActivity): coming back from that
     * must not be greeted with an ad. Unlike [suppressCurrentForeground] this is always about
     * the NEXT foreground event — the app is in the foreground right now, about to leave it.
     */
    fun suppressNextForeground() {
        showDeadlineMillis = 0L
        skipNextForeground = true
    }

    // ProcessLifecycleOwner ON_START: the app came to the foreground (cold start or from background).
    override fun onStart(owner: LifecycleOwner) {
        val isColdStart = !hasHadFirstForeground
        hasHadFirstForeground = true
        if (skipNextForeground || !isCallIdle(CallRepository.callState.value.state)) {
            skipNextForeground = false
            showDeadlineMillis = 0L
            return
        }
        val window = if (isColdStart) COLD_START_SHOW_WINDOW_MILLIS else WARM_START_SHOW_WINDOW_MILLIS
        showDeadlineMillis = SystemClock.elapsedRealtime() + window
        tryShowAd()
    }

    override fun onStop(owner: LifecycleOwner) {
        showDeadlineMillis = 0L
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivity = activity
        // MainActivity resumed => no ad is covering it. Normally onAdDismissedFullScreenContent
        // already cleared this, but when a call intent brings singleTask MainActivity to front,
        // AdActivity is cleared off the task without that callback ever firing — without this,
        // isShowingAd would stay stuck true and block every later ad for the process.
        if (activity is MainActivity && isShowingAd) {
            Log.d(TAG, "MainActivity resumed while isShowingAd — ad was torn down, resetting")
            isShowingAd = false
            loadAd()
        }
        tryShowAd()
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumedActivity === activity) resumedActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun loadAd() {
        if (!isEnabled || isLoadingAd || isAdAvailable()) return
        isLoadingAd = true
        val unitId = RemoteConfigManager.appOpenAdUnitId
        AppOpenAd.load(
            application,
            unitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App Open ad loaded")
                    ad.setOnPaidEventListener { value ->
                        AdRevenueLogger.log(value, unitId, AdFormat.APP_OPEN, ad.responseInfo)
                    }
                    appOpenAd = ad
                    adLoadedAtMillis = SystemClock.elapsedRealtime()
                    isLoadingAd = false
                    tryShowAd()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "App Open ad failed to load: ${error.message}")
                    isLoadingAd = false
                }
            },
        )
    }

    private fun isAdAvailable(): Boolean =
        appOpenAd != null && SystemClock.elapsedRealtime() - adLoadedAtMillis < AD_EXPIRY_MILLIS

    private fun tryShowAd() {
        if (showDeadlineMillis == 0L) return
        if (SystemClock.elapsedRealtime() > showDeadlineMillis) {
            showDeadlineMillis = 0L
            return
        }
        // Re-checked at show time too, not just at load time.
        if (!isEnabled || !RemoteConfigManager.isAppOpenAdEnabled || isShowingAd) return
        if (lastShownAtMillis != 0L &&
            SystemClock.elapsedRealtime() - lastShownAtMillis < MIN_SHOW_INTERVAL_MILLIS
        ) {
            showDeadlineMillis = 0L
            return
        }
        val activity = resumedActivity as? MainActivity ?: return
        if (!isCallIdle(CallRepository.callState.value.state)) {
            showDeadlineMillis = 0L
            return
        }
        val route = currentRoute ?: return
        if (route in BLOCKED_ROUTES) return
        if (!isAdAvailable()) {
            // Expired (or never loaded): drop it and fetch a fresh one. A cold start's window may
            // still be open when it arrives; a warm resume's almost certainly won't be.
            appOpenAd = null
            loadAd()
            return
        }
        val ad = appOpenAd ?: return
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open ad shown")
                // Preload the next one right away so it's ready for the next foreground event.
                loadAd()
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App Open ad dismissed")
                isShowingAd = false
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "App Open ad failed to show: ${error.message}")
                isShowingAd = false
                loadAd()
            }
        }
        isShowingAd = true
        lastShownAtMillis = SystemClock.elapsedRealtime()
        showDeadlineMillis = 0L
        appOpenAd = null // single-use; clearing it first lets loadAd() fetch the next one
        ad.show(activity)
    }

    // DISCONNECTED is CallRepository's terminal "call over" state (it's never reset to NONE until
    // the next outgoing call), so it counts as idle.
    private fun isCallIdle(state: CallUiState): Boolean =
        state == CallUiState.NONE || state == CallUiState.DISCONNECTED
}
