package callerid.callblocker.spamcall.callerinfo.phonecall.ads

import com.google.android.gms.ads.nativead.NativeAd

/** What an inline native ad slot (NativeAdSlot) draws: nothing (placement off, or the load
 * failed — fail-open, the slot just disappears), a shimmer placeholder while the first ad loads,
 * or the ad. A reload with an ad already showing stays [Loaded] with the old ad until the new one
 * arrives, so the shimmer only ever appears before the first ad. */
sealed interface NativeAdSlotState {
    data object Hidden : NativeAdSlotState
    data object Loading : NativeAdSlotState
    data class Loaded(val ad: NativeAd) : NativeAdSlotState
}
