package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.activecall

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAddAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallUiState
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.AddCallOptionsSheet
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.ManualNumberEntryDialog
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallActionButton
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallScreenBackground
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallerAvatar
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.ContextTag
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.SimAccountLabel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.StatusPill
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.UtilityIconButton
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.formatCallTimer
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraTheme
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import kotlinx.coroutines.delay

private val DtmfKeys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#')

/**
 * Real active-call screen, backed by [ActiveCallViewModel]/CallRepository — no simulated timer
 * or sample contact. The timer is recomputed from the real connect timestamp every tick (rather
 * than an incrementing counter) so it can't drift from actual elapsed call time. Mute/Speaker are
 * real InCallService controls, shared with Outgoing Dialing via the same CallRepository state.
 * Keypad sends real DTMF tones (Call.playDtmfTone/stopDtmfTone); Add Call offers a real contact
 * picker and manual number entry but does not (yet) actually merge calls — see
 * [showMergeUnsupportedToast] below.
 */
@Composable
fun ActiveCallScreen(
    onEndCall: () -> Unit,
    onMinimize: () -> Unit,
    onOpenContact: (phoneNumber: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveCallViewModel = viewModel(),
) {
    val callInfo by viewModel.callState.collectAsState()
    val audioState by viewModel.audioState.collectAsState()
    val isSpamMarked by viewModel.isSpamMarked.collectAsState()
    var elapsedSeconds by remember { mutableStateOf(0) }
    var showKeypad by remember { mutableStateOf(false) }
    var dtmfTypedDigits by remember { mutableStateOf("") }
    var showAddCallOptions by remember { mutableStateOf(false) }
    var showManualNumberEntry by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Add Call offers both a real system contact picker (ACTION_PICK on Phone.CONTENT_URI) and
    // manual number entry (see ManualNumberEntryDialog below) — matching how real dialers'
    // "Add call" typically works. This app's CallRepository only ever tracks one Call at a time
    // (see its own doc comment) — actually merging a second call requires either TelecomManager
    // conference support or placing a second call and calling Call.conference() once both are
    // connected, which is a real architecture change (multi-call tracking) beyond this pass. So
    // both paths are intentionally honest about not merging yet, rather than a silent no-op.
    fun showMergeUnsupportedToast(number: String?) {
        val label = number?.let { formatPhoneNumberForDisplay(it) }
            ?: context.getString(R.string.active_call_merge_unsupported_fallback_label)
        Toast.makeText(
            context,
            context.getString(R.string.active_call_merge_unsupported_toast, label),
            Toast.LENGTH_LONG,
        ).show()
    }

    val addCallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pickedUri: Uri? = result.data?.data
        if (pickedUri != null) {
            viewModel.resolvePickedAddCallNumber(pickedUri) { number -> showMergeUnsupportedToast(number) }
        }
    }

    LaunchedEffect(callInfo.connectTimestampMillis) {
        val connectTimestamp = callInfo.connectTimestampMillis ?: return@LaunchedEffect
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - connectTimestamp) / 1000).toInt()
            delay(1000)
        }
    }

    // The far end hanging up (or the call otherwise ending) should move the UI forward on its
    // own — not require the user to tap End Call themselves. Captured into a local val rather
    // than re-read inside the effect: CallRepository resets DISCONNECTED -> NONE almost
    // immediately (onCallRemoved fires right after), so re-reading the live state here could
    // race past DISCONNECTED before this coroutine runs and silently miss the transition.
    val currentCallState = callInfo.state
    LaunchedEffect(currentCallState) {
        if (currentCallState == CallUiState.DISCONNECTED) onEndCall()
    }

    val displayName = callInfo.displayName
    val moreOptionsToastText = stringResource(R.string.active_call_more_options_toast)

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

            StatusPill(text = formatCallTimer(elapsedSeconds), isSpam = false)

            Spacer(modifier = Modifier.height(28.dp))

            CallerAvatar(
                photoUri = callInfo.photoUri,
                initialsSource = displayName ?: callInfo.phoneNumber,
                isSpam = false,
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (displayName != null) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                callInfo.phoneNumber?.let { phoneNumber ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatPhoneNumberForDisplay(phoneNumber),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.5.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = callInfo.phoneNumber?.let { formatPhoneNumberForDisplay(it) } ?: stringResource(R.string.active_call_unknown_caller),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            callInfo.simAccountLabel?.let { simAccountLabel ->
                Spacer(modifier = Modifier.height(10.dp))
                SimAccountLabel(label = simAccountLabel)
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

            Spacer(modifier = Modifier.weight(1f))

            if (showKeypad) {
                DtmfKeypad(
                    typedDigits = dtmfTypedDigits,
                    onKeyDown = { digit ->
                        dtmfTypedDigits += digit
                        viewModel.playDtmfTone(digit)
                    },
                    onKeyUp = viewModel::stopDtmfTone,
                    onClose = { showKeypad = false },
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        UtilityIconButton(
                            icon = Icons.Filled.MicOff,
                            label = stringResource(R.string.call_mute_label),
                            isActive = audioState.isMuted,
                            onClick = { viewModel.setMuted(!audioState.isMuted) },
                        )
                        UtilityIconButton(
                            icon = Icons.Filled.Dialpad,
                            label = stringResource(R.string.active_call_keypad_label),
                            isActive = showKeypad,
                            onClick = { showKeypad = true },
                        )
                        UtilityIconButton(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            label = stringResource(R.string.call_speaker_label),
                            isActive = audioState.isSpeakerOn,
                            onClick = { viewModel.setSpeakerEnabled(!audioState.isSpeakerOn) },
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        UtilityIconButton(
                            icon = Icons.Filled.PersonAddAlt,
                            label = stringResource(R.string.active_call_add_call_label),
                            isActive = false,
                            onClick = { showAddCallOptions = true },
                        )
                        UtilityIconButton(
                            icon = Icons.Filled.Person,
                            label = stringResource(R.string.active_call_contact_label),
                            isActive = false,
                            onClick = { callInfo.phoneNumber?.let(onOpenContact) },
                        )
                        UtilityIconButton(
                            icon = Icons.Filled.MoreVert,
                            label = stringResource(R.string.active_call_more_label),
                            isActive = false,
                            onClick = {
                                // Never scoped/defined beyond this placeholder — real dialers'
                                // "More" typically covers things like call-swap/manage-conference
                                // that don't apply to this app's single-call architecture. Needs
                                // a product decision on what (if anything) belongs here.
                                Toast.makeText(context, moreOptionsToastText, Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            CallActionButton(
                icon = Icons.Filled.CallEnd,
                label = stringResource(R.string.active_call_end_call_label),
                containerColor = MaterialTheme.extendedColors.spam,
                contentColor = Color.White,
                onClick = {
                    // Real disconnect, but also navigate unconditionally so the button always
                    // works even in the (should-be-rare) case no telecom callback ever arrives.
                    viewModel.endCall()
                    onEndCall()
                },
                size = 64.dp,
                modifier = Modifier.padding(bottom = 46.dp),
            )
        }
    }

    if (showAddCallOptions) {
        AddCallOptionsSheet(
            onDismissRequest = { showAddCallOptions = false },
            onDialNumber = {
                showAddCallOptions = false
                showManualNumberEntry = true
            },
            onChooseContact = {
                showAddCallOptions = false
                addCallLauncher.launch(
                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                )
            },
        )
    }

    if (showManualNumberEntry) {
        ManualNumberEntryDialog(
            onDismissRequest = { showManualNumberEntry = false },
            onConfirm = { number ->
                showManualNumberEntry = false
                showMergeUnsupportedToast(number)
            },
        )
    }
}

/** Real in-call DTMF keypad — each key sends [ActiveCallViewModel.playDtmfTone] on press and
 * [ActiveCallViewModel.stopDtmfTone] on release via [onKeyDown]/[onKeyUp], not a decorative grid. */
@Composable
private fun DtmfKeypad(
    typedDigits: String,
    onKeyDown: (Char) -> Unit,
    onKeyUp: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.active_call_hide_keypad_content_description),
                    tint = Color.White,
                )
            }
            Text(
                text = if (typedDigits.isEmpty()) " " else typedDigits,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                color = Color.White,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        DtmfKeys.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { digit ->
                    DtmfKey(digit = digit, onPress = { onKeyDown(digit) }, onRelease = onKeyUp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DtmfKey(digit: Char, onPress: () -> Unit, onRelease: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .pointerInput(digit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit.toString(),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
            color = Color.White,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ActiveCallScreenPreview() {
    CalloraTheme {
        ActiveCallScreen(onEndCall = {}, onMinimize = {}, onOpenContact = {})
    }
}
