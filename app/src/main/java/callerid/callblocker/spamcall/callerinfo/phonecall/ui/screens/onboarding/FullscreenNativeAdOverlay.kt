package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.NativeAdShimmer
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.bindNativeAd
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.rememberNativeAdColors
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.delay

// The close button only appears after this countdown — not skippable from the first frame.
private const val CLOSE_COUNTDOWN_SECONDS = 2

/**
 * Onboarding 1's one-time full-screen native ad, drawn over the page (not a separate Activity).
 * [FullscreenAdState.Loading] is a brief full-screen shimmer (bounded by OnboardingAdsViewModel's fail-open
 * timeout); [FullscreenAdState.Showing] renders the ad with a [CLOSE_COUNTDOWN_SECONDS] countdown
 * in the top-end corner that turns into the close button, which calls [onClose]. Swallows all
 * touches/back presses meant for the page underneath; back acts as close once close is shown.
 */
@Composable
fun FullscreenNativeAdOverlay(state: FullscreenAdState, onClose: () -> Unit) {
    if (state == FullscreenAdState.Hidden) return
    val ad = (state as? FullscreenAdState.Showing)?.ad
    var secondsLeft by remember(ad) { mutableIntStateOf(CLOSE_COUNTDOWN_SECONDS) }
    if (ad != null) {
        LaunchedEffect(ad) {
            while (secondsLeft > 0) {
                delay(1_000)
                secondsLeft--
            }
        }
    }
    val canClose = ad != null && secondsLeft == 0
    BackHandler { if (canClose) onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Blocks taps from reaching Onboarding 1's Next button underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (ad == null) {
            // Full-height version of the inline slot's shimmer, shaped like native_ad_fullscreen.
            NativeAdShimmer(
                mediaHeight = null,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 48.dp),
            )
            return@Box
        }
        FullscreenNativeAdView(ad = ad, modifier = Modifier.fillMaxSize().systemBarsPadding())
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(top = 4.dp, end = 8.dp)
                .size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (canClose) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.onboarding_fullscreen_ad_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = secondsLeft.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenNativeAdView(ad: NativeAd, modifier: Modifier = Modifier) {
    val colors = rememberNativeAdColors()
    val cornerRadiusPx = with(LocalDensity.current) { 14.dp.toPx() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.native_ad_fullscreen, null) as NativeAdView
        },
        update = { view ->
            if (view.tag !== ad) {
                bindNativeAd(view, ad, colors, cornerRadiusPx)
                view.tag = ad
            }
        },
        // The NativeAd itself is destroyed by OnboardingAdsViewModel.onCleared().
        onRelease = { view -> view.destroy() },
    )
}
