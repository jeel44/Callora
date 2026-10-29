package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding

import android.app.Activity
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.AdFormat
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.AdRevenueLogger
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.InlineNativeAdController
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.awaitAdPlacementEnabled
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.requestNativeAd
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.RemoteConfigManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "OnboardingAdsViewModel"

// Fail-open limits: how long a tap on Next may wait for an ad that's still loading before the
// user is simply moved on without it.
private const val FULLSCREEN_AD_WAIT_MILLIS = 3_000L
private const val INTERSTITIAL_WAIT_MILLIS = 2_000L

/** What Onboarding 1 draws over itself after Next: nothing, a brief spinner while a still-loading
 * full-screen ad gets its [FULLSCREEN_AD_WAIT_MILLIS] chance, or the ad itself. */
sealed interface FullscreenAdState {
    data object Hidden : FullscreenAdState
    data object Loading : FullscreenAdState
    data class Showing(val ad: NativeAd) : FullscreenAdState
}

/**
 * All ads for ONE onboarding intro screen visit — each of ONBOARDING_1/2/3 gets its own instance
 * (scoped to its nav back stack entry), so everything here is created when the page appears and
 * destroyed in [onCleared] when it's popped (Next pops each page; Back out of onboarding too).
 *
 * Every placement is gated by its own Remote Config flag, independently:
 *  - inline native (all 3 pages): loaded in init, then auto-refreshed every 30s while the page
 *    is visible (InlineNativeAdController).
 *  - full-screen native (page 1 only, via [preloadFullscreenAd]/[onPage1Next]).
 *  - interstitial (page 3 only, via [preloadInterstitial]/[onPage3Next]).
 * And every Next path is fail-open: with a flag off, a failed/slow load, or a show that never
 * reports back, the user still always reaches the next screen.
 */
class OnboardingAdsViewModel(application: Application) : AndroidViewModel(application) {

    // Inline slot: loading, 30s auto-refresh while the page is visible, no-flash swaps — see
    // InlineNativeAdController (shared with Language Select).
    val inlineAdController = InlineNativeAdController(
        context = application,
        scope = viewModelScope,
        logTag = TAG,
        isPlacementEnabled = { RemoteConfigManager.isOnboardingNativeAdEnabled },
        adUnitId = { RemoteConfigManager.onboardingNativeAdUnitId },
    )

    private val _fullscreenAdState = MutableStateFlow<FullscreenAdState>(FullscreenAdState.Hidden)
    val fullscreenAdState: StateFlow<FullscreenAdState> = _fullscreenAdState.asStateFlow()

    // Completes with the loaded ad, or null when the placement is off or the load failed.
    private var fullscreenAdResult: CompletableDeferred<NativeAd?>? = null
    private var interstitialResult: CompletableDeferred<InterstitialAd?>? = null

    // Next's continuation, held while an ad step is in progress; run at most once (see proceed).
    private var pendingNext: (() -> Unit)? = null
    private var isInterstitialShowing = false
    private var isCleared = false

    init {
        inlineAdController.start()
        // The full-screen native ad is drawn over this same page (not a separate Activity), so
        // the page stays resumed under it — tell the inline slot it's covered so its 30s
        // refresh doesn't keep requesting ads nobody can see. (Interstitials/App Open run in
        // AdActivity, which pauses the page — NativeAdSlot already handles those.)
        viewModelScope.launch {
            fullscreenAdState.collect { state ->
                inlineAdController.setCovered(state != FullscreenAdState.Hidden)
            }
        }
    }

    /** Page 1: start loading the full-screen ad as soon as the page appears, so it's normally
     * already loaded by the time Next is tapped. */
    fun preloadFullscreenAd() {
        if (fullscreenAdResult != null) return
        val result = CompletableDeferred<NativeAd?>().also { fullscreenAdResult = it }
        viewModelScope.launch {
            if (!awaitAdPlacementEnabled { RemoteConfigManager.isOnboardingFullscreenAdEnabled }) {
                result.complete(null)
                return@launch
            }
            requestNativeAd(
                context = getApplication(),
                adUnitId = RemoteConfigManager.onboardingFullscreenAdUnitId,
                onLoaded = { ad -> if (isCleared || !result.complete(ad)) ad.destroy() },
                onFailed = { error ->
                    Log.w(TAG, "Full-screen native ad failed: ${error.message}")
                    result.complete(null)
                },
                // Top-right is where the overlay's close button sits.
                adChoicesPlacement = NativeAdOptions.ADCHOICES_TOP_LEFT,
            )
        }
    }

