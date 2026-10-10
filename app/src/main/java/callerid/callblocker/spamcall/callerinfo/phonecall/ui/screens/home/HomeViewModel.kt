package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.HomeBannerAdController
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallDataPermissions
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallLogRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.RecentCallsSnapshot
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.RecentCallsSnapshotStore
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.RecentCallEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.RemoteConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

private const val TAG = "HomeViewModel"

// Separate tag so the banner's request/refresh timing can be watched on its own in logcat.
private const val BANNER_TAG = "HomeBannerAd"

/** Screenful-sized page — small enough that each fetch (including its per-row contact lookups)
 * finishes fast, however many thousands of real call-log rows exist in total. */
private const val PAGE_SIZE = 30

/** [isLoading] is true only while there is genuinely nothing to show yet (no in-memory or on-disk
 * snapshot) and the first page is being fetched — the only case HomeScreen shows its full
 * first-load spinner. [isRestoringSnapshot] covers the few milliseconds spent reading the on-disk
 * snapshot after process death, so that read never flashes the spinner either. */
data class HomeUiState(
    val hasCallLogPermission: Boolean = false,
    val ownerName: String? = null,
    val recentCalls: List<RecentCallEntry> = emptyList(),
    val isLoading: Boolean = true,
    val isRestoringSnapshot: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreCalls: Boolean = true,
)

/** One row of Home's call list as drawn: a day header, or a run of consecutive calls with the
 * same number on the same day (newest first), shown as one row with a "(n)" count. */
sealed interface HomeListItem {
    val key: String

    data class DayHeader(val epochDay: Long, val timestamp: Long) : HomeListItem {
        override val key: String get() = "day-$epochDay"
    }

    data class CallGroup(val calls: List<RecentCallEntry>, val isFirstInDay: Boolean) : HomeListItem {
        val latest: RecentCallEntry get() = calls.first()
        override val key: String get() = "call-${latest.id}"
    }
}

/**
 * Display list for [HomeUiState.recentCalls]: spam-marked calls dropped (they're shown in Spam
 * Center instead), then grouped by calendar day and by consecutive same-number runs.
 * [HomeUiState.recentCalls] itself keeps every call, spam included — paging offsets, the
 * "newest known call" check and the snapshot all depend on it mirroring the call log, and
 * unmarking a number brings its calls straight back without a reload.
 */
internal fun buildHomeListItems(calls: List<RecentCallEntry>): List<HomeListItem> {
    val zone = ZoneId.systemDefault()
    val items = mutableListOf<HomeListItem>()
    var currentDay: Long? = null
    var currentGroup = mutableListOf<RecentCallEntry>()
    var isFirstInDay = true

    fun flushGroup() {
        if (currentGroup.isEmpty()) return
        items += HomeListItem.CallGroup(currentGroup, isFirstInDay)
        isFirstInDay = false
        currentGroup = mutableListOf()
    }

    for (call in calls) {
        if (call.isSpam) continue
        val day = Instant.ofEpochMilli(call.timestamp).atZone(zone).toLocalDate().toEpochDay()
        if (day != currentDay) {
            flushGroup()
            currentDay = day
            isFirstInDay = true
            items += HomeListItem.DayHeader(epochDay = day, timestamp = call.timestamp)
        } else if (currentGroup.isNotEmpty() && currentGroup.last().formattedPhoneNumber != call.formattedPhoneNumber) {
            flushGroup()
        }
        currentGroup += call
    }
    flushGroup()
    return items
}

