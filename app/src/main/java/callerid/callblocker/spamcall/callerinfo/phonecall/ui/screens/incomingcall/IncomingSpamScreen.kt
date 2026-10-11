package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.incomingcall

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraTheme

/**
 * Incoming call from a number this device has locally marked as spam (via SpamMarkRepository) —
 * visually distinct red radial background plus the "Marked spam by you before" tag, and a Block
 * action in place of Message. Real data throughout, same as [IncomingKnownScreen].
 */
@Composable
fun IncomingSpamScreen(
    isResolvingIdentity: Boolean,
    displayName: String?,
    phoneNumber: String?,
    photoUri: String?,
    onDecline: () -> Unit,
    onAnswer: () -> Unit,
    onBlock: () -> Unit,
    onUrgent: () -> Unit,
    modifier: Modifier = Modifier,
    simAccountLabel: String? = null,
    simAccountColor: Int? = null,
) {
    IncomingCallLayout(
        isResolvingIdentity = isResolvingIdentity,
        displayName = displayName,
        phoneNumber = phoneNumber,
        photoUri = photoUri,
        isSpam = true,
        tagText = stringResource(R.string.call_tag_marked_spam),
        simAccountLabel = simAccountLabel,
        simAccountColor = simAccountColor,
        middleButtonIcon = Icons.Filled.Block,
        middleButtonLabel = stringResource(R.string.incoming_block_label),
        onMiddleButtonClick = onBlock,
        onUrgent = onUrgent,
        onDecline = onDecline,
        onAnswer = onAnswer,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun IncomingSpamScreenPreview() {
    CalloraTheme {
        IncomingSpamScreen(
            isResolvingIdentity = false,
            displayName = null,
            phoneNumber = "+916281904471",
            photoUri = null,
            onDecline = {},
            onAnswer = {},
            onBlock = {},
            onUrgent = {},
        )
    }
}
