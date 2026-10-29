package callerid.callblocker.spamcall.callerinfo.phonecall.ads

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Same cadence as InlineNativeAdController: measured from the end of the previous request
// (success or failure) or from Home becoming visible again — whichever is later.
private const val REFRESH_INTERVAL_MILLIS = 30_000L

/**
 * Everything about the Home screen's anchored adaptive banner, owned by HomeViewModel and drawn
 * by HomeBannerAdSlot. Same shape as [InlineNativeAdController], adapted to [AdView]:
 *  - the slot state (Hidden / shimmer / banner) — shimmer only before the first banner;
 *  - every load (first or refresh) builds a NEW AdView off-screen; it only replaces the visible
 *    one once it has finished loading, so the current banner stays up until then. The replaced
 *    AdView is destroyed in [onAdBound], once the new one is actually attached on screen;
 *  - at most ONE request in flight — the next refresh is only scheduled once a request resolves;
 *  - a [REFRESH_INTERVAL_MILLIS] auto-refresh that only runs while Home is resumed
 *    ([setScreenResumed], from ON_RESUME/ON_PAUSE in HomeBannerAdSlot); paused otherwise and
 *    restarted as a fresh full countdown when Home is visible again;
 *  - fail-open: a failed first load hides the slot; a failed refresh keeps the current banner and
 *    just tries again next interval.
 *
 * The banner's width comes from the slot itself ([setSlotWidth]), so loading starts once both the
 * placement flag has resolved and the slot has been measured.
 *
 * Main-thread only (ViewModel scope, SDK callbacks, Compose callbacks).
 */
class HomeBannerAdController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val logTag: String,
    private val isPlacementEnabled: () -> Boolean,
    private val adUnitId: () -> String,
) {

    private val _state = MutableStateFlow<BannerAdSlotState>(BannerAdSlotState.Hidden)
    val state: StateFlow<BannerAdSlotState> = _state.asStateFlow()

    private val slotWidthDp = MutableStateFlow<Int?>(null)
    private val retiredAdViews = mutableListOf<AdView>()
    private var pendingAdView: AdView? = null
    private var isStarted = false
    private var isEnabled = false
    private var isScreenResumed = false
    private var isRequestInFlight = false
    private var countdownStartMillis = 0L
    private var refreshJob: Job? = null
    private var requestCount = 0
    private var isCleared = false

    /** Resolves the placement flag, waits for the slot's width, then loads the first banner
     * (a preload — whether or not Home is resumed yet). Call once, from the ViewModel's init. */
    fun start() {
        if (isStarted) return
        isStarted = true
        scope.launch {
            isEnabled = awaitAdPlacementEnabled(isPlacementEnabled)
            if (!isEnabled) return@launch
            slotWidthDp.filterNotNull().first()
            load(reason = "initial")
        }
    }

    /** The slot's measured width in dp — from HomeBannerAdSlot. Later loads pick up a change
     * (e.g. rotation); the banner already showing keeps its size until it's replaced. */
    fun setSlotWidth(widthDp: Int) {
        if (widthDp > 0) slotWidthDp.value = widthDp
    }

    /** Home resumed/paused (or the slot left composition) — from HomeBannerAdSlot. Becoming
     * visible restarts the countdown from now; becoming hidden cancels it. */
    fun setScreenResumed(resumed: Boolean) {
        if (isScreenResumed == resumed) return
        isScreenResumed = resumed
        val current = (_state.value as? BannerAdSlotState.Loaded)?.adView
        if (resumed) {
            current?.resume()
            Log.d(logTag, "Home banner visible - refresh running")
            countdownStartMillis = SystemClock.elapsedRealtime()
            scheduleRefresh()
        } else {
            current?.pause()
            Log.d(logTag, "Home banner hidden - refresh paused")
            refreshJob?.cancel()
            refreshJob = null
        }
    }

    /** HomeBannerAdSlot calls this once [adView] is attached and the banner(s) it replaced are off
     * screen: those are now safe to destroy. */
    fun onAdBound(adView: AdView) {
        val iterator = retiredAdViews.iterator()
        while (iterator.hasNext()) {
            val retired = iterator.next()
            if (retired !== adView) {
                retired.destroy()
                iterator.remove()
            }
        }
    }

    /** From the owning ViewModel's onCleared(). */
    fun clear() {
        isCleared = true
        refreshJob?.cancel()
        pendingAdView?.destroy()
        pendingAdView = null
        (_state.value as? BannerAdSlotState.Loaded)?.adView?.destroy()
        _state.value = BannerAdSlotState.Hidden
        retiredAdViews.forEach(AdView::destroy)
        retiredAdViews.clear()
    }

    private fun load(reason: String) {
        // Re-checked per request so a remote "off" activated mid-session stops further loads.
        if (isCleared || !isPlacementEnabled()) return
        val widthDp = slotWidthDp.value ?: return
        refreshJob?.cancel()
        refreshJob = null
        isRequestInFlight = true
        val requestId = ++requestCount
        val adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
        val unitId = adUnitId()
        Log.d(logTag, "Banner request #$requestId ($reason) started, size=${adSize.width}x${adSize.height}dp")
        if (_state.value == BannerAdSlotState.Hidden) _state.value = BannerAdSlotState.Loading(adSize)

        val adView = AdView(context)
        adView.setAdSize(adSize)
        adView.adUnitId = unitId
        // Fires once per impression, auto-refreshes included.
        adView.setOnPaidEventListener { value ->
            AdRevenueLogger.log(value, unitId, AdFormat.BANNER, adView.responseInfo)
        }
        pendingAdView = adView
        // Only the first load result counts: a banner unit with refresh enabled in the AdMob
        // console would otherwise report its own later reloads through this same listener.
        var isSettled = false
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                if (isSettled) return
                isSettled = true
                pendingAdView = null
                if (isCleared) {
                    adView.destroy()
                    return
                }
                Log.d(logTag, "Banner request #$requestId ($reason) loaded, swapping into slot")
                (_state.value as? BannerAdSlotState.Loaded)?.adView?.let(retiredAdViews::add)
                if (!isScreenResumed) adView.pause()
                _state.value = BannerAdSlotState.Loaded(adView, adSize)
                onRequestFinished()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                if (isSettled) return
                isSettled = true
                pendingAdView = null
                adView.destroy()
                Log.w(logTag, "Banner request #$requestId ($reason) failed: ${error.message}")
                // No banner yet -> hide the slot (never a stuck shimmer); otherwise keep the current one.
                if (_state.value is BannerAdSlotState.Loading) _state.value = BannerAdSlotState.Hidden
                onRequestFinished()
            }
        }
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun onRequestFinished() {
        isRequestInFlight = false
        if (isCleared) return
        countdownStartMillis = SystemClock.elapsedRealtime()
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = null
        // Only refreshes a banner that's showing, on a visible Home, with nothing already in flight.
        if (!isEnabled || isCleared || !isScreenResumed || isRequestInFlight) return
        if (_state.value !is BannerAdSlotState.Loaded) return
        val waitMillis = countdownStartMillis + REFRESH_INTERVAL_MILLIS - SystemClock.elapsedRealtime()
        refreshJob = scope.launch {
            delay(waitMillis.coerceAtLeast(0L))
            load(reason = "refresh")
        }
    }
}
