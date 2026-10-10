package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.CallType
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.RecentCallEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatRelativeDayLabel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatTimeOfDay
import coil.compose.AsyncImage

/**
 * One flat call-log row (Home, Spam Center): avatar, name (or number) with a "(n)" count for a
 * run of [count] consecutive calls, the latest call's direction arrow, and its time on the right.
 * No card or elevation — rows are separated by [CallLogDivider] and grouped under
 * [CallLogDayHeader]s, so the time shown is clock time only.
 */
@Composable
fun CallLogRow(
    call: RecentCallEntry,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CallLogAvatar(call = call)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = call.name ?: call.formattedPhoneNumber,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (count > 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "($count)",
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal),
                        color = MaterialTheme.extendedColors.mutedText,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CallDirectionIcon(callType = call.callType)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    // Saved contacts: the number (the name is already the title). Unknown numbers:
                    // the title already IS the number, so say what kind of call it was instead.
                    text = if (call.name != null) call.formattedPhoneNumber else callTypeLabel(call.callType),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extendedColors.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatTimeOfDay(call.timestamp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extendedColors.mutedText,
        )
    }
}

/** "Today" / "Yesterday" / "Jul 18" above each day's rows. */
@Composable
fun CallLogDayHeader(timestamp: Long, modifier: Modifier = Modifier) {
    Text(
        text = formatRelativeDayLabel(timestamp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.extendedColors.mutedText,
        modifier = modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

/** 1dp rule between two rows of the same day, inset to start under the text (not the avatar). */
@Composable
fun CallLogDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AvatarSize + 14.dp)
            .height(1.dp)
            .background(MaterialTheme.extendedColors.listDivider),
    )
}

private val AvatarSize = 44.dp

/** Contact photo when there is one; otherwise an initial-letter circle for a saved contact, or a
 * neutral person outline for an unknown number. */
@Composable
private fun CallLogAvatar(call: RecentCallEntry) {
    val name = call.name
    when {
        call.photoUri != null -> AsyncImage(
            model = call.photoUri,
            contentDescription = null,
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape),
        )
        name != null -> Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (name.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#').toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        else -> Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.mutedText,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun CallDirectionIcon(callType: CallType) {
    val (icon, tint) = when (callType) {
        CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to MaterialTheme.extendedColors.callOutgoing
        CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to MaterialTheme.extendedColors.callMissed
        CallType.INCOMING, CallType.OTHER -> Icons.AutoMirrored.Filled.CallReceived to MaterialTheme.extendedColors.callIncoming
    }
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
}

@Composable
private fun callTypeLabel(callType: CallType): String = when (callType) {
    CallType.OUTGOING -> stringResource(R.string.contact_detail_call_type_outgoing)
    CallType.MISSED -> stringResource(R.string.call_status_missed)
    CallType.INCOMING, CallType.OTHER -> stringResource(R.string.contact_detail_call_type_incoming)
}
