package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallDataPermissions
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
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
            val results = withContext(Dispatchers.IO) { contactsRepository.searchContacts(context, query) }
            _uiState.value = _uiState.value.copy(results = results, isSearching = false)
        }
    }
}
