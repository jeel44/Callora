package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.allcontacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallDataPermissions
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsPagingSource
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SEARCH_DEBOUNCE_MILLIS = 300L
private const val CONTACTS_PAGE_SIZE = 40

data class AllContactsUiState(
    val hasContactsPermission: Boolean = false,
    val query: String = "",
    // Letter -> approximate item index for the A-Z bar, built once per list load/permission
    // grant by ContactsRepository.getAlphabetIndex (see AllContactsViewModel.loadAlphabetIndex).
    val letterIndices: Map<String, Int> = emptyMap(),
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

class AllContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepository = ContactsRepository()

    private val _uiState = MutableStateFlow(AllContactsUiState())
    val uiState: StateFlow<AllContactsUiState> = _uiState.asStateFlow()

    private val _query = MutableStateFlow("")

    /**
     * Paged, provider-backed contact stream — replaces the old full-list-in-memory load. Each
     * (debounced) query rebuilds the [Pager] with a fresh [ContactsPagingSource] bound to that
     * query; [flatMapLatest] cancels the superseded Pager's collection, same cancel-on-keystroke
     * intent as [callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.search.SearchViewModel]'s
     * job-cancelling debounce.
     *
     * The debounce is skipped for the initial empty query and whenever the query is cleared back
     * to blank ([transformLatest] only delays non-empty queries), so first load and "back to full
     * list" both feel instant — only actually-typed filter characters wait out the 300ms window.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedContacts: Flow<PagingData<Contact>> = _query
        .transformLatest { query ->
            if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
            emit(query)
        }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            Pager(
                config = PagingConfig(pageSize = CONTACTS_PAGE_SIZE, enablePlaceholders = false),
                pagingSourceFactory = { ContactsPagingSource(getApplication(), contactsRepository, query) },
            ).flow
        }
        .cachedIn(viewModelScope)

    init {
        checkPermission()
    }

    /**
     * Re-checks permission — call again on ON_RESUME so a grant made via the contextual contacts
     * permission popup (or system settings) is picked up without recreating the screen. Also
     * reloads the A-Z index so it reflects any contacts added/removed while the app was away.
     * The paged contact list itself is refreshed separately, via
     * `androidx.paging.compose.LazyPagingItems.refresh()` on the UI side — the paging-native
     * equivalent of the old manual `refresh()`.
     */
    fun checkPermission() {
        val hasPermission = CallDataPermissions.hasContacts(getApplication())
        _uiState.value = _uiState.value.copy(hasContactsPermission = hasPermission)
        if (hasPermission) loadAlphabetIndex()
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        _query.value = newQuery
    }

    /**
     * Builds the A-Z bar's letter -> approximate item-index map. [ContactsContract]'s query()
     * interface has no server-side GROUP BY/DISTINCT (a ContentProvider only exposes structured
     * query() calls, not raw SQL, so there's no way to ask it directly for "first row-index per
     * starting letter"). The lightweight alternative — see
     * [ContactsRepository.getAlphabetIndex]'s kdoc — is a single streaming pass over just the
     * DISPLAY_NAME column, run once here (not per keystroke, not per scroll) off the main thread.
     */
    private fun loadAlphabetIndex() {
        viewModelScope.launch {
            val indices = withContext(Dispatchers.IO) { contactsRepository.getAlphabetIndex(getApplication()) }
            _uiState.value = _uiState.value.copy(letterIndices = indices)
        }
    }
}
