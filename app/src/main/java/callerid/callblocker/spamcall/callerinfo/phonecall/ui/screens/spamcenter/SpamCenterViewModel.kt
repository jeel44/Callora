package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.spamcenter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallLogRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.RecentCallEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Spam calls listed in Spam Center: the most recent [SPAM_CALLS_LIMIT], found within the newest
// [SPAM_CALLS_MAX_ROWS_SCANNED] call-log rows (see CallLogRepository.getRecentCallsMatching).
private const val SPAM_CALLS_LIMIT = 50
private const val SPAM_CALLS_MAX_ROWS_SCANNED = 5_000

class SpamCenterViewModel(application: Application) : AndroidViewModel(application) {

    private val blockedNumbersRepository = BlockedNumbersRepository()
    private val spamMarkRepository = SpamMarkRepository()
    private val callLogRepository = CallLogRepository()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    // Calls from spam-marked numbers — Home hides these, so this is where they're shown. null
    // until the first load finishes.
    private val _spamCalls = MutableStateFlow<List<RecentCallEntry>?>(null)
    val spamCalls: StateFlow<List<RecentCallEntry>?> = _spamCalls.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val count = withContext(Dispatchers.IO) { blockedNumbersRepository.getBlockedNumbers(context).size }
            _blockedCount.value = count
        }
        viewModelScope.launch {
            val context = getApplication<Application>()
            _spamCalls.value = withContext(Dispatchers.IO) {
                // Same normalized keys Home's spam filter matches against (SpamMarkRepository).
                val spamNumbers = spamMarkRepository.getAllSpamMarks(context).mapTo(HashSet()) { it.phoneNumber }
                if (spamNumbers.isEmpty()) {
                    emptyList()
                } else {
                    callLogRepository.getRecentCallsMatching(
                        context = context,
                        limit = SPAM_CALLS_LIMIT,
                        maxRowsScanned = SPAM_CALLS_MAX_ROWS_SCANNED,
                    ) { number -> spamMarkRepository.normalize(number) in spamNumbers }
                }
            }
        }
    }
}
