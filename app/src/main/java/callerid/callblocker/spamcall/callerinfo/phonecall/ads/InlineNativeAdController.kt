package callerid.callblocker.spamcall.callerinfo.phonecall.ads

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// How often a visible inline slot swaps in a fresh ad, measured from the end of the previous
// request (success or failure) or from the screen becoming visible again — whichever is later.
private const val REFRESH_INTERVAL_MILLIS = 30_000L

/**
 * Everything about ONE inline native ad slot (Language Select, each onboarding intro screen),
 * owned by that screen's ViewModel and drawn by NativeAdSlot. One place for:
 *  - the slot state (Hidden / shimmer / ad) — shimmer only before the first ad;
 *  - at most ONE request in flight: a [reload] arriving mid-request is queued and runs right
 *    after it, never alongside it;
 *  - a [REFRESH_INTERVAL_MILLIS] auto-refresh that only runs while the slot is actually
 *    visible: its screen is resumed ([setScreenResumed], driven by ON_RESUME/ON_PAUSE in
 *    NativeAdSlot — covers navigating away, backgrounding, and full-screen ads that run in their
 *    own Activity) AND nothing in the same window is drawn over it ([setCovered] — e.g.
 *    onboarding 1's full-screen native overlay, which is Compose on top of the same screen, so
 *    the screen never pauses for it). Paused while either is false; restarted as a fresh full
 *    countdown once both are true again;
 *  - fail-open: a failed first load hides the slot; a failed refresh keeps the current ad and
 *    just tries again next interval;
 *  - no-flash swaps: a replaced ad is only destroyed in [onAdBound], once its replacement is
 *    actually bound on screen (destroying one still displayed would blank the slot).
 *
 * Main-thread only (ViewModel scope, SDK callbacks, Compose lifecycle callbacks).
 */
