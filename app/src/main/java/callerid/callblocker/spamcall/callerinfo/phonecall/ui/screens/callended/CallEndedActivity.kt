package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.MainActivity
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.analytics.AnalyticsHelper
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallSummary
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.EndedCallDetails
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.PostCallMonitor
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraTheme
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ThemeState
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * The "call ended" screen: a regular full-screen Activity of its own (no fragment, no dialog, no
 * overlay window), shown after every ended call whether or not it was answered. Launched by
 * InCallServiceImpl while this app is the default phone app, and by the PostCallMonitor triggers
 * / [PostCallActivity] while it isn't.
 *
 * It reuses the same setShowWhenLocked/setTurnScreenOn treatment MainActivity applies for a real
 * incoming call (see MainActivity.applyIncomingCallWindowFlags), so it also appears over the
 * lock screen.
 */
open class CallEndedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Same reason as MainActivity: the window background the system paints before Compose's
        // first frame must already match the saved theme.
        if (ThemeState.isDarkMode) setTheme(R.style.Theme_Callora_Dark)
        super.onCreate(savedInstanceState)
        // Edge to edge, like MainActivity: the header's colour runs up behind the status bar.
        // Light status-bar icons either way, since that header is a saturated blue/red band.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        val launchDetails = intent.getStringExtra(EXTRA_PHONE_NUMBER)?.let { phoneNumber ->
            EndedCallDetails(
                phoneNumber = phoneNumber,
                isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false),
                durationSeconds = intent.getLongExtra(EXTRA_DURATION_SECONDS, 0L),
                wasDeclined = intent.getBooleanExtra(EXTRA_WAS_DECLINED, false),
            )
        }
        if (launchDetails != null) {
            showCallEnded(launchDetails, intent.getLongExtra(EXTRA_ENDED_AT_MILLIS, System.currentTimeMillis()))
        } else {
            onCreateWithoutDetails()
        }
    }

    /** Launched without the extras [launch] sets — nothing real to show. PostCallActivity
     * overrides this to resolve the call from Telecom's post-call intent instead. */
    protected open fun onCreateWithoutDetails() {
        finish()
    }

    protected fun showCallEnded(details: EndedCallDetails, endedAtMillis: Long) {
        val phoneNumber = details.phoneNumber
        val isIncoming = details.isIncoming
        val durationSeconds = details.durationSeconds
        val wasDeclined = details.wasDeclined

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

        setContent {
            CalloraTheme(darkTheme = ThemeState.isDarkMode) {
                CallEndedRoute(
                    phoneNumber = phoneNumber,
                    isIncoming = isIncoming,
                    durationSeconds = durationSeconds,
                    wasDeclined = wasDeclined,
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
        private const val EXTRA_WAS_DECLINED =
            "callerid.callblocker.spamcall.callerinfo.phonecall.EXTRA_CALL_ENDED_WAS_DECLINED"

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
                putExtra(EXTRA_WAS_DECLINED, summary.wasDeclined)
            }
            context.startActivity(intent)
        }

        /**
         * The not-default-phone-app path (CallMonitorService / PostCallPhoneStateReceiver): works
         * out what the call that just ended was and shows it, unless another trigger already did
         * or there's nothing to show — see [PostCallMonitor].
         */
        suspend fun launchForEndedCall(context: Context) {
            if (!PostCallMonitor.tryClaim()) return
            val details = PostCallMonitor.resolveEndedCall(context) ?: return
            launch(
                context = context,
                summary = CallSummary(
                    phoneNumber = details.phoneNumber,
                    displayName = null,
                    durationSeconds = details.durationSeconds,
                    isContactResolved = false,
                    wasDeclined = details.wasDeclined,
                ),
                isIncoming = details.isIncoming,
                endedAtMillis = System.currentTimeMillis(),
            )
        }
    }
}
