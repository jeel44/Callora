package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.ActiveCallUiState
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallUiState
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.navigation.Routes
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.formatCallTimer
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import kotlinx.coroutines.delay

private val BannerGradientStart = Color(0xFF17C471)
private val BannerGradientEnd = Color(0xFF12A25E)

/**
 * App-wide "call still running in the background" affordance. Renders nothing when no call is
 * in progress, and is deliberately not shown over [Routes.ACTIVE_CALL]/[Routes.OUTGOING_DIALING]
 * (both already show full call info) or [Routes.INCOMING_CALL] (a ringing incoming call isn't
 * "ongoing" yet — and already forces its own navigation regardless of this banner).
 *
 * Covers the pre-connect phase too (Dialing/Ringing of an outgoing call), not just once Active —
 * otherwise minimizing Outgoing Dialing before the call connects would leave no way back to it.
 */
@Composable
fun CallBannerHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val activeCall by CallRepository.activeCallUiState.collectAsState()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    val state = activeCall
    if (state != null &&
        currentRoute != Routes.ACTIVE_CALL &&
        currentRoute != Routes.INCOMING_CALL &&
        currentRoute != Routes.OUTGOING_DIALING
    ) {
        OngoingCallBanner(
            state = state,
            onBannerClick = {
                if (state.state == CallUiState.ACTIVE) {
                    navController.navigate(Routes.ACTIVE_CALL)
                } else {
                    // Safe to re-enter: OutgoingDialingViewModel.placeCall() recognizes a call
                    // that's already tracked by CallRepository and won't re-dial.
                    state.phoneNumber?.let { navController.navigate(Routes.outgoingDialingRoute(it)) }
                }
            },
            onEndCallClick = { CallRepository.disconnect() },
            modifier = modifier,
        )
    }
}

@Composable
private fun OngoingCallBanner(
    state: ActiveCallUiState,
    onBannerClick: () -> Unit,
    onEndCallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var elapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(state.connectTimestampMillis) {
        val connectTimestamp = state.connectTimestampMillis ?: return@LaunchedEffect
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - connectTimestamp) / 1000)
                .toInt()
                .coerceAtLeast(0)
            delay(1000)
        }
    }

    val pulseAlpha by rememberInfiniteTransition(label = "call_banner_pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "call_banner_pulse_alpha",
    )

    val name = state.displayName
        ?: state.phoneNumber?.let { formatPhoneNumberForDisplay(it) }
        ?: stringResource(R.string.active_call_unknown_caller)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(listOf(BannerGradientStart, BannerGradientEnd)))
            .clickable(onClick = onBannerClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = pulseAlpha)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        val statusText = when (state.state) {
            CallUiState.RINGING -> stringResource(R.string.call_banner_ringing, name)
            CallUiState.ACTIVE -> stringResource(R.string.call_banner_ongoing, name, formatCallTimer(elapsedSeconds))
            else -> stringResource(R.string.call_banner_calling, name)
        }
        Text(
            text = stringResource(R.string.call_banner_tap_to_return, statusText),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onEndCallClick, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = stringResource(R.string.active_call_end_call_label),
                tint = Color.White,
            )
        }
    }
}
