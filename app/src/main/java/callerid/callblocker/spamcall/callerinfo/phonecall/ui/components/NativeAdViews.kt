package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.InlineNativeAdController
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.NativeAdSlotState
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

// native_ad_large.xml's non-media rows, split by what can be dropped when space is short. Text
// line heights are in sp so the fit check tracks the user's font scale (measured at scale 1.0
// on-device: 18dp body line, 16dp rating/store line - the template's total non-media height
// there is exactly 160dp).
private val CardPadding = 10.dp
private val TemplateRequiredHeight = CardPadding * 2 + 36.dp + 8.dp + 8.dp + 40.dp // header, media gap, CTA gap + CTA
private val BodyGap = 8.dp
private val BodyLineHeight = 18.sp
private val StoreRowGap = 6.dp
private val StoreRowLineHeight = 16.sp
private val MinMediaHeight = AdMediaFrameLayout.MIN_MEDIA_HEIGHT_DP.dp

/**
 * How much of the large template a slot has room for. AdMob requires the headline, CTA, icon,
 * attribution and AdChoices (all in the required part), only recommends the body, and treats
 * star rating/store/price as optional (support.google.com/admob/answer/6240809) - so a slot short
 * of the full template drops the rating/store row first, then the body, before giving up.
 *
 * This replaces a single all-or-nothing 280dp cutoff, which sat exactly at the slot height of the
 * one phone it was tuned on (SM-A226B, 856dp tall): any screen with even slightly less height in
 * dp - e.g. an SM-S908N at 823dp, despite being the "bigger" phone - got no ad at all.
 */
private enum class NativeAdFit { Full, NoStoreRow, Minimal }

/** Non-media height of the template at [fit], for the current density and font scale. */
private fun Density.nonMediaHeight(fit: NativeAdFit): Dp {
    var height = TemplateRequiredHeight
    if (fit != NativeAdFit.Minimal) height += BodyGap + BodyLineHeight.toDp()
    if (fit == NativeAdFit.Full) height += StoreRowGap + StoreRowLineHeight.toDp()
    return height
}

/** The fullest [NativeAdFit] whose template (media at AdMob's minimum) fits in [maxHeight], or
 * null if not even [NativeAdFit.Minimal] does. */
private fun Density.fitFor(maxHeight: Dp): NativeAdFit? =
    NativeAdFit.entries.firstOrNull { maxHeight >= nonMediaHeight(it) + MinMediaHeight }

/**
 * The one shared inline native ad slot (Language Select, the three onboarding intro screens, Call
 * Ended): nothing for [NativeAdSlotState.Hidden], a shimmer shaped like the large template while
 * the first ad loads, then the ad itself in the large template. When given a bounded height (e.g.
 * a weighted gap), the media shrinks to fit it (down to AdMob's 120dp minimum) instead of
 * clipping the CTA; below that the optional rows are dropped (see [NativeAdFit]), and only a slot
 * too short for even the minimal template renders nothing rather than a cut-off ad.
 * Loading, refresh timing and ad lifetimes all live in [InlineNativeAdController]; this only
 * draws its state and tells it when the screen is visible and when a new ad is bound.
 */
@Composable
fun NativeAdSlot(controller: InlineNativeAdController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    // Drives the controller's 30s auto-refresh: runs only while this screen is resumed. Covers
    // navigating away (the back stack entry pauses) and backgrounding (the Activity pauses);
    // leaving composition (e.g. Language Select's locale recreate) also counts as hidden.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.setScreenResumed(true)
                Lifecycle.Event.ON_PAUSE -> controller.setScreenResumed(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.setScreenResumed(false)
        }
    }
    NativeAdSlotContent(state = state, modifier = modifier, onBound = controller::onAdBound)
}

@Composable
private fun NativeAdSlotContent(
    state: NativeAdSlotState,
    modifier: Modifier,
    onBound: (NativeAd) -> Unit,
) {
    if (state == NativeAdSlotState.Hidden) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val bounded = constraints.hasBoundedHeight
        val density = LocalDensity.current
        val fit = if (bounded) density.fitFor(maxHeight) ?: return@BoxWithConstraints else NativeAdFit.Full
        when (state) {
            NativeAdSlotState.Loading -> {
                val preferredMedia = (maxWidth - CardPadding * 2) * 9f / 16f
                val mediaHeight = if (bounded) {
                    min(preferredMedia, maxHeight - density.nonMediaHeight(fit))
                } else {
                    preferredMedia
                }
                NativeAdShimmer(
                    mediaHeight = maxOf(mediaHeight, MinMediaHeight),
                    showBody = fit != NativeAdFit.Minimal,
                    showStoreRow = fit == NativeAdFit.Full,
                    modifier = Modifier.clip(ListItemShape).background(MaterialTheme.colorScheme.surface),
                )
            }
            is NativeAdSlotState.Loaded -> LargeNativeAd(ad = state.ad, fit = fit, onBound = onBound)
            NativeAdSlotState.Hidden -> Unit
        }
    }
}

