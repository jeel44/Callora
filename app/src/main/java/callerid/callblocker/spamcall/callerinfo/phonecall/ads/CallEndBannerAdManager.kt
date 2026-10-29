package callerid.callblocker.spamcall.callerinfo.phonecall.ads

import android.app.Application
import android.content.Context
import android.content.MutableContextWrapper
import android.os.SystemClock
import android.util.Log
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.CallEndAdFormat
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.RemoteConfigManager
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallUiState
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "CallEndBannerAd"

// Don't show a banner that has been held longer than this; it's dropped and replaced instead.
private const val AD_EXPIRY_MILLIS = 60 * 60 * 1000L

// The slot is "square sized": full card width, at most this tall (an inline adaptive banner —
// in practice a 300x250-style creative).
private const val MAX_AD_HEIGHT_DP = 250

// A failed preload is retried a few times on its own (a call may end any moment), then left for
// the next trigger — an endless loop on a no-fill would only burn requests.
private val RETRY_DELAYS_MILLIS = longArrayOf(2_000L, 6_000L, 15_000L)

/** One loaded call-end banner. [contextWrapper] is the AdView's own context: it starts out on the
 * Application (there is no Activity while preloading) and is pointed at the Activity that ends
 * up showing it. The holder owns [adView] and must destroy() it. */
class CallEndBanner(val adView: AdView, val contextWrapper: MutableContextWrapper, val heightDp: Int)

/**
 * Keeps ONE call-end banner loaded ahead of time, so the call-ended screen attaches a finished
 * ad on its first frame instead of starting a request once the call is already over.
 *
 * A preload is triggered — each a no-op while a fresh banner is held or a request is in flight:
 *  - at process start (the app opened, or the system started it for a call);
 *  - when a call starts, on every path there is: CallRepository's state while this app is the
 *    default phone app, CalloraCallScreeningService for a screened call, and the phone-state
 *    reports (PostCallMonitor) while it isn't;
 *  - right after a banner is handed out, for the next call.
 * A request in flight is never cancelled: if the call ends first, the screen takes it over via
 * [takeOrAwait] rather than starting a second one.
 *
 * Gated by the call-end placement's Remote Config switch, and only active while its format is
 * banner (see RemoteConfigManager.callEndAdFormat) — [preloadCallEndAd] picks the manager. Main thread
 * only (ad SDK callbacks, [scope], ViewModel init).
 */
object CallEndBannerAdManager {

    private lateinit var application: Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var heldBanner: CallEndBanner? = null
    private var loadedAtMillis = 0L
    private var isLoading = false
    private var waiter: ((CallEndBanner?) -> Unit)? = null

    /** Called once from CalloraApplication.onCreate() (main thread). */
    fun initialize(app: Application) {
        application = app
        preload(reason = "process start")
        scope.launch {
            var wasIdle = true
            CallRepository.callState.collect { info ->
                val isStarting = info.state == CallUiState.DIALING || info.state == CallUiState.RINGING
                if (wasIdle && isStarting) preload(reason = "call started (${info.state})")
                wasIdle = info.state == CallUiState.NONE || info.state == CallUiState.DISCONNECTED
            }
        }
    }

    /** Height the slot reserves before an ad's real size is known. */
    val slotHeightDp: Int get() = MAX_AD_HEIGHT_DP

    /** Fire-and-forget; safe from any thread, any time after [initialize]. */
    fun preload(reason: String) {
        if (!::application.isInitialized) return
        scope.launch {
            if (isLoading || isFresh()) return@launch
            dropHeld()
            isLoading = true
            if (!awaitBannerWanted()) {
                Log.d(TAG, "Preload skipped ($reason): call-end placement is off or set to native")
                finishLoad(null)
                return@launch
            }
            request(reason, attempt = 0)
        }
    }

