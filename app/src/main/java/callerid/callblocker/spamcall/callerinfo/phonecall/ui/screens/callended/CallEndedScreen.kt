package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.callended

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.analytics.AnalyticsHelper
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.BlockedNumbersRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallLogRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.CallNotesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.SpamMarkRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.CallHistoryEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.CallType
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamCategory
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.SpamMark
import callerid.callblocker.spamcall.callerinfo.phonecall.remoteconfig.CallEndAdFormat
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CallEndBannerAdSlot
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.NativeAdSlot
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.SpamCategoryPickerSheet
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call.formatCallTimer
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraAccent
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraPrimary
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CalloraSpam
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.LightOnSurfaceFaint
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.LightSurfaceVariant
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatRelativeDayLabel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.openWhatsAppChat
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.startExternalActivity
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SpamGradientStart = CalloraSpam
private val SpamGradientEnd = Color(0xFFC62828)
private val NormalGradientStart = CalloraPrimary
private val NormalGradientEnd = CalloraAccent

// Layout tokens — one margin, one section radius, one action size, used everywhere on this
// screen so its parts line up on the same edges.
private val ScreenMargin = 20.dp
private val SectionShape = RoundedCornerShape(18.dp)
private val HeroShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
private val ActionButtonSize = 52.dp
private val ActionButtonShape = RoundedCornerShape(18.dp)

// The most the native-format ad may take of the screen; the card fits itself into what's left.
private val NativeAdMaxHeight = 330.dp

// How many of this number's latest calls the card lists under "Recent calls".
private const val RECENT_CALLS_SHOWN = 3

private data class CallEndedRealData(
    val contact: Contact?,
    val spamMark: SpamMark?,
    val isBlocked: Boolean,
    val recentCalls: List<CallHistoryEntry>,
    val note: String,
)

/** Single source of truth for "was this call ever answered" — same check
 * [callerid.callblocker.spamcall.callerinfo.phonecall.telecom.MissedCallNotifier] uses for its
 * own missed-call notification. Shared by [HeaderBand]'s status line and the call_ended analytics
 * event below so neither has to duplicate the raw `durationSeconds <= 0L && isIncoming` check. */
private fun isCallMissed(isIncoming: Boolean, durationSeconds: Long): Boolean =
    durationSeconds <= 0L && isIncoming

/** Build-2 QA #16: a call the user actively declined (Incoming Call's Decline button, or a quick
 * reply) was previously labelled "Missed call" same as one that simply rang out unanswered —
 * [wasDeclined] (plumbed from [callerid.callblocker.spamcall.callerinfo.phonecall.telecom.CallSummary])
 * is what tells the two apart here. */
