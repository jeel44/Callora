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

    // Whether the block actually took (e.g. canCurrentUserBlockNumbers() is false) or not,
    // onboarding-style screens in this app always move forward after the attempt rather than
    // blocking navigation on the result.
    fun blockNumber(onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                blockedNumbersRepository.blockNumber(getApplication(), phoneNumber)
            }
            onComplete()
        }
    }
}
