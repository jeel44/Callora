package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallDataPermissions
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallLogRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUiState(
    val hasContactsPermission: Boolean = false,
    val query: String = "",
    val results: List<Contact> = emptyList(),
    val isSearching: Boolean = false,
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepository = ContactsRepository()
    private val callLogRepository = CallLogRepository(contactsRepository)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        refreshPermission()
    }

    fun refreshPermission() {
        val granted = CallDataPermissions.hasContacts(getApplication())
        _uiState.value = _uiState.value.copy(hasContactsPermission = granted)
        if (!granted) {
            _uiState.value = _uiState.value.copy(results = emptyList())
        } else if (_uiState.value.query.isNotBlank()) {
            runSearch(_uiState.value.query)
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        runSearch(newQuery)
    }

    // Debounced so a full contacts query isn't dispatched on every keystroke — cancelling the
    // pending job on each new character means only the last keystroke's coroutine survives
    // past the delay to actually run the query.
    private fun runSearch(query: String) {
        searchJob?.cancel()
        val context = getApplication<Application>()

        if (!CallDataPermissions.hasContacts(context) || query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), isSearching = false)
            return
        }

        _uiState.value = _uiState.value.copy(isSearching = true)
        searchJob = viewModelScope.launch {
            delay(300)
            val results = withContext(Dispatchers.IO) { searchContactsAndCallLog(context, query) }
            _uiState.value = _uiState.value.copy(results = results, isSearching = false)
        }
    }

    /**
     * Saved contacts first, then any call-log-only numbers matching [query] that aren't already
     * covered by a contact result (build-2 QA #14: "Searching a number from call history, even
     * one reported as spam, gives 'No matches'" — [ContactsRepository.searchContacts] alone only
     * ever looked at the Contacts provider, so a number that was only ever called/received never
     * matched). A call-log-only match has no saved name, so it's represented the same way every
     * other unsaved-number fallback in this codebase is (see [ContactsRepository.searchContacts]'s
     * own `name = name ?: number`): the formatted number standing in for the name.
     */
    private fun searchContactsAndCallLog(context: Application, query: String): List<Contact> {
        val contactResults = contactsRepository.searchContacts(context, query)
        val matchedNumbers = contactResults.map { it.phoneNumber.filter(Char::isDigit) }.toSet()

        val callLogOnlyNumbers = callLogRepository.searchCallLogNumbers(context, query)
            .filter { it.filter(Char::isDigit) !in matchedNumbers }

        val callLogResults = callLogOnlyNumbers.map { number ->
            Contact(
                name = formatPhoneNumberForDisplay(number),
                phoneNumber = number,
                formattedPhoneNumber = formatPhoneNumberForDisplay(number),
                photoUri = null,
            )
        }
        return contactResults + callLogResults
    }
}
