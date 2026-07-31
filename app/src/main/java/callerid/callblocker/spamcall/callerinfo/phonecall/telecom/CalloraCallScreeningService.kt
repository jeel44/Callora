package callerid.callblocker.spamcall.callerinfo.phonecall.telecom

import android.telecom.Call
import android.telecom.CallScreeningService
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Declaring this service (see AndroidManifest.xml) is what makes this app eligible for
 * RoleManager.ROLE_CALL_SCREENING — the system binds it to screen every incoming call once
 * Callora holds that role (implied by holding the default-dialer role, ROLE_DIALER, which
 * SetDefaultScreen requests and is confirmed held on-device).
 *
 * Real screening: rejects calls from numbers on the system BlockedNumberContract list
 * (BlockedNumbersRepository — written to by IncomingCallViewModel.blockCaller and Contact
 * Detail's Block action) or numbers this device has locally marked as spam
 * (SpamMarkRepository). Both lookups are ContentResolver/DataStore reads, so the response is
 * sent asynchronously rather than blocking onScreenCall's caller thread — the platform allows a
 * CallScreeningService up to 5 seconds to call respondToCall before it assumes an allow.
 */
class CalloraCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val blockedNumbersRepository = BlockedNumbersRepository()
    private val spamMarkRepository = SpamMarkRepository()

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        if (number.isNullOrBlank()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }
        serviceScope.launch {
            val shouldReject = blockedNumbersRepository.isNumberBlocked(applicationContext, number) ||
                spamMarkRepository.isMarkedAsSpam(applicationContext, number)
            val response = CallResponse.Builder()
                .setDisallowCall(shouldReject)
                .setRejectCall(shouldReject)
                // Blocked/spam calls shouldn't surface a missed-call notification — matches how
                // the system's own call-blocking (and most real dialers) treats a rejected call.
                .setSkipNotification(shouldReject)
                .build()
            respondToCall(callDetails, response)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
