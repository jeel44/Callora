package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding3

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.OnboardingScaffold
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.NativeAdSlot
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding.OnboardingAdsViewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.findActivity
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun Onboarding3Screen(
    onOnboardingFinished: () -> Unit,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
    adsViewModel: OnboardingAdsViewModel = viewModel(),
) {
    val activity = LocalContext.current.findActivity()
    // Preloaded on arrival, not on tap, so it's normally ready by the time Get Started is pressed.
    LaunchedEffect(Unit) { adsViewModel.preloadInterstitial() }
    // Fail-open backstop for an interstitial torn down without its dismiss callback (see
    // OnboardingAdsViewModel.onPageResumed).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) adsViewModel.onPageResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    OnboardingScaffold(
        pageCount = 3,
        activePage = 2,
        headline = stringResource(R.string.onboarding_3_headline),
        subtitle = stringResource(R.string.onboarding_3_subtitle),
        buttonLabel = stringResource(R.string.onboarding_button_get_started),
        // Completion is saved on the tap itself (so a kill mid-interstitial can't replay
        // onboarding); then the interstitial (if on and ready within 2s), then onGetStarted.
        onButtonClick = {
            onOnboardingFinished()
            adsViewModel.onPage3Next(activity, onGetStarted)
        },
        modifier = modifier,
        adSlot = { NativeAdSlot(controller = adsViewModel.inlineAdController) },
    ) {
        val composition by rememberLottieComposition(LottieCompositionSpec.Asset("onb_privacy.json"))
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
}
