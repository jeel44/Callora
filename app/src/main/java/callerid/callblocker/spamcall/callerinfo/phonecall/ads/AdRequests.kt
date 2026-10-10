package callerid.callblocker.spamcall.callerinfo.phonecall.ads

import android.content.Context
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.RemoteConfigManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

// Upper bound on waiting for this launch's Remote Config fetch when the last-activated values
// say a placement is off (e.g. first-ever launch, where onboarding runs right after startup).
private const val REMOTE_CONFIG_WAIT_MILLIS = 10_000L

/**
 * Shared "is this placement on?" decision for screen-level ads (Language Select, onboarding) —
 * the same two-step read AppOpenAdManager uses: last-activated values first (disk, no network) so
 * an enabled placement can start loading immediately, then this launch's fetch only if those
 * said off. Also waits for the SDK, so a true result means it's safe to request right away.
 * [isEnabled] is re-read after each step since the fetch activates mid-call.
 */
suspend fun awaitAdPlacementEnabled(isEnabled: () -> Boolean): Boolean {
    MobileAdsInitializer.awaitReady()
    RemoteConfigManager.awaitActivatedValues()
    if (isEnabled()) return true
    RemoteConfigManager.awaitInitialFetch(REMOTE_CONFIG_WAIT_MILLIS)
    return isEnabled()
}

/** One Native Advanced request. Exactly one of [onLoaded]/[onFailed] is called, on the main
 * thread. The caller owns the loaded [NativeAd] and must destroy() it. */
fun requestNativeAd(
    context: Context,
    adUnitId: String,
    onLoaded: (NativeAd) -> Unit,
    onFailed: (LoadAdError) -> Unit,
    adChoicesPlacement: Int = NativeAdOptions.ADCHOICES_TOP_RIGHT,
) {
    AdLoader.Builder(context.applicationContext, adUnitId)
        .forNativeAd(onLoaded)
        .withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) = onFailed(error)
        })
        .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(adChoicesPlacement).build())
        .build()
        .loadAd(AdRequest.Builder().build())
}
