package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.content.Context
import android.os.SystemClock
import android.provider.CallLog
import android.telecom.DisconnectCause
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.AfterCallPreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallLogRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.EndedCall
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.setdefault.isDialerRoleHeld
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val PHONE_STATE_PREFS = "post_call_phone_state_prefs"
private const val LAST_STATE_KEY = "last_state"

// The call-log row for a call is written slightly after the call ends.
private const val CALL_LOG_ATTEMPTS = 6
private const val CALL_LOG_RETRY_DELAY_MILLIS = 500L
// A call-log row counts as "the call that just ended" if its start + talk time lands this close
// to now. Generous because a missed call's row has duration 0 but rang for up to a minute.
private const val JUST_ENDED_WINDOW_MILLIS = 90_000L
// Several sources can report the same call ending (see [PostCallMonitor]); only one may show it.
private const val CLAIM_WINDOW_MILLIS = 10_000L

/** Everything CallEndedActivity needs to show one finished call. */
data class EndedCallDetails(
    val phoneNumber: String,
    val isIncoming: Boolean,
    val durationSeconds: Long,
    val wasDeclined: Boolean,
)

/**
 * The call-ended screen's trigger while this app is NOT the default phone app. (While it is,
 * InCallServiceImpl is bound for every call and launches that screen itself, so everything here
 * stands down.) Three sources can report "a call just ended", strongest first:
 *  - Telecom's own post-call intent, sent to the CALL_SCREENING role holder on API 30+
 *    (PostCallActivity) — the system starts the Activity itself, nothing else needed;
 *  - [CallMonitorService], a foreground service listening to the call state;
 *  - [PostCallPhoneStateReceiver], the phone-state broadcast.
 * The last two open the screen from the background, which only works once the user granted
 * "display over other apps". [tryClaim] makes sure a call reported by several of them is shown
 * once.
 */
object PostCallMonitor {

    private var lastClaimElapsedMillis = 0L

    /** True for exactly one caller per ended call. */
    @Synchronized
    fun tryClaim(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastClaimElapsedMillis != 0L && now - lastClaimElapsedMillis < CLAIM_WINDOW_MILLIS) return false
        lastClaimElapsedMillis = now
        return true
    }

    /**
     * Feeds one phone-state report ([TelephonyManager.EXTRA_STATE_IDLE]/RINGING/OFFHOOK) from the
     * receiver or the service; true when it is the "a call just ended" transition. The last state
     * lives in SharedPreferences, not memory: the process is often started fresh for each
     * broadcast, so ringing and idle are rarely seen by the same instance. Main thread only.
     */
    fun onPhoneStateChanged(context: Context, state: String): Boolean {
        val prefs = context.getSharedPreferences(PHONE_STATE_PREFS, Context.MODE_PRIVATE)
        val previousState = prefs.getString(LAST_STATE_KEY, TelephonyManager.EXTRA_STATE_IDLE)
        // The same change arrives more than once (the broadcast is delivered twice with
        // READ_CALL_LOG held, and the service reports it too) — only a real transition counts.
        if (state == previousState) return false
        prefs.edit().putString(LAST_STATE_KEY, state).apply()
        return state == TelephonyManager.EXTRA_STATE_IDLE
    }

    /**
     * What the call that just ended was, or null when the call-ended screen shouldn't show for
     * it: this app is the default phone app, the "After call screen" setting is off, the call was
     * blocked, or nothing usable is known about it. Read from the call log; [hintNumber] and
     * [hintDisconnectCause] (Telecom's post-call extras) are the fallback when the log can't be
     * read or its row never shows up.
     */
    suspend fun resolveEndedCall(
        context: Context,
        hintNumber: String? = null,
        hintDisconnectCause: Int? = null,
    ): EndedCallDetails? = withContext(Dispatchers.IO) {
        if (isDialerRoleHeld(context)) return@withContext null
        if (!AfterCallPreferencesRepository().afterCallScreenEnabled(context).first()) return@withContext null

        val logged = awaitLoggedCall(context, hintNumber)
        when {
            logged != null ->
                // The system's own blocking (or CalloraCallScreeningService) rejected it — the
                // user never saw this call, so there's nothing to follow up on.
                if (logged.type == CallLog.Calls.BLOCKED_TYPE) {
                    null
                } else {
                    EndedCallDetails(
                        phoneNumber = logged.phoneNumber,
                        isIncoming = logged.type != CallLog.Calls.OUTGOING_TYPE,
                        durationSeconds = logged.durationSeconds,
                        wasDeclined = logged.type == CallLog.Calls.REJECTED_TYPE,
                    )
                }
            !hintNumber.isNullOrBlank() ->
                // Direction and duration aren't in Telecom's extras; a missed/rejected cause at
                // least identifies those as incoming.
                EndedCallDetails(
                    phoneNumber = hintNumber,
                    isIncoming = hintDisconnectCause == DisconnectCause.MISSED ||
                        hintDisconnectCause == DisconnectCause.REJECTED,
                    durationSeconds = 0L,
                    wasDeclined = hintDisconnectCause == DisconnectCause.REJECTED,
                )
            else -> null
        }
    }

    private suspend fun awaitLoggedCall(context: Context, hintNumber: String?): EndedCall? {
        val callLogRepository = CallLogRepository()
        repeat(CALL_LOG_ATTEMPTS) { attempt ->
            val latest = callLogRepository.getLatestCall(context)
            if (latest != null && latest.justEnded() &&
                (hintNumber.isNullOrBlank() || PhoneNumberUtils.compare(latest.phoneNumber, hintNumber))
            ) {
                return latest
            }
            if (attempt < CALL_LOG_ATTEMPTS - 1) delay(CALL_LOG_RETRY_DELAY_MILLIS)
        }
        return null
    }

    private fun EndedCall.justEnded(): Boolean =
        startedAtMillis + durationSeconds * 1000 >= System.currentTimeMillis() - JUST_ENDED_WINDOW_MILLIS
}
