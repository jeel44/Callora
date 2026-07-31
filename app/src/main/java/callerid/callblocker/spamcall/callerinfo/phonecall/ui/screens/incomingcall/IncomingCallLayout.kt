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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallActionButton
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallScreenBackground
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.CallerAvatar
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.ContextTag
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.SimAccountLabel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.StatusPill
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay

/** Shared layout for [IncomingKnownScreen]/[IncomingSpamScreen] — both are real, always-live
 * driven by [CallRepository.callState]; only the accent color, tag, and middle action button
 * differ between the two, per the approved redesign. */
@Composable
internal fun IncomingCallLayout(
    displayName: String?,
    phoneNumber: String?,
    photoUri: String?,
    isSpam: Boolean,
    tagText: String?,
    simAccountLabel: String?,
    middleButtonIcon: ImageVector,
    middleButtonLabel: String,
    onMiddleButtonClick: () -> Unit,
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
            Spacer(modifier = Modifier.height(56.dp))

            StatusPill(text = stringResource(R.string.incoming_call_label), isSpam = isSpam)

            Spacer(modifier = Modifier.height(28.dp))

            CallerAvatar(
                photoUri = photoUri,
                initialsSource = displayName ?: phoneNumber,
                isSpam = isSpam,
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
                phoneNumber?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatPhoneNumberForDisplay(it),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.5.sp),
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = phoneNumber?.let { formatPhoneNumberForDisplay(it) } ?: stringResource(R.string.incoming_unknown_number),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            if (simAccountLabel != null) {
                Spacer(modifier = Modifier.height(10.dp))
                SimAccountLabel(label = simAccountLabel)
            }

            if (tagText != null) {
                Spacer(modifier = Modifier.height(14.dp))
                ContextTag(text = tagText, isSpam = isSpam)
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.incoming_slide_hint),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .padding(bottom = 46.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CallActionButton(
                    icon = Icons.Filled.CallEnd,
                    label = stringResource(R.string.incoming_decline_label),
                    containerColor = MaterialTheme.extendedColors.spam,
                    contentColor = Color.White,
                    onClick = onDecline,
                )
                CallActionButton(
                    icon = middleButtonIcon,
                    label = middleButtonLabel,
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White,
                    onClick = onMiddleButtonClick,
                )
                CallActionButton(
                    icon = Icons.Filled.Call,
                    label = stringResource(R.string.incoming_answer_label),
                    containerColor = MaterialTheme.extendedColors.success,
                    contentColor = Color.White,
                    onClick = onAnswer,
                )
            }
        }
    }
}
