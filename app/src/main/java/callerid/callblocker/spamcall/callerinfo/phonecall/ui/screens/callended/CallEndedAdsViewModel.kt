package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.CallEndBanner
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.CallEndBannerAdManager
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.InlineNativeAdController
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.NativeAdPreloadManager
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.CallEndAdFormat
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.RemoteConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the call-ended screen's ad slot draws. [Loading] reserves the slot's height so the card
 * above it never jumps when the banner arrives. */
sealed interface CallEndBannerState {
    data object Hidden : CallEndBannerState
    data class Loading(val heightDp: Int) : CallEndBannerState
    data class Loaded(val banner: CallEndBanner) : CallEndBannerState
}

private const val TAG = "CallEndedAds"

/**
 * The call-ended screen's ad — scoped to CallEndedActivity, so it lives exactly as long as the
 * popup. Remote Config's `ad_callend_format` picks ONE of two formats when the popup opens:
 *  - banner (default): [bannerState]. Normally [CallEndBannerAdManager] already holds a loaded
 *    banner (preloaded when the call started) and the state is [CallEndBannerState.Loaded] from
 *    the first frame; otherwise the slot is reserved and filled when the manager's request lands,
 *    or collapsed if it fails.
 *  - native: [nativeAdController], started with [NativeAdPreloadManager]'s preloaded ad when
 *    there is one, else making its own live request (shimmer, fail-open).
 * The other format's state stays idle/hidden.
 */
class CallEndedAdsViewModel(application: Application) : AndroidViewModel(application) {

    val format: CallEndAdFormat = RemoteConfigManager.callEndAdFormat

    val nativeAdController = InlineNativeAdController(
        context = application,
        scope = viewModelScope,
        logTag = TAG,
        isPlacementEnabled = { RemoteConfigManager.isCallEndNativeAdEnabled },
        adUnitId = { RemoteConfigManager.callEndNativeAdUnitId },
    )

    private val _bannerState = MutableStateFlow<CallEndBannerState>(CallEndBannerState.Hidden)
    val bannerState: StateFlow<CallEndBannerState> = _bannerState.asStateFlow()

    private var banner: CallEndBanner? = null

    init {
        when (format) {
            CallEndAdFormat.NATIVE -> {
                val preloadedAd = NativeAdPreloadManager.consumePreloadedAd()
                nativeAdController.start(preloadedAd)
                if (preloadedAd != null) NativeAdPreloadManager.preload(reason = "after consume")
            }
            CallEndAdFormat.BANNER -> requestBanner()
        }
    }

    /** Another call ended while the popup was still up: that call's screen gets its own ad. */
    fun refreshForNewCall() {
        when (format) {
            CallEndAdFormat.NATIVE -> nativeAdController.reload()
            CallEndAdFormat.BANNER -> {
                releaseBanner()
                requestBanner()
            }
        }
    }

    private fun requestBanner() {
        val preloaded = CallEndBannerAdManager.takeOrAwait(onReady = ::onBannerReady)
        when {
            preloaded != null -> onBannerReady(preloaded)
            // Known off already: no point reserving space. (Unknown-yet reads as on here
            // — the manager answers with null and the slot collapses if it turns out off.)
            !RemoteConfigManager.isCallEndNativeAdEnabled -> Unit
            else -> _bannerState.value = CallEndBannerState.Loading(CallEndBannerAdManager.slotHeightDp)
        }
    }

    private fun onBannerReady(ready: CallEndBanner?) {
        banner = ready
        _bannerState.value = if (ready != null) CallEndBannerState.Loaded(ready) else CallEndBannerState.Hidden
    }

    private fun releaseBanner() {
        CallEndBannerAdManager.cancelAwait()
        banner?.let {
            CallEndBannerAdManager.rebind(it, getApplication())
            it.adView.destroy()
        }
        banner = null
        _bannerState.value = CallEndBannerState.Hidden
    }

    override fun onCleared() {
        nativeAdController.clear()
        if (format == CallEndAdFormat.BANNER) releaseBanner()
    }
}
