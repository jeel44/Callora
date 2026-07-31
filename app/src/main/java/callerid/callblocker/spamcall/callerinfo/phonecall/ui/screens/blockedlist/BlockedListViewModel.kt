package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.blockedlist

import android.app.Application
import android.provider.BlockedNumberContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.BlockedNumberEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BlockedListUiState(
    val canManageBlockedNumbers: Boolean = true,
    val blockedNumbers: List<BlockedNumberEntry> = emptyList(),
    val isLoading: Boolean = true,
)

class BlockedListViewModel(application: Application) : AndroidViewModel(application) {

    private val blockedNumbersRepository = BlockedNumbersRepository()

    private val _uiState = MutableStateFlow(BlockedListUiState())
    val uiState: StateFlow<BlockedListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()
        val canManage = BlockedNumberContract.canCurrentUserBlockNumbers(context)
        _uiState.value = _uiState.value.copy(canManageBlockedNumbers = canManage, isLoading = true)

        viewModelScope.launch {
            val blockedNumbers = if (canManage) {
                withContext(Dispatchers.IO) { blockedNumbersRepository.getBlockedNumbers(context) }
            } else {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(blockedNumbers = blockedNumbers, isLoading = false)
        }
    }

    fun unblock(phoneNumber: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) { blockedNumbersRepository.unblockNumber(context, phoneNumber) }
            refresh()
        }
    }
}
