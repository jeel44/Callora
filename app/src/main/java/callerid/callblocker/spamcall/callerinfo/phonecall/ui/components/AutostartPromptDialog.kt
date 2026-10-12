package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.analytics.AnalyticsHelper
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptKind
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SetupPromptRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.deviceNeedsAutostartPermission
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.openAutostartSettings

/**
 * Home's one-time "allow autostart" dialog for the OEM skins that need it (see
 * AutostartSettings.kt) — says what the user gets for it, then opens the OEM's own screen.
 * Shown once per install either way: whether it ended up allowed can't be read back.
 *
 * Waits until Home's own call-log sheet has had its turn, so a first visit never stacks two
 * prompts — in practice this appears on the second visit to Home.
 */
@Composable
fun AutostartPromptDialog() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        show = deviceNeedsAutostartPermission() &&
            !SetupPromptRepository.hasAutostartPromptBeenShown(context) &&
            PermissionPromptRepository().hasBeenShown(context, PermissionPromptKind.CALL_LOG)
    }
    if (!show) return

    val dismiss = {
        SetupPromptRepository.markAutostartPromptShown(context)
        show = false
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.autostart_prompt_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.autostart_prompt_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    R.string.autostart_prompt_benefit_caller_id,
                    R.string.autostart_prompt_benefit_spam,
                    R.string.autostart_prompt_benefit_call_details,
                ).forEach { benefit ->
                    Text(
                        text = "•  " + stringResource(benefit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    AnalyticsHelper.logEvent("autostart_prompt_allow")
                    dismiss()
                    context.openAutostartSettings()
                },
            ) { Text(stringResource(R.string.autostart_prompt_allow)) }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.permission_not_now)) }
        },
    )
}
