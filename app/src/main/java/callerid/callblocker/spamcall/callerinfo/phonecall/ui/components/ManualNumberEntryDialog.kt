package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import callerid.callblocker.spamcall.callerinfo.phonecall.R

private val AllowedDialChars = "0123456789+*#".toSet()

/**
 * Manual number entry for Add Call's "Dial a number" option — a plain numeric field rather than
 * the full [DialpadScreen], since there's no T9 match list or call-placement needed here, only a
 * number to hand back to the caller (who shows the same "merging not supported yet" toast used by
 * the contact-picker path).
 */
@Composable
fun ManualNumberEntryDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (phoneNumber: String) -> Unit,
) {
    var typedNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.add_call_dial_number)) },
        text = {
            OutlinedTextField(
                value = typedNumber,
                onValueChange = { newValue ->
                    typedNumber = newValue.filter { it in AllowedDialChars }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                placeholder = { Text(stringResource(R.string.manual_number_entry_placeholder)) },
                modifier = Modifier,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(typedNumber) },
                enabled = typedNumber.isNotEmpty(),
            ) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