    private fun request(reason: String, attempt: Int) {
        Log.d(TAG, "Preload started ($reason, attempt ${attempt + 1})")
        val wrapper = MutableContextWrapper(application)
        val widthDp = (application.resources.displayMetrics.widthPixels / application.resources.displayMetrics.density).toInt()
        val adView = AdView(wrapper)
        val unitId = RemoteConfigManager.callEndBannerAdUnitId
        adView.adUnitId = unitId
        // Fires once per impression, auto-refreshes included.
        adView.setOnPaidEventListener { value ->
            AdRevenueLogger.log(value, unitId, AdFormat.BANNER, adView.responseInfo)
        }
        adView.setAdSize(AdSize.getInlineAdaptiveBannerAdSize(widthDp, MAX_AD_HEIGHT_DP))
        adView.adListener = object : AdListener() {
            // Only this request's first result is ours to act on. The same listener keeps firing
            // for the banner's own auto-refreshes while it is on screen — those happen in place
            // and must neither be handed out again nor destroy a banner that is showing.
            private var settled = false

            override fun onAdLoaded() {
                if (settled) return
                settled = true
                Log.d(TAG, "Preload loaded ($reason)")
                val heightDp = adView.adSize?.height?.takeIf { it > 0 } ?: MAX_AD_HEIGHT_DP
                finishLoad(CallEndBanner(adView, wrapper, heightDp))
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                if (settled) return
                settled = true
                Log.w(TAG, "Preload failed ($reason, attempt ${attempt + 1}): ${error.message}")
                adView.destroy()
                val retryDelay = RETRY_DELAYS_MILLIS.getOrNull(attempt)
                if (retryDelay == null) {
                    finishLoad(null)
                } else {
                    scope.launch {
                        delay(retryDelay)
                        request(reason, attempt + 1)
                    }
                }
            }
        }
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun finishLoad(banner: CallEndBanner?) {
        isLoading = false
        val pendingWaiter = waiter
        waiter = null
        if (pendingWaiter != null) {
            // The call-ended screen is already up and waiting for exactly this request.
            pendingWaiter(banner)
            if (banner != null) preload(reason = "after hand-over")
        } else if (banner != null) {
            heldBanner = banner
            loadedAtMillis = SystemClock.elapsedRealtime()
        }
    }

    /**
     * For the call-ended screen. Returns the held banner at once when there is one (ownership
     * moves to the caller, who must destroy it). Otherwise returns null and [onReady] is called
     * later, exactly once, with the banner from the request in flight — or from a request started
     * right here — or null if it fails or the placement is off. [cancelAwait] withdraws it.
     */
    fun takeOrAwait(onReady: (CallEndBanner?) -> Unit): CallEndBanner? {
        if (isFresh() && RemoteConfigManager.isCallEndNativeAdEnabled &&
            RemoteConfigManager.callEndAdFormat == CallEndAdFormat.BANNER
        ) {
            val banner = heldBanner
            heldBanner = null
            Log.d(TAG, "Preloaded banner handed over")
            preload(reason = "after hand-over")
            return banner
        }
        dropHeld()
        waiter = onReady
        if (!isLoading) {
            // preload() would see the waiter only after its own launch; start it synchronously so
            // the waiter is guaranteed to be answered.
            isLoading = true
            scope.launch {
                if (!awaitBannerWanted()) {
                    finishLoad(null)
                } else {
                    request(reason = "call-ended screen", attempt = 0)
                }
            }
        }
        return null
    }

    /** The call-ended screen closed before its [takeOrAwait] callback fired: whatever that
     * request loads is kept for the next call instead. */
    fun cancelAwait() {
        waiter = null
    }

    /** Points a banner at the Activity now showing it (ad clicks, dialogs), or back at the
     * Application when that Activity goes away. */
    fun rebind(banner: CallEndBanner, context: Context) {
        banner.contextWrapper.baseContext = context
    }

    // The placement is on AND Remote Config's ad_callend_format says banner (native is
    // NativeAdPreloadManager's job).
    private suspend fun awaitBannerWanted(): Boolean =
        awaitAdPlacementEnabled { RemoteConfigManager.isCallEndNativeAdEnabled } &&
            RemoteConfigManager.callEndAdFormat == CallEndAdFormat.BANNER

    private fun isFresh(): Boolean =
        heldBanner != null && SystemClock.elapsedRealtime() - loadedAtMillis < AD_EXPIRY_MILLIS

    private fun dropHeld() {
        heldBanner?.adView?.destroy()
        heldBanner = null
    }
}

/**
 * "A call is starting — have the call-ended screen's ad ready", for the callers that don't care
 * which format Remote Config picked: each manager skips its own preload unless its format is the
 * selected one, so only one request is ever made.
 */
fun preloadCallEndAd(reason: String) {
    CallEndBannerAdManager.preload(reason)
    NativeAdPreloadManager.preload(reason)
}
