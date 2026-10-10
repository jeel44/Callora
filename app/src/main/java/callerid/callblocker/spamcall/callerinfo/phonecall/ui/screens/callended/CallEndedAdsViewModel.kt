package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.InlineNativeAdController
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.RemoteConfigManager

private const val TAG = "CallEndedAds"

/**
 * The post-call popup's inline native ad — scoped to CallEndedActivity, so it's created when the
 * popup opens and destroyed in [onCleared] when the popup finishes. Loading starts in init, in
 * parallel with (never gating) the card's own contact lookup; 30s auto-refresh while the popup is
 * resumed, no-flash swaps and fail-open all come from [InlineNativeAdController].
 */
class CallEndedAdsViewModel(application: Application) : AndroidViewModel(application) {

    val nativeAdController = InlineNativeAdController(
        context = application,
        scope = viewModelScope,
        logTag = TAG,
        isPlacementEnabled = { RemoteConfigManager.isCallEndNativeAdEnabled },
        adUnitId = { RemoteConfigManager.callEndNativeAdUnitId },
    )

    init {
        nativeAdController.start()
    }

    override fun onCleared() {
        nativeAdController.clear()
    }
}
