package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.MainActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.analytics.AnalyticsHelper
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallSummary
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraTheme
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ThemeState
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Truecaller-style "call ended" popup. Launched from
 * [callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository.onCallEnded] (wired
 * up in InCallServiceImpl.onCreate), which fires for every ended call regardless of whether it
 * was ever answered — unlike the existing AfterCallScreen, which only shows for a call that
 * reached Active via a Compose nav LaunchedEffect.
 *
 * A plain Activity, not a system overlay: no SYSTEM_ALERT_WINDOW, no WindowManager overlay
 * window. It reuses the exact same setShowWhenLocked/setTurnScreenOn treatment MainActivity
 * already applies for a real incoming call (see MainActivity.applyIncomingCallWindowFlags), so it
 * pops up over the lock screen or whatever app is currently foregrounded without any new
 * permission. Theme.Callora.CallEnded (see themes.xml) makes the window itself translucent so
 * whatever was behind it stays visible, dimmed, under the card — [CallEndedRoute] draws that dim
 * scrim itself in Compose.
 */
class CallEndedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Without this, decorFitsSystemWindows stays true (the default) and this Activity's own
        // content — including CallEndedRoute's full-screen dim scrim — is laid out only within
        // the system-bar-inset area. The status/nav bar strips outside that area are then a
        // separate, system-composited region that, for this *translucent* window, shows whatever
        // the previous Activity rendered there completely undimmed — the bleed-through bug this
        // fixes. Mirrors MainActivity's own enableEdgeToEdge() call so this window's Compose
        // content actually spans the full physical display, same as everywhere else in the app.
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER)
        if (phoneNumber == null) {
            finish()
            return
        }

        // This Activity sits outside CalloraNavHost's Compose nav graph entirely, so the central
        // OnDestinationChangedListener there (see CalloraNavHost) never sees it — this is the
        // explicit screen_view call that fills that gap. Fired here, right after the early-return
        // above, since this is genuinely the earliest point this screen is confirmed to actually
        // show (no more finish()-and-bail cases follow); doesn't need to wait on any async data
        // (unlike the call_ended event below), unlike CalloraNavHost's per-destination event.
        AnalyticsHelper.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, "call_ended")
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, "call_ended")
            },
        )

        val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)
        val durationSeconds = intent.getLongExtra(EXTRA_DURATION_SECONDS, 0L)
        val endedAtMillis = intent.getLongExtra(EXTRA_ENDED_AT_MILLIS, System.currentTimeMillis())

        setContent {
            CalloraTheme(darkTheme = ThemeState.isDarkMode) {
                CallEndedRoute(
                    phoneNumber = phoneNumber,
                    isIncoming = isIncoming,
                    durationSeconds = durationSeconds,
                    endedAtMillis = endedAtMillis,
                    onDismiss = { finish() },
                    onOpenContactDetail = { number ->
                        startActivity(MainActivity.contactDetailIntent(this, number))
                        finish()
                    },
                    onCall = { number ->
                        // Routes through CalloraNavHost's attemptPlaceCall gate (see
                        // MainActivity.placeCallIntent's own doc) instead of firing
                        // Intent.ACTION_CALL directly — this popup is a separate Activity from
                        // MainActivity's NavHost, so without this hand-off the call would start
                        // with nothing having navigated to OUTGOING_DIALING first, and only
                        // CallBannerHost would end up showing once CallRepository reports it.
                        startActivity(MainActivity.placeCallIntent(this, number))
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PHONE_NUMBER =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_CALL_ENDED_PHONE_NUMBER"
        private const val EXTRA_IS_INCOMING =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_CALL_ENDED_IS_INCOMING"
        private const val EXTRA_DURATION_SECONDS =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_CALL_ENDED_DURATION_SECONDS"
        private const val EXTRA_ENDED_AT_MILLIS =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_CALL_ENDED_AT_MILLIS"

        /** Called from [callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository.onCallEnded]
         * via InCallServiceImpl — [context] is a Service context there, hence FLAG_ACTIVITY_NEW_TASK.
         * No-ops when the ended call never resolved a number (nothing real to show). */
        fun launch(context: Context, summary: CallSummary, isIncoming: Boolean, endedAtMillis: Long) {
            val phoneNumber = summary.phoneNumber ?: return
            val intent = Intent(context, CallEndedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
                putExtra(EXTRA_DURATION_SECONDS, summary.durationSeconds)
                putExtra(EXTRA_ENDED_AT_MILLIS, endedAtMillis)
            }
            context.startActivity(intent)
        }
    }
}
