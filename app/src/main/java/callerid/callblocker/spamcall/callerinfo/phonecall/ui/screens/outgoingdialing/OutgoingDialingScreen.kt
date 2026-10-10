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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallerIdentityCache
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallUiState
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallAppLabelRow
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallScreenBackground
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallSquircleButton
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallerIdentityHeader
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.ContextTag
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.EndCallButton
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraTheme

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
    // Whether callInfo already describes THIS call. It doesn't on the first frame(s): this
    // composable renders before its own LaunchedEffect runs placeCall() (which seeds
    // CallRepository via prepareOutgoingCall), so callInfo is still the empty state — or a
    // previous call's leftover DISCONNECTED state, name and all. The old code fell back to the
    // raw nav-arg number there with isResolvingContactName=false, which is exactly the
    // number -> name flash this screen still had despite the flag. Until callInfo catches up,
    // identity comes from the warm cache (synchronous) or stays a placeholder.
    val isThisCall = CallerIdentityCache.isSameNumber(callInfo.phoneNumber, phoneNumber)
    val cachedIdentity = remember(phoneNumber) { CallerIdentityCache.peek(phoneNumber) }
    val displayedNumber = if (isThisCall) callInfo.phoneNumber ?: phoneNumber else phoneNumber
    val displayName = if (isThisCall) callInfo.displayName else cachedIdentity?.contact?.name
    val photoUri = if (isThisCall) callInfo.photoUri else cachedIdentity?.contact?.photoUri
    val isResolvingContactName = if (isThisCall) callInfo.isResolvingContactName else cachedIdentity == null

    CallScreenBackground(isSpam = false, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallAppLabelRow {
                IconButton(onClick = onMinimize) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.call_minimize_content_description),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            CallerIdentityHeader(
                isResolving = isResolvingContactName,
                displayName = displayName,
                phoneNumber = displayedNumber,
                photoUri = photoUri,
                isSpam = false,
                unknownLabel = stringResource(R.string.active_call_unknown_caller),
                statusText = statusLabel,
            )

            val tagText = when {
                isSpamMarked -> stringResource(R.string.call_tag_marked_spam)
                !isResolvingContactName && displayName != null -> stringResource(R.string.call_tag_saved_contact)
                else -> null
            }
            if (tagText != null) {
                Spacer(modifier = Modifier.height(14.dp))
                ContextTag(text = tagText, isSpam = isSpamMarked)
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                // Keypad/More are inert until the call connects (as in Google Phone): DTMF and
                // the More sheet's add-call/hold only make sense on an answered call, and both
                // live on Active Call, which this screen hands off to the moment it does.
                CallSquircleButton(
                    icon = Icons.Filled.Dialpad,
                    label = stringResource(R.string.active_call_keypad_label),
                    onClick = {},
                    enabled = false,
                )
                CallSquircleButton(
                    icon = Icons.Filled.MicOff,
                    label = stringResource(R.string.call_mute_label),
                    isActive = audioState.isMuted,
                    onClick = { viewModel.setMuted(!audioState.isMuted) },
                )
                CallSquircleButton(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = stringResource(R.string.call_speaker_label),
                    isActive = audioState.isSpeakerOn,
                    onClick = { viewModel.setSpeakerEnabled(!audioState.isSpeakerOn) },
                )
                CallSquircleButton(
                    icon = Icons.Filled.MoreVert,
                    label = stringResource(R.string.active_call_more_label),
                    onClick = {},
                    enabled = false,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            EndCallButton(
                onClick = {
                    // Best-effort real disconnect, but never leaves the user stuck here waiting
                    // on a state callback that may never arrive (e.g. this app isn't currently
                    // holding ROLE_DIALER, so InCallServiceImpl was never bound for this call).
                    viewModel.cancelCall()
                    onCancel()
                },
                modifier = Modifier.padding(bottom = 32.dp),
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
