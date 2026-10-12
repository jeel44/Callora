package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.analytics.AnalyticsHelper
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SetupPromptRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.openAutostartSettings

/**
 * The "allow autostart" dialog for the OEM skins that need it (see AutostartSettings.kt) — says
 * what the user gets for it, then opens the OEM's own screen. Shown once per install, as the last
 * step of SetDefaultScreen's setup sequence: whether it ended up allowed can't be read back, so
 * it is recorded as shown the moment the user answers either way. [onDone] fires after that
 * answer (for "Allow", once the OEM screen has been opened on top).
 */
@Composable
fun AutostartPromptDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val dismiss = {
        SetupPromptRepository.markAutostartPromptShown(context)
        onDone()
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
                    context.openAutostartSettings()
                    dismiss()
                },
            ) { Text(stringResource(R.string.autostart_prompt_allow)) }
        },
        dismissButton = {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.permission_not_now)) }
        },
    )
}
