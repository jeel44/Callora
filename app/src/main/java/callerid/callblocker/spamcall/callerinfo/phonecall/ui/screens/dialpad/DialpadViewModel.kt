package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.dialpad

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
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
        .mapLatest { digits ->
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
