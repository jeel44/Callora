package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.aftercall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.SpamCategoryPickerSheet
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.formatCallTimer
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraTheme
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CardShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay

/**
 * Real after-call screen — uses the normal app background (not the dark call gradient),
 * matching a real dialer returning you to the app once the call has ended. Reads the just-ended
 * call's real number/duration from [CallRepository]'s last-call summary, captured the moment
 * InCallServiceImpl reported the call removed.
 */
@Composable
fun AfterCallScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AfterCallViewModel = viewModel(),
) {
    // A one-shot snapshot: this screen is shown once right after the call ends, so it doesn't
    // need to keep observing CallRepository for further changes.
    val summary = remember { CallRepository.lastCallSummary.value }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.call_status_ended),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.extendedColors.onSurfaceFaint,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            val formattedNumber = summary?.phoneNumber?.let { formatPhoneNumberForDisplay(it) }
            val durationText = summary?.let { formatCallTimer(it.durationSeconds.toInt()) }

            Text(
                text = summary?.displayName ?: formattedNumber ?: stringResource(R.string.incoming_unknown_number),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (summary?.displayName != null) {
                    listOfNotNull(formattedNumber, durationText).joinToString(" · ")
                } else {
                    durationText ?: ""
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.extendedColors.onSurfaceFaint,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.spam_report_question),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(text = stringResource(R.string.spam_report_not_spam), style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = viewModel::onReportSpamClicked,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.extendedColors.spam,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(text = stringResource(R.string.spam_report_report_spam), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (uiState.showReportSheet) {
        SpamCategoryPickerSheet(
            onDismissRequest = viewModel::onReportSheetDismissed,
            onCategorySelected = { category -> viewModel.onReportCategorySelected(category, onMarked = onDismiss) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AfterCallScreenPreview() {
    CalloraTheme {
        AfterCallScreen(onDismiss = {})
    }
}
