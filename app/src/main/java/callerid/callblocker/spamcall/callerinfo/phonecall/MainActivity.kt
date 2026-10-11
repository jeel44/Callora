package callerid.callblocker.spamcall.callerinfo.phonecall

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.AppOpenAdManager
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallUiState
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.navigation.CalloraNavHost
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.navigation.Routes
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraTheme
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ThemeState
import kotlinx.coroutines.launch

private const val DIAG_TAG = "DIAG_MainActivity"

class MainActivity : AppCompatActivity() {

    // Compose state (not a plain var) so mutating it from onNewIntent — a normal Kotlin
    // callback, not a composable — still notifies the already-composed CalloraNavHost below,
    // whether this Activity instance is being freshly created or reused via singleTask.
    private var pendingDeepLinkRoute by mutableStateOf<String?>(null)

    // Separate from pendingDeepLinkRoute: placing a call must go through CalloraNavHost's
    // attemptPlaceCall gate (CALL_PHONE prompt, already-on-a-call snackbar, then the real
    // navigate-to-OUTGOING_DIALING-before-dialing sequence), not a raw navigate() to a route —
    // see CallEndedActivity's "Call" action, the one call-placing entry point that used to fire
    // Intent.ACTION_CALL directly instead of going through that gate.
    private var pendingCallPhoneNumber by mutableStateOf<String?>(null)

