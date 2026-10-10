package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.BannerAdSlotState
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.HomeBannerAdController
import com.google.android.gms.ads.AdView

// How long a freshly attached banner sits on top of the one it replaces before that one is
// removed — covers the new AdView's first draw, so a refresh never shows a blank frame.
private const val SWAP_SETTLE_MILLIS = 300L

/**
 * The Home screen's bottom banner slot: nothing for [BannerAdSlotState.Hidden], a shimmer sized
 * to the banner while the first one loads, then the banner. Loading, refresh timing and AdView
 * lifetimes all live in [HomeBannerAdController]; this reports the slot's width, tells it when
 * Home is visible, and tells it when a new banner is on screen.
 */
@Composable
fun HomeBannerAdSlot(controller: HomeBannerAdController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsState()
    // Drives the controller's 30s auto-refresh: runs only while Home is resumed (navigating away
    // pauses its back stack entry, backgrounding pauses the Activity).
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
    // Always composed (zero height while Hidden) so the width is known before the first request.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.toInt()
        LaunchedEffect(widthDp) { controller.setSlotWidth(widthDp) }
        when (val current = state) {
            BannerAdSlotState.Hidden -> Unit
            is BannerAdSlotState.Loading -> BannerAdShimmer(height = current.adSize.height.dp)
            is BannerAdSlotState.Loaded -> BannerAd(
                adView = current.adView,
                height = current.adSize.height.dp,
                onBound = controller::onAdBound,
            )
        }
    }
}

@Composable
private fun BannerAd(adView: AdView, height: Dp, onBound: (AdView) -> Unit) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surface),
        factory = { context -> FrameLayout(context) },
        // A new [adView] is stacked on top of the old one, which is only removed (and then
        // destroyed by the controller) once the new one has had time to draw — no blank frame.
        update = { container ->
            if (container.tag !== adView) {
                container.tag = adView
                (adView.parent as? ViewGroup)?.removeView(adView)
                container.addView(
                    adView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
                val stale = (0 until container.childCount).map(container::getChildAt).filter { it !== adView }
                if (stale.isEmpty()) {
                    onBound(adView)
                } else {
                    container.postDelayed({
                        stale.forEach(container::removeView)
                        onBound(adView)
                    }, SWAP_SETTLE_MILLIS)
                }
            }
        },
        // Only detaches: the AdView is owned (and destroyed) by HomeViewModel's controller, and is
        // re-attached when Home comes back into composition.
        onRelease = { container -> container.removeAllViews() },
    )
}

/** Animated placeholder shaped like a typical anchored banner: icon square, two text lines, CTA
 * pill — same sweep and colors as the native ad shimmer. */
@Composable
private fun BannerAdShimmer(height: Dp) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val brush = rememberAdShimmerBrush(widthPx = constraints.maxWidth.toFloat())
        val iconSize = min(height - 16.dp, 44.dp)
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBlock(brush, RoundedCornerShape(8.dp), Modifier.size(iconSize))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                ShimmerBlock(brush, RoundedCornerShape(4.dp), Modifier.fillMaxWidth(0.7f).height(12.dp))
                Spacer(Modifier.height(6.dp))
                ShimmerBlock(brush, RoundedCornerShape(4.dp), Modifier.fillMaxWidth(0.45f).height(10.dp))
            }
            Spacer(Modifier.width(10.dp))
            ShimmerBlock(brush, RoundedCornerShape(percent = 50), Modifier.width(72.dp).height(30.dp))
        }
    }
}
