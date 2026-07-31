package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallLogRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamCategory
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamMark
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.formatCallTimer
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraAccent
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraPrimary
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraSpam
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.LightOnSurfaceFaint
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.LightSurfaceVariant
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatRelativeDayLabel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardShape = RoundedCornerShape(18.dp)
private val HeaderTopShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
private val SpamGradientStart = CalloraSpam
private val SpamGradientEnd = Color(0xFFC62828)
private val NormalGradientStart = CalloraPrimary
private val NormalGradientEnd = CalloraAccent

// "View profile" button — a solid shade darker than each variant's own header gradient. No
// existing token in Color.kt goes this dark (CalloraSpam/SetDefaultSpamAvatar* are all lighter
// reds meant for avatars/mock previews, not a pressed-looking solid button), so these are new,
// scoped to this screen only — same convention as CallScreenComponents' own local gradient stops.
private val SpamButtonColor = Color(0xFFB71C1C)
private val NormalButtonColor = Color(0xFF0D47A1)

private data class CallEndedRealData(
    val contact: Contact?,
    val spamMark: SpamMark?,
    val isBlocked: Boolean,
)

/**
 * Loads the ended call's real contact/spam/block state and renders [CallEndedCard] as a
 * dimmed-backdrop popup. [onOpenContactDetail] and [onDismiss] are the only two ways this screen
 * ever navigates anywhere — both close this popup Activity (see CallEndedActivity).
 */
