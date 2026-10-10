package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding1

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.OnboardingScaffold
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.NativeAdSlot
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding.OnboardingAdsViewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding.FullscreenNativeAdOverlay
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun Onboarding1Screen(
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    adsViewModel: OnboardingAdsViewModel = viewModel(),
) {
    val fullscreenAdState by adsViewModel.fullscreenAdState.collectAsState()
    // Preloaded on arrival so it's normally ready the moment Next is tapped.
    LaunchedEffect(Unit) { adsViewModel.preloadFullscreenAd() }

    Box(modifier = modifier.fillMaxSize()) {
        OnboardingScaffold(
            pageCount = 3,
            activePage = 0,
            headline = stringResource(R.string.onboarding_1_headline),
            subtitle = stringResource(R.string.onboarding_1_subtitle),
            buttonLabel = stringResource(R.string.onboarding_button_next),
            // Next goes through the one-time full-screen ad (only after THIS page) — fail-open: with
            // the placement off or no ad in time it's exactly the old onNext.
            onButtonClick = { adsViewModel.onPage1Next(onNext) },
            adSlot = { NativeAdSlot(controller = adsViewModel.inlineAdController) },
        ) {
            val composition by rememberLottieComposition(LottieCompositionSpec.Asset("onb_caller_id.json"))
            val progress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever,
            )
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.size(160.dp),
            )
        }
        FullscreenNativeAdOverlay(state = fullscreenAdState, onClose = adsViewModel::onFullscreenAdClosed)
    }
}
