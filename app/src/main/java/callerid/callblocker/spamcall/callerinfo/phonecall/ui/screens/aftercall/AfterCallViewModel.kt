package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.aftercall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamCategory
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AfterCallUiState(
    val showReportSheet: Boolean = false,
)

class AfterCallViewModel(application: Application) : AndroidViewModel(application) {

    private val spamMarkRepository = SpamMarkRepository()

    // One-shot snapshot, same as AfterCallScreen's own `remember` of the summary — this
    // screen is shown exactly once right after the call ends, so no need to keep observing.
    private val phoneNumber: String? = CallRepository.lastCallSummary.value?.phoneNumber

    private val _uiState = MutableStateFlow(AfterCallUiState())
    val uiState: StateFlow<AfterCallUiState> = _uiState.asStateFlow()

    fun onReportSpamClicked() {
        if (phoneNumber == null) return
        _uiState.value = _uiState.value.copy(showReportSheet = true)
    }

    fun onReportSheetDismissed() {
        _uiState.value = _uiState.value.copy(showReportSheet = false)
    }

    /** [onMarked] runs the caller's navigate-to-Home — marking is a local, instant write that
     * has nowhere left to fail the way a network submission could, so this always proceeds. */
    fun onReportCategorySelected(category: SpamCategory, onMarked: () -> Unit) {
        val number = phoneNumber ?: return
        _uiState.value = _uiState.value.copy(showReportSheet = false)
        viewModelScope.launch {
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) { spamMarkRepository.markAsSpam(context, number, category) }
            onMarked()
        }
    }
}
