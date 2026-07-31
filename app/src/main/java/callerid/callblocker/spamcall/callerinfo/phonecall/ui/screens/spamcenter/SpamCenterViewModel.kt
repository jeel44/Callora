package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.spamcenter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpamCenterViewModel(application: Application) : AndroidViewModel(application) {

    private val blockedNumbersRepository = BlockedNumbersRepository()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val count = withContext(Dispatchers.IO) { blockedNumbersRepository.getBlockedNumbers(context).size }
            _blockedCount.value = count
        }
    }
}
