package callerid.callblocker.spamcall.callerinfo.phonecall.ads

import android.os.Bundle
import android.util.Log
import callerid.callblocker.spamcall.callerinfo.phonecall.analytics.AnalyticsHelper
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.ResponseInfo
import com.google.firebase.analytics.FirebaseAnalytics

// The single event Google Ads should import as the tROAS conversion: one per paid impression,
// across AdMob AND Ad Manager units, so it never double counts whatever else is linked.
private const val EVENT_AD_REVENUE = "ad_revenue"

private const val TAG = "AD_REVENUE"

private const val PLATFORM_ADMOB = "AdMob"
private const val PLATFORM_AD_MANAGER = "Google Ad Manager"

/** Ad format names reported in the `ad_format` param. */
object AdFormat {
    const val APP_OPEN = "app_open"
    const val INTERSTITIAL = "interstitial"
    const val NATIVE = "native"
    const val BANNER = "banner"
}

/**
 * Impression-level ad revenue -> Firebase Analytics, for tROAS bidding. Called from every ad's
 * OnPaidEventListener. Each placement's unit id comes from Remote Config and can be either an
 * AdMob unit ("ca-app-pub-…") or an Ad Manager unit ("/networkCode/…"); the SDK and the paid
 * event are the same for both, so the platform is told apart by the id alone.
 *
 * Two events are logged:
 * - [EVENT_AD_REVENUE] for every unit — the one to mark as a conversion in Google Ads.
 * - Firebase's standard `ad_impression` for Ad Manager units only, so they show up in GA4's ad
 *   revenue reports. AdMob units are left out of it because linking the AdMob app to Firebase
 *   already makes the SDK log `ad_impression` for them; logging it here too would double them.
 */
object AdRevenueLogger {

    fun log(adValue: AdValue, adUnitId: String, adFormat: String, responseInfo: ResponseInfo?) {
        val isAdManager = adUnitId.startsWith("/")
        val value = adValue.valueMicros / 1_000_000.0
        val precision = precisionName(adValue.precisionType)
        val source = responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "unknown"
        Log.d(
            TAG,
            "format=$adFormat unit=$adUnitId value=$value currency=${adValue.currencyCode} " +
                "precision=$precision source=$source",
        )
        // Test ads (and some unfilled mediation paths) report $0; they'd only dilute tROAS.
        if (adValue.valueMicros <= 0) return

        val params = Bundle().apply {
            putDouble(FirebaseAnalytics.Param.VALUE, value)
            putString(FirebaseAnalytics.Param.CURRENCY, adValue.currencyCode)
            putString(FirebaseAnalytics.Param.AD_PLATFORM, if (isAdManager) PLATFORM_AD_MANAGER else PLATFORM_ADMOB)
            putString(FirebaseAnalytics.Param.AD_SOURCE, source)
            putString(FirebaseAnalytics.Param.AD_FORMAT, adFormat)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            putString("precision", precision)
        }
        // Runs inside the ad SDK's paid-event callback: a logging failure must never reach the ad.
        safeLogEvent(EVENT_AD_REVENUE, params)
        if (isAdManager) safeLogEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params)
    }

    private fun safeLogEvent(name: String, params: Bundle) {
        try {
            AnalyticsHelper.logEvent(name, params)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log $name", e)
        }
    }

    private fun precisionName(type: Int): String = when (type) {
        AdValue.PrecisionType.PRECISE -> "precise"
        AdValue.PrecisionType.PUBLISHER_PROVIDED -> "publisher_provided"
        AdValue.PrecisionType.ESTIMATED -> "estimated"
        else -> "unknown"
    }
}