@Composable
private fun LargeNativeAd(ad: NativeAd, fit: NativeAdFit, onBound: (NativeAd) -> Unit) {
    val colors = rememberNativeAdColors()
    val cornerRadiusPx = with(LocalDensity.current) { 10.dp.toPx() }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemShape)
            .background(MaterialTheme.colorScheme.surface),
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.native_ad_large, null) as NativeAdView
        },
        // Swapping [ad] rebinds the SAME view in place, so the old creative stays up until the new
        // one is bound - no blank frame (Language Select's reload-on-selection relies on this).
        update = { view ->
            if (view.tag !== ad) {
                bindNativeAd(view, ad, colors, cornerRadiusPx)
                view.tag = ad
            }
            // After binding (which shows every asset the ad has), and again whenever [fit] changes.
            applyNativeAdFit(view, ad, fit)
            onBound(ad)
        },
        // Releases the view's own ad tracking; the NativeAd itself is owned (and destroyed) by the
        // screen's ViewModel, since it can outlive this view (e.g. Language Select's locale
        // recreate).
        onRelease = { view -> view.destroy() },
    )
}

/** Hides the rows [fit] has no room for; shows the ones the ad provides when there is room. */
private fun applyNativeAdFit(view: NativeAdView, ad: NativeAd, fit: NativeAdFit) {
    val body = view.findViewById<View>(R.id.native_ad_body)
    body.visibility = if (fit != NativeAdFit.Minimal && !ad.body.isNullOrBlank()) View.VISIBLE else View.GONE
    val storeRow = view.findViewById<View>(R.id.native_ad_store_row)
    val hasStoreAssets = listOf(R.id.native_ad_stars, R.id.native_ad_price, R.id.native_ad_store)
        .any { view.findViewById<View>(it).visibility == View.VISIBLE }
    storeRow.visibility = if (fit == NativeAdFit.Full && hasStoreAssets) View.VISIBLE else View.GONE
}

/**
 * Animated placeholder shaped like the large template: header (icon circle + two lines), media
 * block, the body line, the rating/store line, CTA. [mediaHeight] null = the media block takes
 * all remaining height (the full-screen overlay); otherwise sized like the real media will be.
 */
