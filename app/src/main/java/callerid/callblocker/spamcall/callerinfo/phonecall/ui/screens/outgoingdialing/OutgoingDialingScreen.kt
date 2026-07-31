package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.outgoingdialing

import android.telecom.PhoneAccountHandle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallUiState
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallActionButton
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallScreenBackground
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallerAvatar
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.ContextTag
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.PulsingDotsRow
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.StatusPill
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.UtilityIconButton
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraTheme
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay

/**
 * Real outgoing-dialing screen — places the call via [OutgoingDialingViewModel]
 * (TelecomManager.placeCall) and reflects the real Call state reported back through
 * InCallServiceImpl/CallRepository. No simulated timers: it sits on whatever state the
 * telephony stack reports until the call is answered (→ Active Call) or ends before connecting
 * (→ back to Home). Mute/Speaker are real InCallService controls (via CallRepository), not local
 * cosmetic toggles, so they keep working once the call transitions to Active.
 */
@Composable
fun OutgoingDialingScreen(
    phoneNumber: String,
    onCancel: () -> Unit,
    onCallAnswered: () -> Unit,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
    // The SIM the user picked via CalloraNavHost's SimPickerBottomSheet before navigating here —
    // null on a single-SIM device, or whenever the picker never needed to appear (see
    // CalloraNavHost.placeCallWithSimCheck).
    phoneAccountHandle: PhoneAccountHandle? = null,
    viewModel: OutgoingDialingViewModel = viewModel(),
) {
    val callInfo by viewModel.callState.collectAsState()
    val audioState by viewModel.audioState.collectAsState()
    val isSpamMarked by viewModel.isSpamMarked.collectAsState()

    LaunchedEffect(phoneNumber, phoneAccountHandle) {
        val placed = viewModel.placeCall(phoneNumber, phoneAccountHandle)
        if (!placed) onCancel()
    }

    // Reads callInfo.state into a local val rather than re-reading it inside the effect body:
    // CallRepository resets state DISCONNECTED -> NONE almost immediately (onCallRemoved runs
    // right after the disconnect callback), so re-reading the live state here would race and
    // could see NONE by the time this coroutine actually runs, silently swallowing the
    // DISCONNECTED transition and leaving the screen stuck.
    val currentCallState = callInfo.state
    LaunchedEffect(currentCallState) {
        when (currentCallState) {
            CallUiState.ACTIVE -> onCallAnswered()
            CallUiState.DISCONNECTED -> onCancel()
            else -> Unit
        }
    }

    val statusLabel = when (callInfo.state) {
        CallUiState.RINGING -> stringResource(R.string.outgoing_status_ringing)
        else -> stringResource(R.string.outgoing_status_calling)
    }
    val displayedNumber = callInfo.phoneNumber ?: phoneNumber
    val displayName = callInfo.displayName
    // True only for the brief window between a new number showing up and CallRepository's
    // ContactsRepository lookup settling — see ActiveCallInfo.isResolvingContactName's own doc.
    // Gates both the avatar initials and the name/number text below so this screen never renders
    // the raw number only to immediately replace it with the resolved contact name a moment
    // later (a visible flash) — it shows a neutral placeholder for that one short window instead.
    val isResolvingContactName = callInfo.isResolvingContactName

    CallScreenBackground(isSpam = false, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onMinimize) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.call_minimize_content_description),
                        tint = Color.White,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            StatusPill(text = stringResource(R.string.outgoing_call_label), isSpam = false)

            Spacer(modifier = Modifier.height(28.dp))

            CallerAvatar(
                photoUri = callInfo.photoUri,
                initialsSource = if (isResolvingContactName) null else (displayName ?: displayedNumber),
                isSpam = false,
            )

            Spacer(modifier = Modifier.height(20.dp))

            when {
                isResolvingContactName -> {
                    // Neutral placeholder — same style/height as the number-only branch below so
                    // nothing visibly reflows once resolution completes without a saved contact.
                    Text(
                        text = "",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                displayName != null -> {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatPhoneNumberForDisplay(displayedNumber),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.5.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    Text(
                        text = formatPhoneNumberForDisplay(displayedNumber),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            val tagText = when {
                isSpamMarked -> stringResource(R.string.call_tag_marked_spam)
                displayName != null -> stringResource(R.string.call_tag_saved_contact)
                else -> null
            }
            if (tagText != null) {
                Spacer(modifier = Modifier.height(14.dp))
                ContextTag(text = tagText, isSpam = isSpamMarked)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                PulsingDotsRow(color = Color.White.copy(alpha = 0.7f))
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                UtilityIconButton(
                    icon = Icons.Filled.MicOff,
                    label = stringResource(R.string.call_mute_label),
                    isActive = audioState.isMuted,
                    onClick = { viewModel.setMuted(!audioState.isMuted) },
                )
                UtilityIconButton(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = stringResource(R.string.call_speaker_label),
                    isActive = audioState.isSpeakerOn,
                    onClick = { viewModel.setSpeakerEnabled(!audioState.isSpeakerOn) },
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            CallActionButton(
                icon = Icons.Filled.CallEnd,
                label = stringResource(R.string.outgoing_cancel_label),
                containerColor = MaterialTheme.extendedColors.spam,
                contentColor = Color.White,
                onClick = {
                    // Best-effort real disconnect, but never leaves the user stuck here waiting
                    // on a state callback that may never arrive (e.g. this app isn't currently
                    // holding ROLE_DIALER, so InCallServiceImpl was never bound for this call).
                    viewModel.cancelCall()
                    onCancel()
                },
                size = 64.dp,
                modifier = Modifier.padding(bottom = 46.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OutgoingDialingScreenPreview() {
    CalloraTheme {
        OutgoingDialingScreen(
            phoneNumber = "+91 90000 11223",
            onCancel = {},
            onCallAnswered = {},
            onMinimize = {},
        )
    }
}
