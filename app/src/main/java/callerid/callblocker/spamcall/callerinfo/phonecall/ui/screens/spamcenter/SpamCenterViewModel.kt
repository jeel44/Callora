package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.spamcenter

import android.app.Application
import android.telephony.PhoneNumberUtils
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

// Stats-card counts: same scan window as the spam-calls list above, but counting rather than
// collecting rows, so this can scan more of the log without building/holding entries for all of
// it. "This week" is a time filter over the same scan, not a second query.
private const val BLOCKED_STATS_MAX_ROWS_SCANNED = 5_000
private const val BLOCKED_STATS_WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

class SpamCenterViewModel(application: Application) : AndroidViewModel(application) {

    private val blockedNumbersRepository = BlockedNumbersRepository()
    private val spamMarkRepository = SpamMarkRepository()
    private val callLogRepository = CallLogRepository()

    private val _blockedCount = MutableStateFlow(0)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    // Real counts for the stats card — calls in the device's call log from a number that's
    // either on the system block list (BlockedNumbersRepository) or locally spam-marked
    // (SpamMarkRepository), i.e. the same OR CalloraCallScreeningService itself rejects on. Both
    // null until the first load finishes, so the card can show a loading state instead of "0"
    // flashing before real data arrives.
    private val _blockedThisWeekCount = MutableStateFlow<Int?>(null)
    val blockedThisWeekCount: StateFlow<Int?> = _blockedThisWeekCount.asStateFlow()
    private val _blockedAllTimeCount = MutableStateFlow<Int?>(null)
    val blockedAllTimeCount: StateFlow<Int?> = _blockedAllTimeCount.asStateFlow()

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
        viewModelScope.launch {
            val context = getApplication<Application>()
            val (thisWeek, allTime) = withContext(Dispatchers.IO) {
                val spamNumbers = spamMarkRepository.getAllSpamMarks(context).mapTo(HashSet()) { it.phoneNumber }
                val blockedNumbers = blockedNumbersRepository.getBlockedNumbers(context)
                val isBlockedOrSpam: (String) -> Boolean = { number ->
                    spamMarkRepository.normalize(number) in spamNumbers ||
                        blockedNumbers.any { PhoneNumberUtils.compare(it.phoneNumber, number) }
                }
                if (spamNumbers.isEmpty() && blockedNumbers.isEmpty()) {
                    return@withContext 0 to 0
                }
                val matches = callLogRepository.getRecentCallsMatching(
                    context = context,
                    // Only counted, never shown — high enough to not undercount a busy device.
                    limit = BLOCKED_STATS_MAX_ROWS_SCANNED,
                    maxRowsScanned = BLOCKED_STATS_MAX_ROWS_SCANNED,
                    matches = isBlockedOrSpam,
                )
                val weekCutoff = System.currentTimeMillis() - BLOCKED_STATS_WEEK_MILLIS
                val weekCount = matches.count { it.timestamp >= weekCutoff }
                weekCount to matches.size
            }
            _blockedThisWeekCount.value = thisWeek
            _blockedAllTimeCount.value = allTime
        }
    }
}