/**
 * Scoped to Home's own NavBackStackEntry (the default viewModel() in the HOME composable), which
 * is correct for in-session navigation — but that entry, and this ViewModel with it, is destroyed
 * whenever MainActivity is (Back out of Home, the system reclaiming a backgrounded Activity) and
 * with the process. Every such reopen used to start from an empty "loading" state and refetch
 * from scratch. Last-known data now lives in [RecentCallsSnapshotStore] instead, so a new
 * instance paints it on its first frame and only refreshes silently underneath.
 */
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

    // True while a first-page load is in flight (including the disk restore before it). Blocks
    // loadMore(): the old code let HomeScreen's infinite-scroll trigger fire loadMore() at offset
    // 0 *during* the initial refresh — with an empty list the "near the end" check is always
    // true — so every open fetched page 0 twice and showed loadMore's footer spinner right under
    // the RECENT header. That footer spinner was the spinner seen on every reopen.
    private var isFetchingFirstPage = false

    // Bumped whenever the list is replaced from page 0, so a loadMore() that was already in
    // flight against the old list's offsets discards its result instead of appending it.
    private var listGeneration = 0

    // Latest snapshot from spamMarkRepository.spamMarkedNumbersFlow, kept up to date by the
    // collector started in init{}. null until that Flow's first emission: annotating against an
    // empty set before then would strip a restored row's red spam tint, only for it to come back.
    private var latestSpamNumbers: Set<String>? = null

    /** Home's bottom banner — shimmer, 30s refresh and AdView lifetimes; drawn by HomeBannerAdSlot. */
    val bannerAdController = HomeBannerAdController(
        context = application,
        scope = viewModelScope,
        logTag = BANNER_TAG,
        isPlacementEnabled = { RemoteConfigManager.isHomeBannerAdEnabled },
        adUnitId = { RemoteConfigManager.homeBannerAdUnitId },
    )

    init {
        bannerAdController.start()
        observeSpamMarks()
        val context = getApplication<Application>()
        val hasCallLogPermission = CallDataPermissions.hasCallLog(context)
        val inMemory = RecentCallsSnapshotStore.memory
        if (hasCallLogPermission && inMemory != null) {
            // Synchronous, so the very first frame already shows the last-known list: either the
            // process outlived the previous Activity (reopened after Back, Activity recreated), or
            // it's a cold start and CalloraApplication's RecentCallsSnapshotStore.preload already
            // promoted the on-disk first page into memory. Page 0 is then reloaded
            // silently behind it (isLoading stays false) so contact-name edits/deleted rows since
            // the snapshot was taken are picked up too, not just newly-logged calls.
            nextOffset = inMemory.nextOffset
            _uiState.value = HomeUiState(
                hasCallLogPermission = true,
                ownerName = inMemory.ownerName,
                recentCalls = inMemory.recentCalls,
                isLoading = false,
                hasMoreCalls = inMemory.hasMoreCalls,
            )
            launchFirstPageLoad()
        } else {
            _uiState.value = HomeUiState(
                hasCallLogPermission = hasCallLogPermission,
                isLoading = true,
                isRestoringSnapshot = hasCallLogPermission,
            )
            launchFirstPageLoad {
                if (!hasCallLogPermission) {
                    withContext(Dispatchers.IO) { RecentCallsSnapshotStore.clear(context) }
                    return@launchFirstPageLoad
                }
                // Cold start where the preload hadn't finished yet: wait for it (no second read)
                // and show its first page while the real first page loads silently behind it.
                val onDisk = withContext(Dispatchers.IO) { RecentCallsSnapshotStore.awaitDiskSnapshot(context) }
                _uiState.value = if (onDisk != null && onDisk.recentCalls.isNotEmpty()) {
                    _uiState.value.copy(
                        ownerName = onDisk.ownerName,
                        recentCalls = onDisk.recentCalls,
                        isLoading = false,
                        isRestoringSnapshot = false,
                    )
                } else {
                    _uiState.value.copy(isRestoringSnapshot = false)
                }
            }
        }
    }

    // Declared after init{} on purpose: property initializers run in source order, so the initial
    // value is built from the snapshot init{} just restored — the first frame still shows the
    // last-known list instead of an empty one.
    /** What HomeScreen's list actually draws — see [buildHomeListItems]. */
    val listItems: StateFlow<List<HomeListItem>> = _uiState
        .map { buildHomeListItems(it.recentCalls) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, buildHomeListItems(_uiState.value.recentCalls))

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
                    saveSnapshot(persistToDisk = false)
                }
            }
        }
    }

    /**
     * Call on ON_RESUME (Home is reached constantly via back-navigation from Contacts/Spam
     * Center/Settings/Contact Detail, and via app switching). Never shows the spinner or clears
     * already-loaded data: it only re-checks permission and tops the list up in the background.
     */
    fun onScreenResumed() {
        val context = getApplication<Application>()
        val hasCallLogPermission = CallDataPermissions.hasCallLog(context)
        val state = _uiState.value

        when {
            !hasCallLogPermission -> {
                _uiState.value = state.copy(hasCallLogPermission = false)
                // Don't keep a copy of the call log around once access to it was revoked.
                viewModelScope.launch(Dispatchers.IO) { RecentCallsSnapshotStore.clear(context) }
            }
            isFetchingFirstPage -> {
                // A first-page load is already in flight (e.g. the very first ON_RESUME right
                // after init{}) — let it finish instead of racing a second fetch.
                _uiState.value = state.copy(hasCallLogPermission = true)
            }
            !state.hasCallLogPermission -> {
                // Permission was just granted (e.g. via the contextual call-log permission
                // popup) — nothing valid was ever shown, so this is a genuine first load.
                refresh()
            }
            state.recentCalls.isEmpty() -> {
                // The call log was empty last time — re-check quietly. This used to call the
                // spinner-showing refresh(), i.e. a spinner on every single resume for anyone
                // with an empty call log.
                launchFirstPageLoad()
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
     * synchronous Set lookup (no IO), so it's cheap to re-run on every page load. Leaves entries
     * untouched until that Flow has emitted at least once.
     *
     * Only `.copy()`s (allocates a new instance for) entries whose isSpam actually flips —
     * everything else keeps its existing object reference. Combined with HomeScreen's
     * items(..., key = { it.id }), this is what lets Compose skip recomposing every unaffected
     * row when a single number gets marked/unmarked elsewhere, instead of treating the whole
     * list as changed. */
    private fun annotateSpam(entries: List<RecentCallEntry>): List<RecentCallEntry> {
        val spamNumbers = latestSpamNumbers ?: return entries
        return entries.map { entry ->
            val shouldBeSpam = spamMarkRepository.normalize(entry.phoneNumber) in spamNumbers
            if (entry.isSpam == shouldBeSpam) entry else entry.copy(isSpam = shouldBeSpam)
        }
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
                // New rows at the top shift every older row's provider offset down — keep the
                // paging cursor roughly on the same next row (loadMore's id de-dup absorbs the
                // remainder when filtered private-number rows make this undercount).
                nextOffset += newEntries.size
                _uiState.value = _uiState.value.copy(recentCalls = newEntries + currentCalls)
                saveSnapshot(persistToDisk = true)
            }
        }
    }

    /** Explicit reload from the first page (call-log permission just granted, or a user-
     * triggered refresh). Shows the spinner only when there's nothing on screen yet; existing
     * rows stay visible until the new page replaces them. */
    fun refresh() {
        Log.d(TAG, "refresh() called — re-querying the live call log from the first page")
        val context = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            hasCallLogPermission = CallDataPermissions.hasCallLog(context),
            isLoading = _uiState.value.recentCalls.isEmpty(),
        )
        launchFirstPageLoad()
    }

    /** Single entry point for (re)loading page 0, so two first-page loads can never overlap and
     * [loadMore] stays blocked for the whole duration. [beforeFetch] runs first, inside the same
     * guarded window (init{} uses it for the disk restore). */
    private fun launchFirstPageLoad(beforeFetch: suspend () -> Unit = {}) {
        if (isFetchingFirstPage) return
        isFetchingFirstPage = true
        viewModelScope.launch {
            try {
                beforeFetch()
                fetchFirstPage()
            } finally {
                isFetchingFirstPage = false
            }
        }
    }

    private suspend fun fetchFirstPage() {
        val context = getApplication<Application>()
        val hasCallLogPermission = CallDataPermissions.hasCallLog(context)
        val ownerName = withContext(Dispatchers.IO) { contactsRepository.getOwnerDisplayName(context) }
        val page = if (hasCallLogPermission) {
            withContext(Dispatchers.IO) { callLogRepository.getRecentCalls(context, limit = PAGE_SIZE, offset = 0) }
        } else {
            null
        }
        Log.d(TAG, "fetchFirstPage() loaded: ${page?.entries?.size ?: 0} entries")
        listGeneration++
        nextOffset = page?.rawRowCount ?: 0
        _uiState.value = _uiState.value.copy(
            hasCallLogPermission = hasCallLogPermission,
            ownerName = ownerName,
            recentCalls = page?.entries?.let { annotateSpam(it) } ?: emptyList(),
            isLoading = false,
            isRestoringSnapshot = false,
            isLoadingMore = false,
            hasMoreCalls = (page?.rawRowCount ?: 0) == PAGE_SIZE,
        )
        if (hasCallLogPermission) saveSnapshot(persistToDisk = true)
    }

    /** Fetches the next page and appends it — called as the user scrolls near the end of the
     * currently-loaded list. No-op if a page is already in flight, page 0 is (re)loading, or the
     * real end of the call log has already been reached. */
    fun loadMore() {
        val state = _uiState.value
        if (isFetchingFirstPage || state.isLoadingMore || !state.hasMoreCalls || !state.hasCallLogPermission) return

        _uiState.value = state.copy(isLoadingMore = true)
        val context = getApplication<Application>()
        val offset = nextOffset
        val generation = listGeneration
        viewModelScope.launch {
            val page = withContext(Dispatchers.IO) {
                callLogRepository.getRecentCalls(context, limit = PAGE_SIZE, offset = offset)
            }
            if (generation != listGeneration) return@launch
            nextOffset = offset + page.rawRowCount
            Log.d(TAG, "loadMore() loaded page at offset=$offset: ${page.entries.size} entries, rawRowCount=${page.rawRowCount}")
            // Defensive de-dup on the provider row id (HomeScreen's LazyColumn key): a call
            // logged/deleted between pages shifts provider offsets, and a repeated key crashes
            // LazyColumn outright.
            val currentCalls = _uiState.value.recentCalls
            val knownIds = currentCalls.mapTo(HashSet()) { it.id }
            val newEntries = annotateSpam(page.entries.filter { it.id !in knownIds })
            _uiState.value = _uiState.value.copy(
                recentCalls = currentCalls + newEntries,
                isLoadingMore = false,
                hasMoreCalls = page.rawRowCount == PAGE_SIZE,
            )
            saveSnapshot(persistToDisk = false)
        }
    }

    /** Memory always (cheap); disk only when the first page changed — loadMore/spam-tint
     * changes don't touch what's persisted. */
    private fun saveSnapshot(persistToDisk: Boolean) {
        val state = _uiState.value
        val snapshot = RecentCallsSnapshot(
            ownerName = state.ownerName,
            recentCalls = state.recentCalls,
            nextOffset = nextOffset,
            hasMoreCalls = state.hasMoreCalls,
        )
        RecentCallsSnapshotStore.update(snapshot)
        if (persistToDisk) {
            val context = getApplication<Application>()
            viewModelScope.launch(Dispatchers.IO) { RecentCallsSnapshotStore.writeToDisk(context, snapshot, PAGE_SIZE) }
        }
    }

    override fun onCleared() {
        bannerAdController.clear()
    }
}