@Composable
fun CallEndedRoute(
    phoneNumber: String,
    isIncoming: Boolean,
    durationSeconds: Long,
    endedAtMillis: Long,
    onDismiss: () -> Unit,
    onOpenContactDetail: (phoneNumber: String) -> Unit,
    onCall: (phoneNumber: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var data by remember { mutableStateOf<CallEndedRealData?>(null) }
    var isBlocked by remember { mutableStateOf(false) }
    var spamReported by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(phoneNumber) {
        visible = true
        val loaded = withContext(Dispatchers.IO) {
            val contactsRepository = ContactsRepository()
            val spamMarkRepository = SpamMarkRepository()
            val blockedNumbersRepository = BlockedNumbersRepository()
            CallEndedRealData(
                contact = contactsRepository.getContactByPhoneNumber(context, phoneNumber, includeDetails = true),
                spamMark = spamMarkRepository.getSpamMark(context, phoneNumber),
                isBlocked = blockedNumbersRepository.isNumberBlocked(context, phoneNumber),
            )
        }
        data = loaded
        isBlocked = loaded.isBlocked
    }

    fun requestDismiss() {
        visible = false
    }
    LaunchedEffect(visible) {
        if (!visible) {
            // Matches the AnimatedVisibility exit spec below — gives the fade/scale-out time to
            // actually play before the Activity behind it is torn down.
            delay(200)
            onDismiss()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.92f),
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.92f),
        ) {
            CallEndedCard(
                phoneNumber = phoneNumber,
                isIncoming = isIncoming,
                durationSeconds = durationSeconds,
                endedAtMillis = endedAtMillis,
                contact = data?.contact,
                spamMark = data?.spamMark,
                isBlocked = isBlocked,
                spamReported = spamReported,
                onDismiss = ::requestDismiss,
                onCall = { onCall(phoneNumber) },
                onMessage = {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")))
                },
                onSave = {
                    context.startActivity(
                        Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
                            putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                        },
                    )
                },
                onOpenContact = { onOpenContactDetail(phoneNumber) },
                onToggleBlock = {
                    coroutineScope.launch {
                        val blockedNumbersRepository = BlockedNumbersRepository()
                        val succeeded = withContext(Dispatchers.IO) {
                            if (isBlocked) {
                                blockedNumbersRepository.unblockNumber(context, phoneNumber)
                            } else {
                                blockedNumbersRepository.blockNumber(context, phoneNumber)
                            }
                        }
                        if (succeeded) {
                            isBlocked = !isBlocked
                            Toast.makeText(
                                context,
                                if (isBlocked) {
                                    context.getString(R.string.call_ended_toast_number_blocked)
                                } else {
                                    context.getString(R.string.call_ended_toast_number_unblocked)
                                },
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.call_ended_toast_block_update_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
                onReportSpam = {
                    coroutineScope.launch {
                        // Same repository every other spam-marking entry point in the app uses;
                        // no category picker here (unlike the old AfterCallScreen's
                        // SpamCategoryPickerSheet) — this is a quick one-tap report.
                        withContext(Dispatchers.IO) {
                            SpamMarkRepository().markAsSpam(context, phoneNumber, SpamCategory.OTHER)
                        }
                        spamReported = true
                    }
                },
            )
        }
    }
}

@Composable
private fun CallEndedCard(
    phoneNumber: String,
    isIncoming: Boolean,
    durationSeconds: Long,
    endedAtMillis: Long,
    contact: Contact?,
    spamMark: SpamMark?,
    isBlocked: Boolean,
    spamReported: Boolean,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onSave: () -> Unit,
    onOpenContact: () -> Unit,
    onToggleBlock: () -> Unit,
    onReportSpam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSpam = spamMark != null
    var expanded by remember { mutableStateOf(false) }
    var callCount by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    LaunchedEffect(expanded, phoneNumber) {
        if (expanded && !isSpam && callCount == null) {
            callCount = withContext(Dispatchers.IO) {
                CallLogRepository().getCallHistoryForNumber(context, phoneNumber, limit = 50).size
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .shadow(elevation = 16.dp, shape = CardShape)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HeaderBand(
            isSpam = isSpam,
            isIncoming = isIncoming,
            durationSeconds = durationSeconds,
            endedAtMillis = endedAtMillis,
            contact = contact,
            spamMark = spamMark,
            phoneNumber = phoneNumber,
            onDismiss = onDismiss,
        )

        ViewProfileButton(
            isSpam = isSpam,
            onToggle = { expanded = !expanded },
        )

        if (expanded) {
            ProfileDetailsPanel(
                isSpam = isSpam,
                contact = contact,
                spamMark = spamMark,
                callCount = callCount,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = formatPhoneNumberForDisplay(phoneNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionButton(
                icon = Icons.Filled.Call,
                label = stringResource(R.string.call_ended_action_call),
                tint = MaterialTheme.colorScheme.primary,
                onClick = onCall,
            )
            ActionButton(
                icon = Icons.AutoMirrored.Filled.Message,
                label = stringResource(R.string.call_ended_action_message),
                tint = MaterialTheme.colorScheme.primary,
                onClick = onMessage,
            )
            if (isSpam) {
                ActionButton(
                    icon = Icons.Filled.Bookmark,
                    label = stringResource(R.string.call_ended_action_save),
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onSave,
                )
            } else if (contact != null) {
                ActionButton(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.call_ended_action_contact),
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onOpenContact,
                )
            } else {
                ActionButton(
                    icon = Icons.Filled.Bookmark,
                    label = stringResource(R.string.call_ended_action_save),
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onSave,
                )
            }
            ActionButton(
                icon = Icons.Filled.Block,
                label = if (isBlocked) stringResource(R.string.call_ended_action_unblock) else stringResource(R.string.call_ended_action_block),
                tint = if (isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.spam,
                onClick = onToggleBlock,
            )
        }

        if (!isSpam) {
            SpamReportSection(reported = spamReported, onReportSpam = onReportSpam, onDismiss = onDismiss)
        }
    }
}

/** Ported from the old AfterCallScreen's "Was this call spam?" prompt — the one feature that
 * screen had that this card didn't, now that AfterCallScreen no longer fires on call end. Only
 * shown for the normal/blue variant; the red spam variant already implies spam status, so asking
 * again would be redundant. Reports via the same [SpamMarkRepository] every other spam-marking
 * entry point in the app uses (defaulting to [SpamCategory.OTHER] — there's no category picker
 * here, unlike AfterCallScreen's SpamCategoryPickerSheet). [reported] is hoisted up to
 * [CallEndedRoute] (same shape as [CallEndedCard]'s own isBlocked/onToggleBlock) since it reflects
 * a real repository write. "No, it's fine" has nothing to persist, so it just closes the whole
 * card via the same [onDismiss] the header's X button uses, rather than only hiding this section. */
@Composable
private fun SpamReportSection(reported: Boolean, onReportSpam: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (reported) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.spam,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.call_ended_reported_as_spam),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.spam_report_question),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                ) {
                    Text(text = stringResource(R.string.spam_report_not_spam), style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onReportSpam,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.extendedColors.spam,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(text = stringResource(R.string.spam_report_report_spam), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun HeaderBand(
    isSpam: Boolean,
    isIncoming: Boolean,
    durationSeconds: Long,
    endedAtMillis: Long,
    contact: Contact?,
    spamMark: SpamMark?,
    phoneNumber: String,
    onDismiss: () -> Unit,
) {
    val gradient = if (isSpam) {
        Brush.linearGradient(listOf(SpamGradientStart, SpamGradientEnd))
    } else {
        Brush.linearGradient(listOf(NormalGradientStart, NormalGradientEnd))
    }

    val missed = durationSeconds <= 0L && isIncoming
    val statusWord = if (missed) stringResource(R.string.call_status_missed) else stringResource(R.string.call_status_ended)
    val statusLine = stringResource(R.string.call_ended_status_line, statusWord, formatRelativeShort(endedAtMillis))
    val titleLine = when {
        isSpam -> spamMark!!.category.displayLabel()
        contact != null -> contact.name
        else -> formatPhoneNumberForDisplay(phoneNumber)
    }
    val subtextLine = if (isSpam) {
        stringResource(R.string.call_ended_marked_as_spam_subtext, formatRelativeShort(spamMark!!.markedAt))
    } else {
        formatCallTimer(durationSeconds.toInt())
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeaderTopShape)
            .background(gradient)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderAvatar(isSpam = isSpam, contact = contact)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(
                    text = titleLine,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = subtextLine,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }

        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.call_ended_dismiss_content_description), tint = Color.White)
        }
    }
}

/** Spam: shield icon on a translucent-white circle (unchanged). Normal/identified: a real contact
 * photo when [ContactsRepository.getContactByPhoneNumber] resolved one, otherwise a plain
 * person-silhouette placeholder on a light neutral background — not initials, matching the
 * reference design (this app has no other real per-contact glyph to fall back to besides the
 * photo itself). */
@Composable
private fun HeaderAvatar(isSpam: Boolean, contact: Contact?) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (isSpam) Color.White.copy(alpha = 0.22f) else LightSurfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isSpam -> Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
            contact?.photoUri != null -> AsyncImage(
                model = contact.photoUri,
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(CircleShape),
            )
            else -> Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = LightOnSurfaceFaint,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** Solid rounded-rect button (not a translucent row) sitting below the header band, inset with
 * the same horizontal margin as the card's other inner content — its color is a darker shade of
 * that variant's own header gradient, so it reads as "belonging" to the header above it. Tapping
 * it still just toggles the detail panel below; there's no separate chevron/expand indicator. */
@Composable
private fun ViewProfileButton(isSpam: Boolean, onToggle: () -> Unit) {
    val backgroundColor = if (isSpam) SpamButtonColor else NormalButtonColor
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.call_ended_view_profile),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** Only real, already-loaded fields — nothing here is fabricated. A field is simply omitted
 * (not shown as "0"/blank) when there's no real data behind it. */
@Composable
private fun ProfileDetailsPanel(
    isSpam: Boolean,
    contact: Contact?,
    spamMark: SpamMark?,
    callCount: Int?,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        if (isSpam && spamMark != null) {
            DetailRow(
                icon = Icons.Filled.Shield,
                text = stringResource(R.string.call_ended_detail_category, spamMark.category.displayLabel()),
            )
            DetailRow(
                icon = Icons.Filled.Schedule,
                text = stringResource(R.string.call_ended_detail_marked, formatRelativeShort(spamMark.markedAt)),
            )
        } else {
            contact?.organization?.let { DetailRow(icon = Icons.Filled.Work, text = it) }
            contact?.email?.let { DetailRow(icon = Icons.Filled.Mail, text = it) }
            if (callCount != null && callCount > 0) {
                DetailRow(
                    icon = Icons.Filled.Schedule,
                    text = context.resources.getQuantityString(
                        R.plurals.call_ended_detail_call_count,
                        callCount,
                        callCount,
                    ),
                )
            }
            if (contact?.organization == null && contact?.email == null && (callCount == null || callCount == 0)) {
                Text(
                    text = stringResource(R.string.call_ended_no_additional_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.onSurfaceFaint,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.onSurfaceSoft,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SpamCategory.displayLabel(): String = when (this) {
    SpamCategory.TELEMARKETING -> stringResource(R.string.spam_category_telemarketing)
    SpamCategory.SCAM -> stringResource(R.string.spam_category_scam)
    SpamCategory.ROBOCALL -> stringResource(R.string.spam_category_robocall)
    SpamCategory.FRAUD -> stringResource(R.string.spam_category_fraud)
    SpamCategory.OTHER -> stringResource(R.string.spam_category_other)
}

/** "just now" / "12m ago" / "3h ago", falling back to [formatRelativeDayLabel] past 24h — this
 * popup is shown immediately at call-end, so the common case is always "just now"; the longer
 * forms only matter for a [SpamMark.markedAt] from a much earlier session. */
@Composable
private fun formatRelativeShort(timestampMillis: Long): String {
    val diffMillis = (System.currentTimeMillis() - timestampMillis).coerceAtLeast(0)
    val minutes = diffMillis / 60_000
    return when {
        minutes < 1 -> stringResource(R.string.call_ended_relative_just_now)
        minutes < 60 -> stringResource(R.string.call_ended_relative_minutes_ago, minutes)
        minutes < 24 * 60 -> stringResource(R.string.call_ended_relative_hours_ago, minutes / 60)
        else -> formatRelativeDayLabel(timestampMillis)
    }
}
