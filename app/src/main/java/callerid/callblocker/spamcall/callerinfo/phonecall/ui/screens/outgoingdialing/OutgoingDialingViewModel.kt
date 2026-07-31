package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.outgoingdialing

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.ActiveCallInfo
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallAudioUiState
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "OutgoingDialingVM"

class OutgoingDialingViewModel(application: Application) : AndroidViewModel(application) {

    private val spamMarkRepository = SpamMarkRepository()

    val callState: StateFlow<ActiveCallInfo> = CallRepository.callState

    // Real InCallService mute/route state — shared with Active Call rather than a locally-toggled
    // flag, so Mute/Speaker tapped here still reads correctly once the call becomes Active.
    val audioState: StateFlow<CallAudioUiState> = CallRepository.audioState

    private var callPlaced = false

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

    /**
     * Fires the real outgoing call via TelecomManager — the actual call state from here on
     * comes back through [callState], populated by InCallServiceImpl once the system binds it.
     * Returns false (permission missing, or no TelecomManager available) so the screen can bail
     * back out immediately instead of sitting on a "Calling…" state that will never update.
     *
     * Idempotent two ways: [callPlaced] covers recomposition/config changes within the same
     * screen instance, and the [CallRepository.callState] check below covers re-entering this
     * screen via a FRESH instance for a call that's already in progress (tapping the ongoing-call
     * banner/notification while still Dialing/Ringing navigates back to this same route, which
     * creates a brand new ViewModel with callPlaced=false — without this check that would call
     * CallRepository.reset() and TelecomManager.placeCall() a second time on top of the call
     * already running, instead of just resuming observation of it).
     *
     * [phoneAccountHandle] is the SIM the user picked via CalloraNavHost's SimPickerBottomSheet
     * (see placeCallWithSimCheck) — null on a single-SIM device, or whenever the picker never
     * needed to appear, in which case this is exactly the pre-existing placeCall(uri, null)
     * behavior: TelecomManager decides (silently uses the system default SIM, or shows its own
     * picker) with no change from before dual-SIM support existed.
     */
    fun placeCall(phoneNumber: String, phoneAccountHandle: PhoneAccountHandle?): Boolean {
        if (callPlaced) return true
        val existingState = CallRepository.callState.value.state
        if (existingState != CallUiState.NONE && existingState != CallUiState.DISCONNECTED) {
            callPlaced = true
            return true
        }
        val context = getApplication<Application>()
        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCallPermission) {
            Log.d(TAG, "placeCall: CALL_PHONE not granted, bailing out")
            return false
        }

        val telecomManager = context.getSystemService(TelecomManager::class.java) ?: return false
        CallRepository.reset()
        val uri = Uri.fromParts("tel", phoneNumber, null)
        val extras = phoneAccountHandle?.let { bundleOf(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE to it) }
        Log.i("DIAG_PlaceCall", "placeCall: about to invoke TelecomManager.placeCall, uri=$uri, accountHandle=$phoneAccountHandle")
        try {
            telecomManager.placeCall(uri, extras)
            Log.i("DIAG_PlaceCall", "placeCall: TelecomManager.placeCall returned with no exception")
        } catch (e: Exception) {
            Log.e("DIAG_PlaceCall", "placeCall: TelecomManager.placeCall threw", e)
            throw e
        }
        callPlaced = true
        return true
    }

    fun cancelCall() {
        CallRepository.disconnect()
    }

    /** Real InCallService.setMuted — same underlying call as Active Call's Mute button. */
    fun setMuted(muted: Boolean) {
        CallRepository.setMuted(muted)
    }

    /** Real InCallService.setAudioRoute — same underlying call as Active Call's Speaker button. */
    fun setSpeakerEnabled(enabled: Boolean) {
        CallRepository.setSpeakerEnabled(enabled)
    }
}
