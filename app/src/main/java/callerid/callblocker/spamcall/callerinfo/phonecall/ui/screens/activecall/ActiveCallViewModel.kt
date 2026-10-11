package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.activecall

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.ActiveCallInfo
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallAudioUiState
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActiveCallViewModel(application: Application) : AndroidViewModel(application) {

    private val spamMarkRepository = SpamMarkRepository()
    private val contactsRepository = ContactsRepository()

    val callState: StateFlow<ActiveCallInfo> = CallRepository.callState

    // Real InCallService mute/route state — the same source Outgoing Dialing's Mute/Speaker
    // buttons write to, so both screens always agree on the current audio state.
    val audioState: StateFlow<CallAudioUiState> = CallRepository.audioState

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

    /** Real Call.disconnect() via CallRepository — not just a navigation trigger. */
    fun endCall() {
        CallRepository.disconnect()
    }

    /** Real InCallService.setMuted — same underlying call as Outgoing Dialing's Mute button. */
    fun setMuted(muted: Boolean) {
        CallRepository.setMuted(muted)
    }

    /** Real Call.hold()/unhold() — see CallRepository's own doc. */
    fun toggleHold(currentlyOnHold: Boolean) {
        if (currentlyOnHold) CallRepository.unhold() else CallRepository.hold()
    }

    /** Real InCallService.setAudioRoute — same underlying call as Outgoing Dialing's Speaker button. */
    fun setSpeakerEnabled(enabled: Boolean) {
        CallRepository.setSpeakerEnabled(enabled)
    }

    /** Real Call.playDtmfTone — the in-call keypad's key-press, not a local beep sample. */
    fun playDtmfTone(digit: Char) {
        CallRepository.playDtmfTone(digit)
    }

    /** Real Call.stopDtmfTone — must be called on key release, or the tone would play forever. */
    fun stopDtmfTone() {
        CallRepository.stopDtmfTone()
    }

    /** Resolves the number behind Add Call's contact-picker result off the main thread —
     * [onResolved] is called with null if the picked row had no usable number. */
    fun resolvePickedAddCallNumber(phoneRowUri: Uri, onResolved: (String?) -> Unit) {
        viewModelScope.launch {
            val number = withContext(Dispatchers.IO) {
                contactsRepository.resolvePickedPhoneNumber(getApplication(), phoneRowUri)
            }
            onResolved(number)
        }
    }
}