class InlineNativeAdController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val logTag: String,
    private val isPlacementEnabled: () -> Boolean,
    private val adUnitId: () -> String,
) {

    private val _state = MutableStateFlow<NativeAdSlotState>(NativeAdSlotState.Hidden)
    val state: StateFlow<NativeAdSlotState> = _state.asStateFlow()

    private val retiredAds = mutableListOf<NativeAd>()
    private var isStarted = false
    private var isEnabled = false
    private var isScreenResumed = false
    private var isCovered = false
    private val isVisible: Boolean get() = isScreenResumed && !isCovered
    private var isRequestInFlight = false
    private var isReloadQueued = false
    private var countdownStartMillis = 0L
    private var refreshJob: Job? = null
    private var requestCount = 0
    private var isCleared = false

    /** Resolves the placement flag, then loads the first ad (whether or not the slot is visible
     * yet — it's a preload). Call once, from the ViewModel's init. A non-null [preloadedAd]
     * (already loaded, placement already checked) is shown as the first ad instead — no request,
     * no shimmer; this controller then owns it. Refreshes work the same either way. */
    fun start(preloadedAd: NativeAd? = null) {
        if (isStarted) {
            preloadedAd?.destroy()
            return
        }
        isStarted = true
        if (preloadedAd != null) {
            Log.d(logTag, "Showing preloaded native ad, skipping the initial request")
            isEnabled = true
            _state.value = NativeAdSlotState.Loaded(preloadedAd)
            return
        }
        scope.launch {
            isEnabled = awaitAdPlacementEnabled(isPlacementEnabled)
            if (isEnabled) load(reason = "initial")
        }
    }

    /** A screen-driven fresh ad (e.g. Language Select's selection change). Queued if a request
     * is already in flight, so the slot never has two overlapping requests. */
    fun reload() {
        if (!isEnabled || isCleared) return
        if (isRequestInFlight) {
            isReloadQueued = true
            return
        }
        load(reason = "manual")
    }

    /** The slot's screen resumed/paused (or the slot left composition) — from NativeAdSlot. */
    fun setScreenResumed(resumed: Boolean) {
        if (isScreenResumed == resumed) return
        val wasVisible = isVisible
        isScreenResumed = resumed
        onVisibilityChanged(wasVisible)
    }

    /** Something in the same window is drawn over the slot (or stopped being) — from the owning
     * ViewModel, which knows about its own overlays. */
    fun setCovered(covered: Boolean) {
        if (isCovered == covered) return
        val wasVisible = isVisible
        isCovered = covered
        Log.d(logTag, "Inline slot ${if (covered) "covered" else "uncovered"} - refresh ${if (isVisible) "running" else "paused"}")
        onVisibilityChanged(wasVisible)
    }

    // Becoming visible restarts the countdown from now; becoming hidden cancels it.
    private fun onVisibilityChanged(wasVisible: Boolean) {
        if (isVisible == wasVisible) return
        if (isVisible) {
            countdownStartMillis = SystemClock.elapsedRealtime()
            scheduleRefresh()
        } else {
            refreshJob?.cancel()
            refreshJob = null
        }
    }

    /** NativeAdSlot calls this once [ad] is bound to its view: the ads it replaced are now safe to
     * destroy. */
    fun onAdBound(ad: NativeAd) {
        val iterator = retiredAds.iterator()
        while (iterator.hasNext()) {
            val retired = iterator.next()
            if (retired !== ad) {
                retired.destroy()
                iterator.remove()
            }
        }
    }

    /** From the owning ViewModel's onCleared(). */
    fun clear() {
        isCleared = true
        refreshJob?.cancel()
        (_state.value as? NativeAdSlotState.Loaded)?.ad?.destroy()
        _state.value = NativeAdSlotState.Hidden
        retiredAds.forEach(NativeAd::destroy)
        retiredAds.clear()
    }

    private fun load(reason: String) {
        // Re-checked per request so a remote "off" activated mid-session stops further loads.
        if (isCleared || !isPlacementEnabled()) return
        refreshJob?.cancel()
        refreshJob = null
        isRequestInFlight = true
        val requestId = ++requestCount
        Log.d(logTag, "Native ad request #$requestId ($reason) started")
        if (_state.value == NativeAdSlotState.Hidden) _state.value = NativeAdSlotState.Loading
        requestNativeAd(
            context = context,
            adUnitId = adUnitId(),
            onLoaded = { ad ->
                if (isCleared) {
                    ad.destroy()
                } else {
                    Log.d(logTag, "Native ad request #$requestId ($reason) loaded, swapping into slot")
                    (_state.value as? NativeAdSlotState.Loaded)?.ad?.let(retiredAds::add)
                    _state.value = NativeAdSlotState.Loaded(ad)
                }
                onRequestFinished()
            },
            onFailed = { error ->
                Log.w(logTag, "Native ad request #$requestId ($reason) failed: ${error.message}")
                // No ad yet -> hide the slot (never a stuck shimmer); otherwise keep the current ad.
                if (_state.value == NativeAdSlotState.Loading) _state.value = NativeAdSlotState.Hidden
                onRequestFinished()
            },
        )
    }

    private fun onRequestFinished() {
        isRequestInFlight = false
        if (isCleared) return
        if (isReloadQueued) {
            isReloadQueued = false
            load(reason = "manual")
            return
        }
        countdownStartMillis = SystemClock.elapsedRealtime()
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = null
        // Only refreshes an ad that's showing, on a visible slot, with nothing already in flight.
        if (!isEnabled || isCleared || !isVisible || isRequestInFlight) return
        if (_state.value !is NativeAdSlotState.Loaded) return
        val waitMillis = countdownStartMillis + REFRESH_INTERVAL_MILLIS - SystemClock.elapsedRealtime()
        refreshJob = scope.launch {
            delay(waitMillis.coerceAtLeast(0L))
            load(reason = "refresh")
        }
    }
}
