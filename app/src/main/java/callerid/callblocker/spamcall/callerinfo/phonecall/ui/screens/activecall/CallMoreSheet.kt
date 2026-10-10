package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.activecall

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
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors

/** Active Call's "More" sheet — Add call / Video call / Hold. Only UI: [ActiveCallScreen] decides
 * what each row does (Add call reuses the existing [AddCallOptionsSheet] flow). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallMoreSheet(
    onDismissRequest: () -> Unit,
    onAddCall: () -> Unit,
    onVideoCall: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.call_more_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.call_more_sheet_close_content_description),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            MoreSheetRow(
                icon = Icons.Filled.AddIcCall,
                label = stringResource(R.string.active_call_add_call_label),
                onClick = onAddCall,
            )
            MoreSheetDivider()
            MoreSheetRow(
                icon = Icons.Filled.Videocam,
                label = stringResource(R.string.active_call_video_call_label),
                onClick = onVideoCall,
            )
            MoreSheetDivider()
            MoreSheetRow(
                icon = Icons.Filled.Pause,
                label = stringResource(R.string.active_call_hold_label),
                onClick = onHold,
            )
        }
    }
}

@Composable
private fun MoreSheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MoreSheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 84.dp, end = 24.dp)
            .height(1.dp)
            .background(MaterialTheme.extendedColors.listDivider),
    )
}