@Composable
fun NativeAdShimmer(
    mediaHeight: Dp?,
    modifier: Modifier = Modifier,
    showBody: Boolean = true,
    showStoreRow: Boolean = true,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val brush = rememberAdShimmerBrush(widthPx = constraints.maxWidth.toFloat())
        Column(modifier = Modifier.padding(CardPadding)) {
            Row(modifier = Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                ShimmerBlock(brush, CircleShape, Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    ShimmerBlock(brush, RoundedCornerShape(4.dp), Modifier.fillMaxWidth(0.6f).height(14.dp))
                    Spacer(Modifier.height(6.dp))
                    ShimmerBlock(brush, RoundedCornerShape(4.dp), Modifier.fillMaxWidth(0.35f).height(10.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            ShimmerBlock(brush, RoundedCornerShape(10.dp), mediaModifier(mediaHeight))
            if (showBody) {
                Spacer(Modifier.height(10.dp))
                ShimmerBlock(brush, RoundedCornerShape(4.dp), Modifier.fillMaxWidth(0.85f).height(13.dp))
            }
            if (showStoreRow) {
                Spacer(Modifier.height(8.dp))
                ShimmerBlock(brush, RoundedCornerShape(4.dp), Modifier.fillMaxWidth(0.4f).height(13.dp))
            }
            Spacer(Modifier.height(10.dp))
            ShimmerBlock(brush, RoundedCornerShape(10.dp), Modifier.fillMaxWidth().height(40.dp))
        }
    }
}

@Composable
private fun ColumnScope.mediaModifier(mediaHeight: Dp?): Modifier =
    if (mediaHeight == null) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(mediaHeight)

/** The animated diagonal sweep every ad shimmer (native and banner) is painted with, sized to a
 * placeholder [widthPx] wide. */
@Composable
internal fun rememberAdShimmerBrush(widthPx: Float): Brush {
    val transition = rememberInfiniteTransition(label = "adShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "adShimmerProgress",
    )
    val base = MaterialTheme.extendedColors.onSurfaceSoft.copy(alpha = 0.12f)
    val highlight = MaterialTheme.extendedColors.onSurfaceSoft.copy(alpha = 0.28f)
    val sweep = widthPx * 3f * progress - widthPx
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(sweep, 0f),
        end = Offset(sweep + widthPx, widthPx * 0.3f),
    )
}

@Composable
internal fun ShimmerBlock(brush: Brush, shape: Shape, modifier: Modifier) {
    Box(modifier = modifier.clip(shape).background(brush))
}

/** Theme colors for the Android-View-based native templates. */
data class NativeAdColors(
    val headline: Int,
    val body: Int,
    val accent: Int,
    val onAccent: Int,
)

@Composable
fun rememberNativeAdColors(): NativeAdColors = NativeAdColors(
    headline = MaterialTheme.colorScheme.onSurface.toArgb(),
    body = MaterialTheme.extendedColors.onSurfaceSoft.toArgb(),
    accent = MaterialTheme.colorScheme.primary.toArgb(),
    onAccent = MaterialTheme.colorScheme.onPrimary.toArgb(),
)

/**
 * Binds [ad] into a native template — native_ad_large.xml or native_ad_fullscreen.xml, which share
 * asset view IDs. Every optional asset (body, icon, advertiser, media, stars, price, store, CTA)
 * is set GONE when the ad doesn't provide it, so no empty gap is left behind.
 */
fun bindNativeAd(view: NativeAdView, ad: NativeAd, colors: NativeAdColors, cornerRadiusPx: Float) {
    val headline = view.findViewById<TextView>(R.id.native_ad_headline)
    val body = view.findViewById<TextView>(R.id.native_ad_body)
    val advertiser = view.findViewById<TextView>(R.id.native_ad_advertiser)
    val icon = view.findViewById<ImageView>(R.id.native_ad_icon)
    val media = view.findViewById<MediaView>(R.id.native_ad_media)
    val stars = view.findViewById<RatingBar>(R.id.native_ad_stars)
    val price = view.findViewById<TextView>(R.id.native_ad_price)
    val store = view.findViewById<TextView>(R.id.native_ad_store)
    val storeRow = view.findViewById<View>(R.id.native_ad_store_row)
    val callToAction = view.findViewById<Button>(R.id.native_ad_call_to_action)
    val attribution = view.findViewById<TextView>(R.id.native_ad_attribution)

    headline.text = ad.headline
    headline.setTextColor(colors.headline)
    body.setTextOrHide(ad.body, colors.body)
    advertiser.setTextOrHide(ad.advertiser, colors.body)

    val iconDrawable = ad.icon?.drawable
    icon.setImageDrawable(iconDrawable)
    icon.visibility = if (iconDrawable == null) View.GONE else View.VISIBLE

    ad.mediaContent?.let { media.mediaContent = it }

    val starRating = ad.starRating
    if (starRating != null && starRating > 0) {
        stars.rating = starRating.toFloat()
        stars.progressTintList = ColorStateList.valueOf(colors.accent)
        stars.visibility = View.VISIBLE
    } else {
        stars.visibility = View.GONE
    }
    price.setTextOrHide(ad.price, colors.body)
    store.setTextOrHide(ad.store, colors.body)
    storeRow.visibility =
        if (stars.visibility == View.GONE && price.visibility == View.GONE && store.visibility == View.GONE) {
            View.GONE
        } else {
            View.VISIBLE
        }

    callToAction.text = ad.callToAction
    callToAction.setTextColor(colors.onAccent)
    callToAction.background = GradientDrawable().apply {
        cornerRadius = cornerRadiusPx
        setColor(colors.accent)
    }
    callToAction.visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE

    attribution.setTextColor(colors.onAccent)
    attribution.background = GradientDrawable().apply {
        cornerRadius = cornerRadiusPx / 2
        setColor(colors.accent)
    }

    view.headlineView = headline
    view.bodyView = body
    view.advertiserView = advertiser
    view.iconView = icon
    view.mediaView = media
    view.starRatingView = stars
    view.priceView = price
    view.storeView = store
    view.callToActionView = callToAction
    // Explicit AdChoices placement (both templates have one); the SDK fills it when the ad has it.
    view.adChoicesView = view.findViewById<AdChoicesView>(R.id.native_ad_choices)
    // Must come last — registers the asset views above for click/impression tracking.
    view.setNativeAd(ad)
}

private fun TextView.setTextOrHide(value: String?, color: Int) {
    text = value
    setTextColor(color)
    visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
}
