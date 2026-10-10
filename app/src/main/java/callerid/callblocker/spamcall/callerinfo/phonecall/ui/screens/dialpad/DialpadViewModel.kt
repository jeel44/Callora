package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.dialpad

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.normalizeDialedNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

private const val MATCH_DEBOUNCE_MILLIS = 150L

private const val INDIA_COUNTRY_PREFIX = "+91"

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

    private val _typedDigits = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val matchingContacts: StateFlow<List<Contact>> = _typedDigits
        .debounce(MATCH_DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .mapLatest { typed ->
            val digits = contactMatchDigits(typed)
            if (digits.isBlank()) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) {
                    contactsRepository.getContactsMatchingDigits(getApplication(), digits)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
