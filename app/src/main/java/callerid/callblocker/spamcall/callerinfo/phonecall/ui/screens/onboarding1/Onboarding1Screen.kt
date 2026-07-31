package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.onboarding1

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.OnboardingScaffold
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun Onboarding1Screen(onNext: () -> Unit, modifier: Modifier = Modifier) {
    OnboardingScaffold(
        pageCount = 3,
        activePage = 0,
        headline = stringResource(R.string.onboarding_1_headline),
        subtitle = stringResource(R.string.onboarding_1_subtitle),
        buttonLabel = stringResource(R.string.onboarding_button_next),
        onButtonClick = onNext,
        modifier = modifier,
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
}
