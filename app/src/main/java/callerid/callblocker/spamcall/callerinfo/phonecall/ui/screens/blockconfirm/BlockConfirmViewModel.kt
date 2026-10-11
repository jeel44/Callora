package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.blockconfirm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockConfirmViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val blockedNumbersRepository = BlockedNumbersRepository()

    private val phoneNumber: String = Uri.decode(
        checkNotNull(savedStateHandle.get<String>("phoneNumber")) { "block_confirm requires a phoneNumber nav argument" },
    )

    // Navigation always proceeds after the attempt (this is still an onboarding-style
    // confirmation sheet, not a form to retry) - but build-2 QA (#8) found the sheet claiming
    // "you won't receive calls or texts from this number" even when the block silently failed
    // (not holding ROLE_DIALER). [onComplete] now carries whether it actually succeeded, so the
    // screen can tell the person honestly instead of letting a false promise stand.
    fun blockNumber(onComplete: (succeeded: Boolean) -> Unit) {
        viewModelScope.launch {
            val succeeded = withContext(Dispatchers.IO) {
                blockedNumbersRepository.blockNumber(getApplication(), phoneNumber)
            }
            onComplete(succeeded)
        }
    }
}
