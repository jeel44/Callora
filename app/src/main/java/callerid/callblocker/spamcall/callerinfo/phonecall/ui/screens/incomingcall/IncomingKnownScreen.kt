package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.incomingcall

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraTheme

/**
 * Incoming call from a number this device has NOT locally marked as spam — covers both saved
 * contacts (shows the "Saved contact" tag) and genuinely unknown numbers (no tag at all). Real
 * data throughout: driven by [IncomingCallViewModel]/CallRepository, no simulated content.
 */
@Composable
fun IncomingKnownScreen(
    isResolvingIdentity: Boolean,
    displayName: String?,
    phoneNumber: String?,
    photoUri: String?,
    isSavedContact: Boolean,
    onDecline: () -> Unit,
    onAnswer: () -> Unit,
    onMessage: () -> Unit,
    onUrgent: () -> Unit,
    modifier: Modifier = Modifier,
    simAccountLabel: String? = null,
) {
    IncomingCallLayout(
        isResolvingIdentity = isResolvingIdentity,
        displayName = displayName,
        phoneNumber = phoneNumber,
        photoUri = photoUri,
        isSpam = false,
        tagText = if (isSavedContact) stringResource(R.string.call_tag_saved_contact) else null,
        simAccountLabel = simAccountLabel,
        middleButtonIcon = Icons.AutoMirrored.Filled.Message,
        middleButtonLabel = stringResource(R.string.incoming_message_label),
        onMiddleButtonClick = onMessage,
        onUrgent = onUrgent,
        onDecline = onDecline,
        onAnswer = onAnswer,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun IncomingKnownScreenSavedContactPreview() {
    CalloraTheme {
        IncomingKnownScreen(
            isResolvingIdentity = false,
            displayName = "Riya Sharma",
            phoneNumber = "+919000011223",
            photoUri = null,
            isSavedContact = true,
            onDecline = {},
            onAnswer = {},
            onMessage = {},
            onUrgent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IncomingKnownScreenUnknownPreview() {
    CalloraTheme {
        IncomingKnownScreen(
            isResolvingIdentity = false,
            displayName = null,
            phoneNumber = "+916281904471",
            photoUri = null,
            isSavedContact = false,
            onDecline = {},
            onAnswer = {},
            onMessage = {},
            onUrgent = {},
        )
    }
}
