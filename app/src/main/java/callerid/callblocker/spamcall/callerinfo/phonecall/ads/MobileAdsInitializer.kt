package callerid.callblocker.spamcall.callerinfo.phonecall.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "MobileAdsInitializer"

/**
 * Runs [MobileAds.initialize] exactly once per process, OFF the main thread (Google's guidance —
 * initialization does disk/IPC work that would otherwise delay the first frame), and exposes
 * [awaitReady] so ad loaders wait for it instead of racing it.
 */
object MobileAdsInitializer {

    private val ready = CompletableDeferred<Unit>()
    private var started = false

    /** [backgroundScope] must be on a background dispatcher — see CalloraApplication. */
    fun initialize(context: Context, backgroundScope: CoroutineScope) {
        if (started) return
        started = true
        val appContext = context.applicationContext
        backgroundScope.launch {
            MobileAds.initialize(appContext) {
                Log.d(TAG, "MobileAds initialized")
                ready.complete(Unit)
            }
        }
    }

    suspend fun awaitReady() = ready.await()
}
