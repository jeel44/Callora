package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.notifications

import android.app.Application
import android.telephony.PhoneNumberUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallLogRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NOTIFICATIONS_LIMIT = 20
private const val NOTIFICATIONS_MAX_ROWS_SCANNED = 5_000

/** One real "a spam/blocked call happened" event, derived from the call log — see
 * [NotificationsViewModel.refresh]. No separate notification log exists anywhere in this app
 * (spam marking/blocking are local-only, see CalloraCallScreeningService), so this is the
 * complete, honest list of what's actually happened, not a feed with its own storage. */
data class SpamBlockedNotification(
    val id: Long,
    val displayName: String?,
    val formattedPhoneNumber: String,
    val timestampMillis: Long,
)

class NotificationsViewModel(application: Application) : AndroidViewModel(application) {

    private val blockedNumbersRepository = BlockedNumbersRepository()
    private val spamMarkRepository = SpamMarkRepository()
    private val callLogRepository = CallLogRepository()

    // null until the first load finishes, then real entries (possibly empty on a fresh install
    // or a device with no blocked/spam calls yet) — never hardcoded sample rows.
    private val _notifications = MutableStateFlow<List<SpamBlockedNotification>?>(null)
    val notifications: StateFlow<List<SpamBlockedNotification>?> = _notifications.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            _notifications.value = withContext(Dispatchers.IO) {
                val spamNumbers = spamMarkRepository.getAllSpamMarks(context).mapTo(HashSet()) { it.phoneNumber }
                val blockedNumbers = blockedNumbersRepository.getBlockedNumbers(context)
                if (spamNumbers.isEmpty() && blockedNumbers.isEmpty()) return@withContext emptyList()

                callLogRepository.getRecentCallsMatching(
                    context = context,
                    limit = NOTIFICATIONS_LIMIT,
                    maxRowsScanned = NOTIFICATIONS_MAX_ROWS_SCANNED,
                ) { number ->
                    spamMarkRepository.normalize(number) in spamNumbers ||
                        blockedNumbers.any { PhoneNumberUtils.compare(it.phoneNumber, number) }
                }.map { call ->
                    SpamBlockedNotification(
                        id = call.id,
                        displayName = call.name,
                        formattedPhoneNumber = call.formattedPhoneNumber,
                        timestampMillis = call.timestamp,
                    )
                }
            }
        }
    }
}