private fun callStatusWord(isIncoming: Boolean, durationSeconds: Long, wasDeclined: Boolean): Int = when {
    wasDeclined -> R.string.call_status_declined
    isCallMissed(isIncoming, durationSeconds) -> R.string.call_status_missed
    else -> R.string.call_status_ended
}

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
    wasDeclined: Boolean,
    endedAtMillis: Long,
    onDismiss: () -> Unit,
    onOpenContactDetail: (phoneNumber: String) -> Unit,
    onCall: (phoneNumber: String) -> Unit,
    modifier: Modifier = Modifier,
    adsViewModel: CallEndedAdsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val bannerState by adsViewModel.bannerState.collectAsState()

    var data by remember { mutableStateOf<CallEndedRealData?>(null) }
    var isBlocked by remember { mutableStateOf(false) }
    var spamReported by remember { mutableStateOf(false) }
    // Build-2 QA #21: "Report spam" here used to go straight to SpamMarkRepository with a
    // hardcoded SpamCategory.OTHER, never asking which kind of spam it was — the one report
    // entry point in the app that skipped the picker every other one (Contact Detail's "Block &
    // Report Spam") already shows. Same SpamCategoryPickerSheet, same two-step flow now.
    var showSpamCategoryPicker by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    // Separate from [visible]: the card now starts hidden while its data loads, so "not visible"
    // no longer implies "being dismissed" — keying the dismiss effect on visible alone would
    // close this popup 200ms after launch, before the card ever appeared.
    var dismissRequested by remember { mutableStateOf(false) }

    LaunchedEffect(phoneNumber) {
        // The card used to be made visible here, BEFORE the lookup below — so its first frames
        // rendered with contact == null (raw number in the header, number-only actions) and then
        // swapped to the contact's name once this landed. Now only the dim scrim paints
        // immediately; the card animates in once, already showing its final identity.
        val loaded = withContext(Dispatchers.IO) {
            val contactsRepository = ContactsRepository()
            val spamMarkRepository = SpamMarkRepository()
            val blockedNumbersRepository = BlockedNumbersRepository()
            CallEndedRealData(
                contact = contactsRepository.getContactByPhoneNumber(context, phoneNumber, includeDetails = true),
                spamMark = spamMarkRepository.getSpamMark(context, phoneNumber),
                isBlocked = blockedNumbersRepository.isNumberBlocked(context, phoneNumber),
                recentCalls = CallLogRepository().getCallHistoryForNumber(context, phoneNumber, limit = RECENT_CALLS_SHOWN),
                note = CallNotesRepository().getNote(context, phoneNumber),
            )
        }
        data = loaded
        isBlocked = loaded.isBlocked
        if (!dismissRequested) visible = true

        // Fired once per call-ended screen shown, same as CallEndedActivity's screen_view — but
        // necessarily here rather than there: is_spam depends on the real spamMark lookup above
        // (SpamMarkRepository, same IO-dispatched read CallEndedCard's own `isSpam` already
        // derives from), which CallEndedActivity.onCreate can't have synchronously without
        // blocking the main thread the way this screen deliberately avoids everywhere else. Keyed
        // on phoneNumber like the rest of this LaunchedEffect, so this can't double-fire on
        // recomposition — a given CallEndedActivity instance only ever shows one phoneNumber.
        AnalyticsHelper.logEvent(
            "call_ended",
            Bundle().apply {
                putString("call_type", if (isCallMissed(isIncoming, durationSeconds)) "missed" else "answered")
                putBoolean("is_spam", loaded.spamMark != null)
            },
        )
    }

    fun requestDismiss() {
        visible = false
        dismissRequested = true
    }
    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            // Matches the AnimatedVisibility exit spec below — gives the fade/scale-out time to
            // actually play before the Activity is finished.
            delay(150)
            onDismiss()
        }
    }

    // A full screen of its own, not a card floating over a dimmed backdrop: the opaque surface
    // fills the window, the header runs up behind the status bar, the ad sits at the bottom.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            // Content + ad. The ad is drawn inside the content's own
            // AnimatedVisibility so it appears/leaves with the card — but [visible] and the dismiss
            // timing above never look at the ad: it is normally preloaded (CallEndBannerAdManager)
            // and otherwise simply shows up here whenever it's ready.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Not the status bar: HeaderBand pads itself so its colour fills that strip.
                    .navigationBarsPadding()
                    // The note field: keeps it above the keyboard.
                    .imePadding(),
            ) {
                CallEndedCard(
                    phoneNumber = phoneNumber,
                    isIncoming = isIncoming,
                    durationSeconds = durationSeconds,
                    wasDeclined = wasDeclined,
                    endedAtMillis = endedAtMillis,
                    contact = data?.contact,
                    spamMark = data?.spamMark,
                    isBlocked = isBlocked,
                    spamReported = spamReported,
                    recentCalls = data?.recentCalls.orEmpty(),
                    savedNote = data?.note.orEmpty(),
                    onSaveNote = { note ->
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { CallNotesRepository().setNote(context, phoneNumber, note) }
                            Toast.makeText(context, R.string.call_ended_note_saved, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onWhatsApp = {
                        if (!context.openWhatsAppChat(phoneNumber)) {
                            Toast.makeText(context, R.string.contact_detail_whatsapp_not_installed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = ::requestDismiss,
                    onCall = { onCall(phoneNumber) },
                    onMessage = {
                        context.startExternalActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")))
                    },
                    onSave = {
                        context.startExternalActivity(
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
                    onReportSpam = { showSpamCategoryPicker = true },
                    modifier = Modifier.weight(1f),
                )
                // Below the card, never inside it, and measured BEFORE it: the ad takes its own
                // height and the card (weight; see FitColumn) takes what's left — so on a
                // short screen, or with the keyboard up, it is the card that gives way, never the
                // ad. Which ad is Remote Config's choice: the banner has a fixed height; the
                // native ad fits itself into at most NativeAdMaxHeight (its media shrinks, see
                // NativeAdSlot) and takes no space at all until it has something to show.
                when (adsViewModel.format) {
                    CallEndAdFormat.BANNER -> CallEndBannerAdSlot(
                        state = bannerState,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    CallEndAdFormat.NATIVE -> NativeAdSlot(
                        controller = adsViewModel.nativeAdController,
                        modifier = Modifier
                            .heightIn(max = NativeAdMaxHeight)
                            .padding(bottom = 12.dp),
                    )
                }
            }
        }

        if (showSpamCategoryPicker) {
            SpamCategoryPickerSheet(
                onDismissRequest = { showSpamCategoryPicker = false },
                onCategorySelected = { category ->
                    showSpamCategoryPicker = false
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            SpamMarkRepository().markAsSpam(context, phoneNumber, category)
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
    wasDeclined: Boolean,
    endedAtMillis: Long,
    contact: Contact?,
    spamMark: SpamMark?,
    isBlocked: Boolean,
    spamReported: Boolean,
    recentCalls: List<CallHistoryEntry>,
    savedNote: String,
    onSaveNote: (String) -> Unit,
    onWhatsApp: () -> Unit,
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

    // Nothing on this screen scrolls. The content is given whatever height is left above the ad
    // and FitColumn lays out as many sections as fit in it at their full size, leaving out the
    // least important ones (see DropOrder) rather than squeezing or clipping any of them.
    //
    // Reading order, top to bottom: who it was and how the call went (hero) -> what to do about
    // it (one filled primary action, the rest tonal) -> context (recent calls) -> the user's own
    // input (note, spam verdict). One accent colour per screen: blue, or red for a spam number.
    FitColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HeroBand(
            isSpam = isSpam,
            isIncoming = isIncoming,
            durationSeconds = durationSeconds,
            wasDeclined = wasDeclined,
            endedAtMillis = endedAtMillis,
            contact = contact,
            spamMark = spamMark,
            phoneNumber = phoneNumber,
            profileExpanded = expanded,
            onToggleProfile = { expanded = !expanded },
            onDismiss = onDismiss,
        )

        if (expanded) {
            Box(modifier = Modifier.layoutId(DropOrder.PROFILE)) {
                ProfileDetailsPanel(
                    isSpam = isSpam,
                    contact = contact,
                    spamMark = spamMark,
                    callCount = callCount,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenMargin)
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val accent = if (isSpam) MaterialTheme.extendedColors.spam else MaterialTheme.colorScheme.primary
            // The one filled button: calling back is what this screen is most often opened for.
            ActionButton(
                icon = Icons.Filled.Call,
                label = stringResource(R.string.call_ended_action_call),
                tint = accent,
                filled = true,
                onClick = onCall,
            )
            ActionButton(
                icon = Icons.AutoMirrored.Filled.Message,
                label = stringResource(R.string.call_ended_action_message),
                tint = accent,
                onClick = onMessage,
            )
            WhatsAppActionButton(onClick = onWhatsApp)
            if (!isSpam && contact != null) {
                ActionButton(
                    icon = Icons.Filled.Person,
                    label = stringResource(R.string.call_ended_action_contact),
                    tint = accent,
                    onClick = onOpenContact,
                )
            } else {
                ActionButton(
                    icon = Icons.Filled.Bookmark,
                    label = stringResource(R.string.call_ended_action_save),
                    tint = accent,
                    onClick = onSave,
                )
            }
            ActionButton(
                icon = Icons.Filled.Block,
                label = if (isBlocked) stringResource(R.string.call_ended_action_unblock) else stringResource(R.string.call_ended_action_block),
                tint = if (isBlocked) accent else MaterialTheme.extendedColors.spam,
                onClick = onToggleBlock,
            )
        }

        // Each recent call is its own FitColumn child, so on a shorter screen the list loses
        // its oldest rows one at a time instead of disappearing whole.
        recentCalls.forEachIndexed { index, call ->
            Column(
                modifier = Modifier
                    .layoutId(if (index == 0) DropOrder.RECENT_CALLS_FIRST else DropOrder.RECENT_CALLS_OLDER + index)
                    .fillMaxWidth()
                    .padding(horizontal = ScreenMargin),
            ) {
                if (index == 0) {
                    SectionLabel(text = stringResource(R.string.call_ended_recent_calls_title))
                }
                RecentCallRow(call)
            }
        }

        Box(modifier = Modifier.layoutId(DropOrder.NOTE)) {
            CallNoteSection(savedNote = savedNote, onSaveNote = onSaveNote)
        }

        if (!isSpam) {
            Box(modifier = Modifier.layoutId(DropOrder.SPAM_QUESTION)) {
                SpamReportSection(reported = spamReported, onReportSpam = onReportSpam, onDismiss = onDismiss)
            }
        }
    }
}

/**
 * Which of the card's sections give way first when the screen is too short for all of them
 * (higher goes first; REQUIRED never does): the older recent calls, then the spam question, the
 * recent-calls list altogether, View profile, and last the note. The header and the action row
 * always stay.
 */
private object DropOrder {
    const val REQUIRED = 0
    const val NOTE = 1
    const val PROFILE = 2
    const val RECENT_CALLS_FIRST = 3
    const val SPAM_QUESTION = 4
    // + the row's index, so the oldest row has the highest value and goes first.
    const val RECENT_CALLS_OLDER = 5
}

/**
 * A Column that never scrolls, squeezes or clips: every child is measured at its natural height,
 * and when they don't all fit in the height available, whole children are left out — highest
 * [DropOrder] (set with Modifier.layoutId) first — until the rest do. Children without a
 * layoutId are REQUIRED.
 */
@Composable
private fun FitColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val childConstraints = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(childConstraints) }
        val dropOrder = measurables.map { (it.layoutId as? Int) ?: DropOrder.REQUIRED }
        val kept = BooleanArray(placeables.size) { true }
        var totalHeight = placeables.sumOf { it.height }
        if (constraints.hasBoundedHeight) {
            placeables.indices
                .filter { dropOrder[it] > DropOrder.REQUIRED }
                .sortedByDescending { dropOrder[it] }
                .forEach { index ->
                    if (totalHeight > constraints.maxHeight) {
                        kept[index] = false
                        totalHeight -= placeables[index].height
                    }
                }
        }
        layout(constraints.maxWidth, constraints.constrainHeight(totalHeight)) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                if (kept[index]) {
                    placeable.place(0, y)
                    y += placeable.height
                }
            }
        }
    }
}

/** Small all-caps-free section heading: quieter than the content under it, so the eye lands on
 * the data, not the label. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.extendedColors.onSurfaceSoft,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/** One of this number's latest calls (the one that just ended first), so the screen answers
 * "when did we last speak / how often do they call" without opening Contact Detail. Same
 * direction glyphs and colours as the Recent list's rows (CallLogRow), so it reads at a glance. */
@Composable
private fun RecentCallRow(call: CallHistoryEntry) {
    val (icon, tint) = when (call.callType) {
        CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to MaterialTheme.extendedColors.callOutgoing
        CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to MaterialTheme.extendedColors.callMissed
        CallType.INCOMING, CallType.OTHER -> Icons.AutoMirrored.Filled.CallReceived to MaterialTheme.extendedColors.callIncoming
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = when (call.callType) {
                CallType.INCOMING -> stringResource(R.string.contact_detail_call_type_incoming)
                CallType.OUTGOING -> stringResource(R.string.contact_detail_call_type_outgoing)
                CallType.MISSED -> stringResource(R.string.call_status_missed)
                CallType.OTHER -> stringResource(R.string.contact_detail_call_type_other)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (call.durationSeconds > 0L) {
                stringResource(
                    R.string.call_ended_status_line,
                    formatRelativeShort(call.timestamp),
                    formatCallTimer(call.durationSeconds.toInt()),
                )
            } else {
                formatRelativeShort(call.timestamp)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.onSurfaceSoft,
        )
    }
}

/** A free-text note about this number (see [CallNotesRepository]) — shows the saved one, and
 * offers Save (inside the field, so typing never changes the screen's layout) only while the
 * text differs from it. A filled, borderless field: it should look like a place to jot something
 * down, not like a form. */
@Composable
private fun CallNoteSection(savedNote: String, onSaveNote: (String) -> Unit) {
    var note by remember(savedNote) { mutableStateOf(savedNote) }
    var lastSaved by remember(savedNote) { mutableStateOf(savedNote) }
    TextField(
        value = note,
        onValueChange = { note = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin)
            .padding(top = 10.dp, bottom = 12.dp),
        placeholder = { Text(stringResource(R.string.call_ended_note_placeholder)) },
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        shape = SectionShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.onSurfaceSoft,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = if (note.trim() != lastSaved) {
            {
                IconButton(
                    onClick = {
                        lastSaved = note.trim()
                        onSaveNote(note)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = stringResource(R.string.call_ended_note_save),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            null
        },
    )
}

/** Same footprint as [ActionButton], but with WhatsApp's full-colour logo — tinting would
 * flatten it. */
@Composable
private fun WhatsAppActionButton(onClick: () -> Unit) {
    val label = stringResource(R.string.contact_detail_action_whatsapp)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_whatsapp),
            contentDescription = null,
            modifier = Modifier
                .size(ActionButtonSize)
                .clip(ActionButtonShape)
                .clickable(role = Role.Button, onClick = onClick),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extendedColors.onSurfaceSoft,
            textAlign = TextAlign.Center,
        )
    }
}

/** "Was this call spam?" — only for a number not already marked. A tonal card with the question
 * and its two answers; the destructive answer carries the colour, the harmless one is outlined.
 * [onReportSpam] opens SpamCategoryPickerSheet (see CallEndedRoute); "No, it's fine" has nothing
 * to persist, so it just closes the screen via the same [onDismiss] the header's X uses. Once
 * [reported], the card collapses to a one-line confirmation. */
@Composable
private fun SpamReportSection(reported: Boolean, onReportSpam: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenMargin)
            .padding(bottom = 12.dp)
            .clip(SectionShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = if (reported) MaterialTheme.extendedColors.spam else MaterialTheme.extendedColors.onSurfaceSoft,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(if (reported) R.string.call_ended_reported_as_spam else R.string.spam_report_question),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (!reported) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                ) {
                    Text(text = stringResource(R.string.spam_report_not_spam), style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onReportSpam,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
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

/**
 * The hero: who it was and how the call went, in the screen's one saturated colour (brand blue,
 * or red for a number marked as spam) running up behind the status bar. Hierarchy by size and
 * weight, not by more colour — the name/number is the largest thing on the screen, the outcome
 * sits under it as quiet translucent chips. Tapping the identity row opens the profile details
 * (the chevron next to the name says so), replacing the old full-width "View profile" button.
 */
@Composable
private fun HeroBand(
    isSpam: Boolean,
    isIncoming: Boolean,
    durationSeconds: Long,
    wasDeclined: Boolean,
    endedAtMillis: Long,
    contact: Contact?,
    spamMark: SpamMark?,
    phoneNumber: String,
    profileExpanded: Boolean,
    onToggleProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val gradient = if (isSpam) {
        Brush.linearGradient(listOf(SpamGradientStart, SpamGradientEnd))
    } else {
        Brush.linearGradient(listOf(NormalGradientStart, NormalGradientEnd))
    }

    val isMissed = isCallMissed(isIncoming, durationSeconds)
    val statusWord = stringResource(callStatusWord(isIncoming, durationSeconds, wasDeclined))
    val formattedNumber = formatPhoneNumberForDisplay(phoneNumber)
    val titleLine = when {
        isSpam -> spamMark!!.category.displayLabel()
        contact != null -> contact.name
        else -> formattedNumber
    }
    // The number, whenever the title above isn't already the number itself.
    val subtitleLine = if (isSpam || contact != null) formattedNumber else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(gradient)
            .statusBarsPadding()
            .padding(start = ScreenMargin, end = 8.dp, top = 8.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.call_ended_view_profile),
                        onClick = onToggleProfile,
                    )
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderAvatar(isSpam = isSpam, contact = contact)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = titleLine,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitleLine != null) {
                        Text(
                            text = subtitleLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = if (profileExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(22.dp),
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.call_ended_dismiss_content_description),
                    tint = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroChip(
                icon = when {
                    isMissed || wasDeclined -> Icons.AutoMirrored.Filled.CallMissed
                    isIncoming -> Icons.AutoMirrored.Filled.CallReceived
                    else -> Icons.AutoMirrored.Filled.CallMade
                },
                text = statusWord,
            )
            if (durationSeconds > 0L) {
                HeroChip(icon = Icons.Filled.Schedule, text = formatCallTimer(durationSeconds.toInt()))
            }
            Text(
                text = if (isSpam) {
                    stringResource(R.string.call_ended_marked_as_spam_subtext, formatRelativeShort(spamMark!!.markedAt))
                } else {
                    formatRelativeShort(endedAtMillis)
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A quiet translucent pill on the hero: one fact about the call. */
@Composable
private fun HeroChip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
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
            .size(60.dp)
            // A translucent ring lifts the avatar off the gradient without a hard edge.
            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .padding(3.dp)
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
                modifier = Modifier.fillMaxSize().clip(CircleShape),
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
            .padding(horizontal = ScreenMargin)
            .padding(top = 12.dp)
            .clip(SectionShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
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
private fun ActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    // The screen's single primary action is solid [tint]; every other one is a soft tint of it.
    filled: Boolean = false,
) {
    // Only the squircle is the touch target (so the ripple matches its shape and never clips the
    // label); merging lets TalkBack read the label as the button's name.
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(ActionButtonSize)
                .clip(ActionButtonShape)
                .background(if (filled) tint else tint.copy(alpha = 0.12f))
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (filled) Color.White else tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.extendedColors.onSurfaceSoft,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal,
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
