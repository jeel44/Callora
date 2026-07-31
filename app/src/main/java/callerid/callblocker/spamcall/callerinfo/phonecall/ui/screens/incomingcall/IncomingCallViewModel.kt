package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.incomingcall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.ActiveCallInfo
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Contact-name resolution lives on [CallRepository.callState] itself now (shared across the
 * whole call lifecycle — ringing, active, ended) rather than this screen doing its own separate
 * lookup, so Incoming Call and Active Call can never disagree about who's calling. */
class IncomingCallViewModel(application: Application) : AndroidViewModel(application) {

    private val spamMarkRepository = SpamMarkRepository()
    private val blockedNumbersRepository = BlockedNumbersRepository()

    val callState: StateFlow<ActiveCallInfo> = CallRepository.callState

    // This device's own "I marked this number as spam before" record — distinct from
    // callState.displayName == null (which just means "not a saved contact"), since a number can
    // be genuinely unknown without ever having been marked as spam by this user.
    private val _isSpamMarked = MutableStateFlow(false)
    val isSpamMarked: StateFlow<Boolean> = _isSpamMarked.asStateFlow()

    init {
        viewModelScope.launch {
            callState.map { it.phoneNumber }.distinctUntilChangedBy { it }.collect { phoneNumber ->
                _isSpamMarked.value = if (phoneNumber != null) {
                    withContext(Dispatchers.IO) {
                        spamMarkRepository.isMarkedAsSpam(getApplication(), phoneNumber)
                    }
                } else {
                    false
                }
            }
        }
    }

    /** Real Call.answer() via CallRepository — not just a navigation trigger. */
    fun answer() {
        CallRepository.answer()
    }

    /** Real Call.reject() via CallRepository — not just a navigation trigger. */
    fun reject() {
        CallRepository.reject()
    }

    /** Adds the current caller to the system block list — used by the Incoming Spam screen's
     * middle button (mirrors ContactDetailScreen's block action, minus the confirm step: there's
     * no time for a confirmation screen while a call is actively ringing). This now results in
     * genuine future-call rejection: CalloraCallScreeningService.onScreenCall consults this same
     * BlockedNumbersRepository on every incoming call and rejects a match — previously that
     * service allowed every call through unconditionally, so a "blocked" number was written to
     * BlockedNumberContract but nothing ever acted on it. */
    fun blockCaller() {
        val phoneNumber = callState.value.phoneNumber ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { blockedNumbersRepository.blockNumber(getApplication(), phoneNumber) }
        }
    }
}
