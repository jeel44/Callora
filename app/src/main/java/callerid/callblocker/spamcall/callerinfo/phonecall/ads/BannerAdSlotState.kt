package callerid.callblocker.spamcall.callerinfo.phonecall.ads

import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** What the Home banner slot (HomeBannerAdSlot) draws: nothing (placement off, or the first load
 * failed — fail-open), a shimmer sized to [adSize] while the first banner loads, or the banner.
 * A refresh with a banner already showing stays [Loaded] with the old AdView until the new one
 * has loaded, so the shimmer only ever appears before the first banner. */
sealed interface BannerAdSlotState {
    data object Hidden : BannerAdSlotState
    data class Loading(val adSize: AdSize) : BannerAdSlotState
    data class Loaded(val adView: AdView, val adSize: AdSize) : BannerAdSlotState
}

/** Height the slot occupies, in dp — 0 when hidden. Shimmer and banner use the same size, so the
 * layout never jumps when one replaces the other. */
val BannerAdSlotState.heightDp: Int
    get() = when (this) {
        BannerAdSlotState.Hidden -> 0
        is BannerAdSlotState.Loading -> adSize.height
        is BannerAdSlotState.Loaded -> adSize.height
    }
