package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallDataPermissions
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallLogRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.RecentCallEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "HomeViewModel"

/** Screenful-sized page — small enough that each fetch (including its per-row contact lookups)
 * finishes fast, however many thousands of real call-log rows exist in total. */
private const val PAGE_SIZE = 30

data class HomeUiState(
    val hasCallLogPermission: Boolean = false,
    val ownerName: String? = null,
    val recentCalls: List<RecentCallEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMoreCalls: Boolean = true,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepository = ContactsRepository()
    private val callLogRepository = CallLogRepository()
    private val spamMarkRepository = SpamMarkRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Counts raw provider rows consumed so far (not `recentCalls.size`, which can be smaller —
    // private/unknown-number rows get filtered out of the displayed list but still occupied a
    // row offset in the provider's ordering).
    private var nextOffset = 0

    // Latest snapshot from spamMarkRepository.spamMarkedNumbersFlow, kept up to date by the
    // collector started in init{}. Read synchronously by annotateSpam() when a page loads, so a
    // fresh page is tagged correctly immediately without an extra per-row suspend/IO round trip.
    private var latestSpamNumbers: Set<String> = emptySet()

    init {
        refresh()
        observeSpamMarks()
    }

    /**
     * Marking/unmarking a number as spam anywhere in the app (e.g. Contact Detail's overflow
     * menu) writes through the same DataStore file [SpamMarkRepository.spamMarkedNumbersFlow]
     * reads from, so this Flow emits immediately on that change — collecting it here (rather
     * than only checking isMarkedAsSpam once per row at load time) is what makes the red tint/
     * pill on Home update live instead of requiring an app restart to pick it up.
     */
    private fun observeSpamMarks() {
        viewModelScope.launch {
            spamMarkRepository.spamMarkedNumbersFlow(getApplication()).collect { spamNumbers ->
                latestSpamNumbers = spamNumbers
                val current = _uiState.value.recentCalls
                if (current.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(recentCalls = annotateSpam(current))
                }
            }
        }
    }

    /**
     * Call on ON_RESUME (Home is reached constantly via back-navigation from Contacts/Spam
     * Center/Settings/Contact Detail, and this ViewModel survives all of that — it's scoped to
     * Home's NavBackStackEntry, which stays on the back stack the whole time). Unlike [refresh],
     * this never unconditionally shows the spinner or clears already-loaded data: a full reload
     * is only justified when there's nothing valid on screen yet (no cached data) or permission
     * was just granted; otherwise it tops up the list silently in the background.
     */
    fun onScreenResumed() {
        val context = getApplication<Application>()
        val hasCallLogPermission = CallDataPermissions.hasCallLog(context)
        val state = _uiState.value

        when {
            state.isLoading -> {
                // A load is already in flight (e.g. the very first ON_RESUME right after
                // init{}'s own refresh()) — let it finish instead of racing a second fetch.
                _uiState.value = state.copy(hasCallLogPermission = hasCallLogPermission)
            }
            hasCallLogPermission && !state.hasCallLogPermission -> {
                // Permission was just granted (e.g. via the contextual call-log permission
                // popup) — nothing valid was ever shown yet, so a full spinner-visible load is
                // appropriate here.
                refresh()
            }
            !hasCallLogPermission -> {
                _uiState.value = state.copy(hasCallLogPermission = false)
            }
            state.recentCalls.isEmpty() -> {
                // No cached data yet (the call log was genuinely empty last time, or this really
                // is the first load) — nothing on screen to preserve, so the spinner is fine.
                refresh()
            }
            else -> {
                // Already showing real data: refresh silently so returning to Home never flashes
                // a spinner or an empty list, while still picking up a call made/received while
                // the user was away on another screen.
                silentlyRefreshNewestCalls(context)
            }
        }
    }

    /** Tags each entry with whether its number is in [latestSpamNumbers] — the live snapshot
     * [observeSpamMarks] keeps current, reusing the same [SpamMarkRepository] Contact Detail's
     * "Mark as spam" reads/writes, rather than duplicating spam-detection logic here. Purely a
     * synchronous Set lookup (no IO), so it's cheap to re-run on every page load.
     *
     * Only `.copy()`s (allocates a new instance for) entries whose isSpam actually flips —
     * everything else keeps its existing object reference. Combined with HomeScreen's
     * items(..., key = { it.id }), this is what lets Compose skip recomposing every unaffected
     * row when a single number gets marked/unmarked elsewhere, instead of treating the whole
     * list as changed. */
    private fun annotateSpam(entries: List<RecentCallEntry>): List<RecentCallEntry> =
        entries.map { entry ->
            val shouldBeSpam = spamMarkRepository.normalize(entry.phoneNumber) in latestSpamNumbers
            if (entry.isSpam == shouldBeSpam) entry else entry.copy(isSpam = shouldBeSpam)
        }

    private fun silentlyRefreshNewestCalls(context: Application) {
        viewModelScope.launch {
            val page = withContext(Dispatchers.IO) {
                callLogRepository.getRecentCalls(context, limit = PAGE_SIZE, offset = 0)
            }
            val currentCalls = _uiState.value.recentCalls
            val newestKnownTimestamp = currentCalls.firstOrNull()?.timestamp ?: 0L
            val newEntries = annotateSpam(page.entries.takeWhile { it.timestamp > newestKnownTimestamp })
            if (newEntries.isNotEmpty()) {
                Log.d(TAG, "silentlyRefreshNewestCalls: ${newEntries.size} new call(s) since last load")
                _uiState.value = _uiState.value.copy(recentCalls = newEntries + currentCalls)
            }
        }
    }

    /** Full reload from the first page — the only path that shows the spinner and clears
     * existing data, so only call this for a genuine first load (see [onScreenResumed]) or an
     * explicit user-triggered refresh (e.g. pull-to-refresh). */
    fun refresh() {
        Log.d(TAG, "refresh() called — re-querying the live call log from the first page")
        val context = getApplication<Application>()
        val hasCallLogPermission = CallDataPermissions.hasCallLog(context)
        nextOffset = 0
        _uiState.value = _uiState.value.copy(
            hasCallLogPermission = hasCallLogPermission,
            isLoading = true,
            recentCalls = emptyList(),
            hasMoreCalls = true,
        )

        viewModelScope.launch {
            val ownerName = withContext(Dispatchers.IO) { contactsRepository.getOwnerDisplayName(context) }
            val page = if (hasCallLogPermission) {
                withContext(Dispatchers.IO) { callLogRepository.getRecentCalls(context, limit = PAGE_SIZE, offset = 0) }
            } else {
                null
            }
            nextOffset = page?.rawRowCount ?: 0
            Log.d(TAG, "refresh() loaded first page: ${page?.entries?.size ?: 0} entries")
            val recentCalls = page?.entries?.let { annotateSpam(it) } ?: emptyList()
            _uiState.value = _uiState.value.copy(
                hasCallLogPermission = hasCallLogPermission,
                ownerName = ownerName,
                recentCalls = recentCalls,
                isLoading = false,
                hasMoreCalls = (page?.rawRowCount ?: 0) == PAGE_SIZE,
            )
        }
    }

    /** Fetches the next page and appends it — called as the user scrolls near the end of the
     * currently-loaded list. No-op if a page is already in flight or the real end of the call
     * log has already been reached. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMoreCalls || !state.hasCallLogPermission) return

        _uiState.value = state.copy(isLoadingMore = true)
        val context = getApplication<Application>()
        val offset = nextOffset
        viewModelScope.launch {
            val page = withContext(Dispatchers.IO) {
                callLogRepository.getRecentCalls(context, limit = PAGE_SIZE, offset = offset)
            }
            nextOffset = offset + page.rawRowCount
            Log.d(TAG, "loadMore() loaded page at offset=$offset: ${page.entries.size} entries, rawRowCount=${page.rawRowCount}")
            val newEntries = annotateSpam(page.entries)
            _uiState.value = _uiState.value.copy(
                recentCalls = _uiState.value.recentCalls + newEntries,
                isLoadingMore = false,
                hasMoreCalls = page.rawRowCount == PAGE_SIZE,
            )
        }
    }
}
