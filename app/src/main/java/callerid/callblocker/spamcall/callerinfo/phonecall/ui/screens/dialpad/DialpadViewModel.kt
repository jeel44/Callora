package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.dialpad

import android.app.Application
import android.telephony.PhoneNumberUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallLogRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.RecentCallEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.normalizeDialedNumber
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.prewarmPhoneNumberMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MATCH_DEBOUNCE_MILLIS = 150L

private const val INDIA_COUNTRY_PREFIX = "+91"

/** How many of the newest call-log rows the Suggested list is picked from. */
private const val SUGGESTION_SCAN_ROWS = 50

private const val MAX_SUGGESTIONS = 4

/**
 * Digits to substring-match contacts against for what's on the dialpad. A number that resolves
 * to +91 (typed bare, as "091...", or already as "+91...") matches on its national part only, so
 * a contact saved as "+91 98765 43210", "098765 43210" or "9876543210" is found either way.
 * Everything else ("+1...", "00...", partial input) matches on exactly the digits typed.
 */
internal fun contactMatchDigits(typed: String): String {
    val dialed = normalizeDialedNumber(typed).filter { it.isDigit() || it == '+' }
    return if (dialed.startsWith(INDIA_COUNTRY_PREFIX)) {
        dialed.removePrefix(INDIA_COUNTRY_PREFIX)
    } else {
        typed.filter { it.isDigit() }
    }
}

/**
 * Drives the Dialpad's live T9-style match list. Typed digits are debounced before hitting
 * [ContactsRepository] — local contact lookups are fast, but debouncing still avoids re-querying
 * the provider on every single keystroke during fast typing.
 */
class DialpadViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepository = ContactsRepository()
    private val callLogRepository = CallLogRepository(contactsRepository)

    private val _suggestions = MutableStateFlow<List<RecentCallEntry>>(emptyList())

    /**
     * The empty-dialpad "Suggested" list. There's no frequent-contacts query in the app, so this
     * is the most recently called/calling distinct numbers that belong to a saved contact, taken
     * from the newest [SUGGESTION_SCAN_ROWS] call-log rows. Loaded once per screen visit.
     */
    val suggestions: StateFlow<List<RecentCallEntry>> = _suggestions.asStateFlow()

    private val _typedDigits = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val matchingContacts: StateFlow<List<Contact>> = _typedDigits
        .debounce(MATCH_DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .mapLatest { typed ->
            // contactMatchDigits runs libphonenumber parsing too, so it stays off the main
            // thread along with the provider query.
            withContext(Dispatchers.IO) {
                val digits = contactMatchDigits(typed)
                if (digits.isBlank()) {
                    emptyList()
                } else {
                    contactsRepository.getContactsMatchingDigits(getApplication(), digits)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // The screen formats the typed number synchronously while composing, so load
        // libphonenumber's metadata now — well before the user's first key press — rather than
        // on the main thread at that first press.
        viewModelScope.launch(Dispatchers.Default) { prewarmPhoneNumberMetadata() }
        viewModelScope.launch(Dispatchers.IO) {
            val recent = callLogRepository.getRecentCalls(getApplication(), limit = SUGGESTION_SCAN_ROWS, offset = 0).entries
            val picked = mutableListOf<RecentCallEntry>()
            for (entry in recent) {
                if (entry.name == null) continue
                // Loose match, as elsewhere in the app, so "+91 98765 43210" and "09876543210"
                // in the call log count as the same person.
                if (picked.any { PhoneNumberUtils.compare(it.phoneNumber, entry.phoneNumber) }) continue
                picked += entry
                if (picked.size == MAX_SUGGESTIONS) break
            }
            _suggestions.value = picked
        }
    }

    /** Called on every dialpad key press/backspace/clear so [matchingContacts] tracks the field. */
    fun onTypedDigitsChanged(digits: String) {
        _typedDigits.value = digits
    }

    /** Tears down the [android.database.ContentObserver] [ContactsRepository.getContactsMatchingDigits]
     * registers on first use — this ViewModel (and its [ContactsRepository] instance) doesn't
     * outlive the Dialpad screen, so the observer shouldn't either. */
    override fun onCleared() {
        super.onCleared()
        contactsRepository.disposeT9Cache(getApplication())
    }
}
