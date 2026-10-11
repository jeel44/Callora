package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.incomingcall

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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallAppLabelRow
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallOvalActionButton
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallScreenBackground
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallSecondaryPillButton
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallerIdentityHeader
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.ContextTag
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.SimAccountLabel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors

/** Shared layout for [IncomingKnownScreen]/[IncomingSpamScreen] — both are real, always-live
 * driven by [CallRepository.callState]; only the backdrop tint, tag, and second pill button
 * (Message vs Block) differ between the two. [isResolvingIdentity] holds the caller identity
 * (avatar glyph, name/number, tag) on a same-height placeholder until both the contact lookup and
 * the spam-mark read have settled, so ringing never paints number -> name. Tap-only Decline/Answer
 * (no swipe), matching the stable Google Phone app. */
@Composable
internal fun IncomingCallLayout(
    isResolvingIdentity: Boolean,
    displayName: String?,
    phoneNumber: String?,
    photoUri: String?,
    isSpam: Boolean,
    tagText: String?,
    simAccountLabel: String?,
    simAccountColor: Int? = null,
    middleButtonIcon: ImageVector,
    middleButtonLabel: String,
    onMiddleButtonClick: () -> Unit,
    onUrgent: () -> Unit,
    onDecline: () -> Unit,
    onAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CallScreenBackground(isSpam = isSpam, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallAppLabelRow()

            Spacer(modifier = Modifier.height(28.dp))

            CallerIdentityHeader(
                isResolving = isResolvingIdentity,
                displayName = displayName,
                phoneNumber = phoneNumber,
                photoUri = photoUri,
                isSpam = isSpam,
                unknownLabel = stringResource(R.string.incoming_unknown_number),
            )

            if (simAccountLabel != null) {
                Spacer(modifier = Modifier.height(14.dp))
                SimAccountLabel(label = simAccountLabel, color = simAccountColor?.let { Color(it) })
            }

            if (tagText != null && !isResolvingIdentity) {
                Spacer(modifier = Modifier.height(12.dp))
                ContextTag(text = tagText, isSpam = isSpam)
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CallSecondaryPillButton(
                    icon = Icons.Filled.SwapVert,
                    label = stringResource(R.string.incoming_urgent_label),
                    // Declines with a canned "call again if it's urgent" text (wired in
                    // CalloraNavHost's INCOMING_CALL route).
                    onClick = onUrgent,
                )
                CallSecondaryPillButton(
                    icon = middleButtonIcon,
                    label = middleButtonLabel,
                    onClick = onMiddleButtonClick,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CallOvalActionButton(
                    icon = Icons.Filled.Call,
                    iconRotation = 135f,
                    label = stringResource(R.string.incoming_decline_label),
                    contentDescription = stringResource(R.string.incoming_decline_label),
                    containerColor = MaterialTheme.extendedColors.callMissed,
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                )
                CallOvalActionButton(
                    icon = Icons.Filled.Call,
                    label = stringResource(R.string.incoming_answer_label),
                    contentDescription = stringResource(R.string.incoming_answer_label),
                    containerColor = MaterialTheme.extendedColors.callOutgoing,
                    onClick = onAnswer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
