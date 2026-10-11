package callerid.callblocker.spamcall.callerinfo.phonecall.ads

import android.app.Application
import android.os.SystemClock
import android.util.Log
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.RemoteConfigManager
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallUiState
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "NativeAdPreload"

// Google's guidance for native ads: don't show one that has been held for more than an hour.
private const val AD_EXPIRY_MILLIS = 60 * 60 * 1000L

/**
 * Preloads the post-call popup's native ad while a call is in progress, so CallEndedActivity can
 * show it immediately instead of starting a request (and a shimmer) after the call has ended.
 *
 *  - A preload starts when a call starts: [CallRepository.callState] moving from idle (NONE or
 *    DISCONNECTED — DISCONNECTED lingers until the next reset(), so a back-to-back call goes
 *    straight DISCONNECTED -> RINGING/DIALING and still counts) into DIALING or RINGING.
 *  - Gated by Remote Config's `ad_callend_native_enabled` via [awaitAdPlacementEnabled], same as
 *    the popup's own live request.
 *  - Holds at most ONE ad, for at most [AD_EXPIRY_MILLIS]. A fresh held ad (or a request already
 *    in flight) makes a new trigger a no-op; a stale one is destroyed and replaced.
 *  - An in-flight request is never cancelled: if the call ends before it lands, the ad is still
 *    stored, for the next call-end to use.
 *  - [consumePreloadedAd] hands the ad over (ownership moves to the caller, who must destroy it).
 *
 * The held ad lives as long as the process; there is no teardown callback for a process being
 * killed, and the OS reclaims it with the process. Every ad this object drops (stale or replaced)
 * is destroyed here.
 *
 * Only ever touched from the main thread (collector, ad SDK callbacks, [scope], ViewModel init).
 */
object NativeAdPreloadManager {

    private lateinit var application: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var preloadedAd: NativeAd? = null
    private var adLoadedAtMillis = 0L
    private var isLoadingAd = false

    /** Called once from CalloraApplication.onCreate() (main thread). */
    fun initialize(app: Application) {
        application = app
        scope.launch {
            var wasIdle = true
            CallRepository.callState.collect { info ->
                val isStarting = info.state == CallUiState.DIALING || info.state == CallUiState.RINGING
                if (wasIdle && isStarting) preload(reason = "call started (${info.state})")
                wasIdle = isCallIdle(info.state)
            }
        }
    }

    /** Starts loading the next ad unless a fresh one is already held or a request is in flight.
     * Fire-and-forget; safe to call any time after [initialize]. */
    fun preload(reason: String = "manual") {
        if (isLoadingAd || isAdAvailable()) return
        dropHeldAd()
        isLoadingAd = true
        scope.launch {
            if (!awaitAdPlacementEnabled { RemoteConfigManager.isCallEndNativeAdEnabled }) {
                Log.d(TAG, "Preload skipped ($reason): ad_callend_native_enabled is off")
                isLoadingAd = false
                return@launch
            }
            Log.d(TAG, "Preload started ($reason)")
            requestNativeAd(
                context = application,
                adUnitId = RemoteConfigManager.callEndNativeAdUnitId,
                onLoaded = { ad ->
                    Log.d(TAG, "Preload loaded ($reason)")
                    dropHeldAd()
                    preloadedAd = ad
                    adLoadedAtMillis = SystemClock.elapsedRealtime()
                    isLoadingAd = false
                },
                onFailed = { error ->
                    // No immediate retry: a no-fill would just fail again in a loop. The next call
                    // start (or consume) tries again.
                    Log.w(TAG, "Preload failed ($reason): ${error.message}")
                    isLoadingAd = false
                },
            )
        }
    }

    /** The held ad if it's fresh and the placement is still on — removed from the holder, so the
     * caller now owns it and must destroy() it. Null otherwise (a stale one is destroyed here). */
    fun consumePreloadedAd(): NativeAd? {
        if (!isAdAvailable() || !RemoteConfigManager.isCallEndNativeAdEnabled) {
            dropHeldAd()
            return null
        }
        val ad = preloadedAd
        preloadedAd = null
        Log.d(TAG, "Preloaded ad consumed")
        return ad
    }

    private fun isAdAvailable(): Boolean =
        preloadedAd != null && SystemClock.elapsedRealtime() - adLoadedAtMillis < AD_EXPIRY_MILLIS

    private fun dropHeldAd() {
        preloadedAd?.destroy()
        preloadedAd = null
    }

    // Same idle rule as AppOpenAdManager: DISCONNECTED is CallRepository's terminal state.
    private fun isCallIdle(state: CallUiState): Boolean =
        state == CallUiState.NONE || state == CallUiState.DISCONNECTED
}