    // Read by the splash screen's keep-on-screen condition on every pre-draw pass (main thread).
    private var isStartDestinationResolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTimer.mark("MainActivity.onCreate start")
        val extrasDump = intent.extras?.keySet()?.joinToString { key -> "$key=${intent.extras?.get(key)}" }
        Log.i(
            DIAG_TAG,
            "onCreate: received intent, action=${intent.action}, extras=[$extrasDump], " +
                "EXTRA_INCOMING_CALL=${intent.getBooleanExtra(EXTRA_INCOMING_CALL, false)}, " +
                "EXTRA_OPEN_ACTIVE_CALL=${intent.getBooleanExtra(EXTRA_OPEN_ACTIVE_CALL, false)}",
        )
        // Must run before super.onCreate()/window inflation — this is what the system paints as
        // the window background while the process cold-starts (before Compose ever gets a frame
        // on screen) AND while this Activity's window is torn down and rebuilt by recreate() —
        // notably AppCompatDelegate.setApplicationLocales()'s recreate() on a language switch, or
        // Settings' dark-mode toggle if that's ever wired to recreate() too. Only matters for a
        // genuinely fresh/recreated window: an incoming call arriving via onNewIntent (Activity
        // already running, no window rebuild) never hits this path at all.
        // Incoming-call cold starts no longer get their own dark window (Theme_Callora_IncomingCall):
        // the call screens now paint the theme's own canvas, so the regular light/dark window
        // background is already the matching one and avoids a dark flash in light mode.
        // installSplashScreen() swaps Theme.Callora.Starting (the manifest theme the cold-start
        // splash was drawn from) for its postSplashScreenTheme, Theme.Callora — so it must come
        // before the dark-mode setTheme() below, which would otherwise be overwritten. A no-op
        // theme-wise for IncomingCallActivityAlias launches, whose theme has no splash attrs.
        val splashScreen = installSplashScreen()
        // Hold the splash until CalloraNavHost has resolved its start destination, so the blank
        // placeholder Surface it shows meanwhile never reaches the screen. Not for incoming calls:
        // nothing may delay the call UI's first frame.
        if (!intent.getBooleanExtra(EXTRA_INCOMING_CALL, false)) {
            splashScreen.setKeepOnScreenCondition { !isStartDestinationResolved }
        }
        if (ThemeState.isDarkMode) {
            Log.i(DIAG_TAG, "onCreate: ThemeState.isDarkMode true, calling setTheme(Theme_Callora_Dark) BEFORE super.onCreate()")
            setTheme(R.style.Theme_Callora_Dark)
        } else {
            Log.i(DIAG_TAG, "onCreate: light mode, keeping default Theme.Callora")
        }
        super.onCreate(savedInstanceState)
        syncSystemSplashThemeWithThemeState()
        suppressAppOpenAdForCallIntent(intent)
        enableEdgeToEdge()
        applyIncomingCallWindowFlags(intent)
        observeCallStateForWindowFlagCleanup()
        pendingDeepLinkRoute = routeForIntent(intent)
        pendingCallPhoneNumber = phoneNumberFromIntent(intent)
        Log.i(DIAG_TAG, "onCreate: pendingDeepLinkRoute=$pendingDeepLinkRoute")
        setContent {
            CalloraTheme(darkTheme = ThemeState.isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CalloraNavHost(
                        pendingDeepLinkRoute = pendingDeepLinkRoute,
                        onDeepLinkConsumed = { pendingDeepLinkRoute = null },
                        pendingCallPhoneNumber = pendingCallPhoneNumber,
                        onCallConsumed = { pendingCallPhoneNumber = null },
                        onStartDestinationResolved = {
                            if (!isStartDestinationResolved) {
                                StartupTimer.mark("start destination resolved (splash released)")
                                // Lets `adb logcat -s ActivityTaskManager` report "Fully drawn"
                                // for the first real screen, not just the splash window.
                                reportFullyDrawn()
                            }
                            isStartDestinationResolved = true
                        },
                    )
                }
            }
        }
        StartupTimer.mark("MainActivity.onCreate end")
    }

    // Single-Activity app (singleTask, see AndroidManifest.xml) — MainActivity being resumed is
    // therefore an accurate "is Callora in the foreground" signal. Read by
    // InCallServiceImpl.launchIncomingCallUi() to skip its redundant startActivity/bring-to-front
    // call when the app is already on screen: CalloraNavHost's own LaunchedEffect (reacting to
    // CallRepository.callState) already navigates to the Incoming Call screen in-place in that
    // case, so there's nothing left for a fresh Activity intent to do except trigger a visible,
    // unnecessary task-to-front transition on top of that already-correct navigation.
    override fun onResume() {
        super.onResume()
        isResumed = true
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
    }

    // MainActivity is launchMode="singleTask" (see AndroidManifest.xml) specifically so that
    // InCallServiceImpl re-launching it for an incoming call while it's already running reuses
    // this instance via onNewIntent rather than stacking a second one on top, which would leave
    // two separate NavControllers alive.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val extrasDump = intent.extras?.keySet()?.joinToString { key -> "$key=${intent.extras?.get(key)}" }
        Log.i(
            DIAG_TAG,
            "onNewIntent: received intent, action=${intent.action}, extras=[$extrasDump], " +
                "EXTRA_INCOMING_CALL=${intent.getBooleanExtra(EXTRA_INCOMING_CALL, false)}, " +
                "EXTRA_OPEN_ACTIVE_CALL=${intent.getBooleanExtra(EXTRA_OPEN_ACTIVE_CALL, false)}",
        )
        setIntent(intent)
        suppressAppOpenAdForCallIntent(intent)
        applyIncomingCallWindowFlags(intent)
        pendingDeepLinkRoute = routeForIntent(intent)
        pendingCallPhoneNumber = phoneNumberFromIntent(intent)
        Log.i(DIAG_TAG, "onNewIntent: pendingDeepLinkRoute=$pendingDeepLinkRoute")
    }

    /** Any intent that opens a call or post-call flow — incoming call, the ongoing-call
     * notification, placing/dialing a call, or CallEndedActivity's Contact action — must never
     * have an App Open ad appear over what it opens (see AppOpenAdManager). Only a plain launch
     * (launcher icon / recents) is eligible. */
    /**
     * The system draws the cold-start splash before any app code runs, so the only way to make
     * it follow the saved theme is to tell the system ahead of time: on API 33+ the theme passed
     * to [android.window.SplashScreen.setSplashScreenTheme] is persisted and used for every later
     * cold start. Re-sent whenever ThemeState flips (Settings toggle), so the next launch's
     * splash matches. Below API 33 the manifest's always-light Theme.Callora.Starting is used.
     */
    private fun syncSystemSplashThemeWithThemeState() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            snapshotFlow { ThemeState.isDarkMode }.collect { isDark ->
                splashScreen.setSplashScreenTheme(
                    if (isDark) R.style.Theme_Callora_Starting_Dark else R.style.Theme_Callora_Starting,
                )
            }
        }
    }

    private fun suppressAppOpenAdForCallIntent(intent: Intent) {
        val isCallRelated = intent.action == Intent.ACTION_DIAL ||
            intent.getBooleanExtra(EXTRA_INCOMING_CALL, false) ||
            intent.getBooleanExtra(EXTRA_OPEN_ACTIVE_CALL, false) ||
            intent.hasExtra(EXTRA_PLACE_CALL_PHONE_NUMBER) ||
            intent.hasExtra(EXTRA_OPEN_CONTACT_DETAIL)
        if (isCallRelated) AppOpenAdManager.suppressCurrentForeground()
    }

    /**
     * InCallServiceImpl launches this activity for real incoming calls, which can arrive while
     * the device is locked or the app isn't running at all — without these flags the activity
     * would start behind the lock screen where the user would never see it.
     */
    private fun applyIncomingCallWindowFlags(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_INCOMING_CALL, false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    /** [applyIncomingCallWindowFlags] grants show-over-keyguard for as long as this Activity
     * instance lives — never reverting it would leave MainActivity permanently exempt from being
     * paused by the lock screen after just one incoming call, which both leaks whatever screen
     * the app is on over the lock screen indefinitely and makes [isResumed] an unreliable "is the
     * device actually unlocked" signal for every call after the first (see
     * InCallServiceImpl.isDeviceUnlockedAndInteractive, which exists to compensate for exactly
     * that). Collects for the life of the Activity (not just while resumed/started) so a call
     * ending while the phone is locked clears the exemption immediately rather than waiting for
     * the user to next resume the app. */
    private fun observeCallStateForWindowFlagCleanup() {
        lifecycleScope.launch {
            CallRepository.callState.collect { info ->
                if (info.state == CallUiState.NONE || info.state == CallUiState.DISCONNECTED) {
                    clearIncomingCallWindowFlags()
                }
            }
        }
    }

    /** Reverts the show-over-keyguard/turn-screen-on exemption granted in
     * [applyIncomingCallWindowFlags]. Safe to call even when the flags were never set (e.g. after
     * an outgoing call, which never grants them) — clearing an already-clear flag is a no-op. */
    private fun clearIncomingCallWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        } else {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    /** Resolves a deep-link route from an intent, consumed via [pendingDeepLinkRoute] by
     * CalloraNavHost's own LaunchedEffect. Two independent triggers:
     *  - EXTRA_INCOMING_CALL: covers the case CalloraNavHost's own start-destination resolution
     *    (see resolveStartDestination in CalloraNavHost.kt) can't — this Activity instance
     *    already exists and was merely brought back via onNewIntent (backgrounded-not-killed),
     *    so setContent{}/resolveStartDestination never re-runs for this call. Without navigating
     *    explicitly here, the NavHost would keep showing whatever screen was last open until the
     *    reactive LaunchedEffect(callUiState.state, callUiState.isIncoming) eventually catches
     *    up. Checked first since it's the more urgent of the two.
     *  - EXTRA_OPEN_ACTIVE_CALL: the ongoing-call notification's tap target — this is what makes
     *    it open straight into the live call instead of whatever screen the app happened to be
     *    on. Reads live [CallRepository] state (rather than always assuming Active) since the
     *    notification now also shows during an outgoing call's Dialing/Ringing phase — tapping it
     *    then must land on OutgoingDialingScreen, not a blank/wrong Active Call screen with no
     *    active call to show. */
    /** The number to place a call to, from either this app's own [EXTRA_PLACE_CALL_PHONE_NUMBER]
     * (CallEndedActivity's "Call" action) or a system `tel:`/`android.intent.action.DIAL` deep
     * link (another app's "Call" button, a phone number link in a message, etc. — Callora is a
     * default-dialer candidate and declares a `tel:` intent-filter in the manifest, so these
     * must actually be handled, not silently dropped on Home). Goes through the same
     * attemptPlaceCall gate as every other call-placing entry point (see [placeCallIntent]'s own
     * doc), so a deep-link call still gets the CALL_PHONE prompt / already-on-a-call snackbar. */
    private fun phoneNumberFromIntent(intent: Intent): String? {
        intent.getStringExtra(EXTRA_PLACE_CALL_PHONE_NUMBER)?.let { return it }
        if (intent.data?.scheme == "tel") {
            return intent.data?.schemeSpecificPart
        }
        return null
    }

    private fun routeForIntent(intent: Intent): String? {
        if (intent.getBooleanExtra(EXTRA_INCOMING_CALL, false)) return Routes.INCOMING_CALL
        // CallEndedActivity's "Contact" action — opens the real contact_detail screen for a
        // matched number, same as tapping that contact anywhere else in the app.
        intent.getStringExtra(EXTRA_OPEN_CONTACT_DETAIL)?.let { phoneNumber ->
            return Routes.contactDetailRoute(phoneNumber)
        }
        if (!intent.getBooleanExtra(EXTRA_OPEN_ACTIVE_CALL, false)) return null
        val info = CallRepository.callState.value
        return when (info.state) {
            CallUiState.ACTIVE -> Routes.ACTIVE_CALL
            CallUiState.DIALING, CallUiState.RINGING ->
                info.phoneNumber?.let { Routes.outgoingDialingRoute(it) } ?: Routes.HOME
            else -> Routes.HOME
        }
    }

    companion object {
        const val EXTRA_INCOMING_CALL =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_INCOMING_CALL"
        const val EXTRA_OPEN_ACTIVE_CALL =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_OPEN_ACTIVE_CALL"
        const val EXTRA_OPEN_CONTACT_DETAIL =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_OPEN_CONTACT_DETAIL"
        const val EXTRA_PLACE_CALL_PHONE_NUMBER =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_PLACE_CALL_PHONE_NUMBER"

        @Volatile
        var isResumed: Boolean = false
            private set

        /** Used by CallEndedActivity's "Contact" action to open a matched number's real
         * contact_detail screen — reuses this singleTask Activity rather than launching a second
         * MainActivity instance. */
        fun contactDetailIntent(context: Context, phoneNumber: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_CONTACT_DETAIL, phoneNumber)
            }

        /** Used by CallEndedActivity's "Call" action (calling a number back right after a call
         * ended) — routes into CalloraNavHost's attemptPlaceCall gate (see
         * CalloraNavHostContent's pendingCallPhoneNumber effect) instead of firing a raw
         * Intent.ACTION_CALL, so it navigates straight to OUTGOING_DIALING like every other
         * call-placing entry point rather than only surfacing CallBannerHost. */
        fun placeCallIntent(context: Context, phoneNumber: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_PLACE_CALL_PHONE_NUMBER, phoneNumber)
            }
    }
}
