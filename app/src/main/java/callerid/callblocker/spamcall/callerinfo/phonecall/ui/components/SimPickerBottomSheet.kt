package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import android.telecom.PhoneAccountHandle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallAccount
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors

/**
 * Shown by CalloraNavHost.placeCallWithSimCheck right before a call is actually placed, but only
 * when TelecomManager.getCallCapablePhoneAccounts() reports 2+ accounts (see PhoneAccounts.kt)
 * and the user has no system default calling SIM set (getDefaultOutgoingAccount) —
 * a single-SIM device never sees this at all, so its outgoing calls keep the exact
 * placeCall(uri, null) behavior that existed before dual-SIM support. Each row's color is the
 * SIM's own system-reported color where available (see CallAccount.color).
 *
 * Dismissing (backdrop tap or swipe-down) cancels the call attempt outright rather than falling
 * back to some default account — same "explicit response required" spirit as
 * [PermissionPreviewBottomSheet], since silently guessing which SIM to bill the user through is
 * worse than making them tap again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimPickerBottomSheet(
    accounts: List<CallAccount>,
    onAccountSelected: (PhoneAccountHandle) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.sim_picker_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.sim_picker_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.onSurfaceSoft,
            )

            Spacer(modifier = Modifier.height(16.dp))

            accounts.forEachIndexed { index, account ->
                SimPickerRow(
                    account = account,
                    slotColor = Color(account.color),
                    onClick = { onAccountSelected(account.handle) },
                )
                if (index != accounts.lastIndex) Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SimPickerRow(
    account: CallAccount,
    slotColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(slotColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Filled.SimCard, contentDescription = null, tint = slotColor)
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = account.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