    /** Page 1's Next: show the full-screen ad if there is one (the overlay then calls
     * [onFullscreenAdClosed]), otherwise go straight on to [next]. */
    fun onPage1Next(next: () -> Unit) {
        if (pendingNext != null) return // already handling a tap
        pendingNext = next
        val result = fullscreenAdResult
        if (result == null || !RemoteConfigManager.isOnboardingFullscreenAdEnabled) {
            proceed()
            return
        }
        viewModelScope.launch {
            if (!result.isCompleted) _fullscreenAdState.value = FullscreenAdState.Loading
            val ad = withTimeoutOrNull(FULLSCREEN_AD_WAIT_MILLIS) { result.await() }
            if (ad == null) {
                Log.d(TAG, "No full-screen ad within ${FULLSCREEN_AD_WAIT_MILLIS}ms — continuing")
                _fullscreenAdState.value = FullscreenAdState.Hidden
                proceed()
            } else {
                _fullscreenAdState.value = FullscreenAdState.Showing(ad)
            }
        }
    }

    /** The overlay's close button (only reachable after its countdown). The overlay stays drawn
     * until navigation replaces this page, so page 1 never flashes back in between. */
    fun onFullscreenAdClosed() = proceed()

    /** Page 3: preload the interstitial the moment the page appears. */
    fun preloadInterstitial() {
        if (interstitialResult != null) return
        val result = CompletableDeferred<InterstitialAd?>().also { interstitialResult = it }
        viewModelScope.launch {
            if (!awaitAdPlacementEnabled { RemoteConfigManager.isOnboardingInterstitialAdEnabled }) {
                result.complete(null)
                return@launch
            }
            val unitId = RemoteConfigManager.onboardingInterstitialAdUnitId
            InterstitialAd.load(
                getApplication(),
                unitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        ad.setOnPaidEventListener { value ->
                            AdRevenueLogger.log(value, unitId, AdFormat.INTERSTITIAL, ad.responseInfo)
                        }
                        result.complete(ad)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Interstitial failed: ${error.message}")
                        result.complete(null)
                    }
                },
            )
        }
    }

    /** Page 3's Next: show the preloaded interstitial, then [next] once it's dismissed. If it
     * isn't ready within [INTERSTITIAL_WAIT_MILLIS] (or the placement is off), [next] runs
     * without it. */
    fun onPage3Next(activity: Activity?, next: () -> Unit) {
        if (pendingNext != null) return
        pendingNext = next
        val result = interstitialResult
        if (activity == null || result == null || !RemoteConfigManager.isOnboardingInterstitialAdEnabled) {
            proceed()
            return
        }
        viewModelScope.launch {
            val ad = withTimeoutOrNull(INTERSTITIAL_WAIT_MILLIS) { result.await() }
            if (ad == null || isCleared) {
                Log.d(TAG, "No interstitial within ${INTERSTITIAL_WAIT_MILLIS}ms — continuing")
                proceed()
                return@launch
            }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() = proceed()

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "Interstitial failed to show: ${error.message}")
                    proceed()
                }
            }
            isInterstitialShowing = true
            ad.show(activity)
        }
    }

    /** Page 3 resumed. Normally the interstitial's dismiss callback has already moved on — but if
     * its AdActivity was torn down without reporting back (e.g. an incoming call brings
     * singleTask MainActivity to front over it, see AppOpenAdManager), this is what keeps
     * onboarding from being stuck behind an ad that's no longer there. */
    fun onPageResumed() {
        if (isInterstitialShowing) proceed()
    }

    private fun proceed() {
        isInterstitialShowing = false
        val next = pendingNext ?: return
        pendingNext = {} // keeps further taps ignored; the page is about to be replaced
        next()
    }

    @OptIn(ExperimentalCoroutinesApi::class) // getCompleted(), only after isCompleted
    override fun onCleared() {
        isCleared = true
        pendingNext = null
        inlineAdController.clear()
        fullscreenAdResult?.let { result ->
            if (result.isCompleted) result.getCompleted()?.destroy() else result.cancel()
        }
        // InterstitialAd has no destroy(); dropping its callback and our reference releases it.
        interstitialResult?.let { result ->
            if (result.isCompleted) result.getCompleted()?.fullScreenContentCallback = null else result.cancel()
        }
    }
}
