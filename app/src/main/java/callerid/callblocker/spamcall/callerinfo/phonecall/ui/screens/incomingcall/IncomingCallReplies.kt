package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.incomingcall

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallRepository

/**
 * Incoming Call's "Message" picker — Telecom's own quick-reply texts ([replies], sent by
 * declining via Call.reject(true, text)) plus "Write your own", mirroring the stock dialer.
 * [replies] is empty when the call can't be answered by text at all, leaving only the composer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickReplySheet(
    replies: List<String>,
    onReply: (String) -> Unit,
    onWriteOwn: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.incoming_quick_reply_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            replies.forEach { reply ->
                QuickReplyRow(icon = Icons.AutoMirrored.Filled.Message, label = reply, onClick = { onReply(reply) })
            }
            QuickReplyRow(
                icon = Icons.Filled.Edit,
                label = stringResource(R.string.incoming_quick_reply_write_own),
                onClick = onWriteOwn,
            )
        }
    }
}

@Composable
private fun QuickReplyRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Declines the ringing call and opens the SMS composer for [phoneNumber] (pre-filled with [body]).
 * The Incoming Call screen is usually showing over the lock screen (setShowWhenLocked), and an
 * Activity started from there opens BEHIND the keyguard — invisible, which is why the old
 * Message button looked dead. So on a locked device the user is asked to unlock first, and the
 * call is only declined once that succeeds: cancelling the unlock leaves it ringing, untouched.
 */
internal fun declineAndOpenSmsComposer(activity: Activity, phoneNumber: String, body: String? = null) {
    val openComposer = {
        CallRepository.rejectForSmsCompose()
        activity.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber"))
                .apply { if (body != null) putExtra("sms_body", body) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
    val keyguardManager = activity.getSystemService(KeyguardManager::class.java)
    if (keyguardManager?.isKeyguardLocked == true) {
        keyguardManager.requestDismissKeyguard(
            activity,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = openComposer()
            },
        )
    } else {
        openComposer()
    }
}
