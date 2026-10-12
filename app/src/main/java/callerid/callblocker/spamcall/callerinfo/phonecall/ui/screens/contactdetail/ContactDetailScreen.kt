package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.contactdetail

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.CallHistoryEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.CallType
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.SimCallLogIndicator
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.SpamCategoryPickerSheet
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ChipShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatRelativeDayLabel
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatTimeOfDay
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.initialsOf
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.normalizeDialedNumber
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.openWhatsAppChat
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.startExternalActivity
import coil.compose.AsyncImage

/**
 * Real contact_detail screen — [ContactDetailViewModel] looks the nav-argument phone number
 * up against the Contacts provider. Shows the saved contact's name/photo on a match, otherwise an
 * honest "Unknown number" state (no fabricated spam/report data — no real spam detection exists).
 */
@Composable
fun ContactDetailScreen(
    onBack: () -> Unit,
    onNavigateToBlockConfirm: (phoneNumber: String) -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactDetailViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Blocking happens on the separate block_confirm screen, so the blocked state shown here
    // can go stale the moment that screen is popped back to this one — re-check on resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshBlockedState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // "Block & Report Spam": already blocked → the existing one-tap unblock; already spam-marked →
    // straight to block_confirm; otherwise the existing report (category picker) flow first, and
    // block_confirm once a category is picked (see the SpamCategoryPickerSheet below).
    val onBlockAndReport: () -> Unit = {
        when {
            uiState.isBlocked -> viewModel.unblockNumber()
            uiState.spamMark != null -> onNavigateToBlockConfirm(uiState.phoneNumber)
            else -> viewModel.onReportClicked()
        }
    }

    val onMessage: () -> Unit = { context.startExternalActivity(smsIntent(uiState.phoneNumber)) }
    val onWhatsApp: () -> Unit = {
        if (!context.openWhatsAppChat(uiState.phoneNumber)) viewModel.onWhatsAppUnavailable()
    }
    // No video-call support exists anywhere in the app (ActiveCallScreen's own Video button just
    // shows an "unsupported" toast) — this used to silently place a normal voice call instead,
    // indistinguishable from tapping Call, with no indication video wasn't actually used
    // (build-2 QA N2: "WhatsApp and Video buttons do nothing"). Now gives the same honest
    // unavailable feedback WhatsApp already gets, rather than disguising it as a real call.
    val onVideo: () -> Unit = { viewModel.onVideoCallUnavailable() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarMessageShown()
        }
    }

    // Once the contact no longer exists, there's nothing left for this screen to show — leave
    // back to wherever the user came from (Recents or Contacts list).
    LaunchedEffect(uiState.deleted) {
        if (uiState.deleted) onBack()
    }

    // WRITE_CONTACTS is a separate dangerous permission from READ_CONTACTS (already granted
    // during onboarding) — only requested here, just-in-time, since Delete is the one real-world
    // action in this whole app that needs it.
    val writeContactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onDeleteConfirmed() }

    val onConfirmDelete: () -> Unit = {
        val hasWriteContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasWriteContacts) {
            viewModel.onDeleteConfirmed()
        } else {
            writeContactsLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Header lives in the content Column below, not the topBar slot: see HomeScreen's Scaffold for
        // why (topBar-height-derived content padding goes stale on a cold-start inset change).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.dialpad_back_content_description),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box {
                    IconButton(onClick = viewModel::onOverflowMenuOpened, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.contact_detail_more_content_description),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    ContactOverflowMenu(
                        expanded = uiState.showOverflowMenu,
                        hasContact = uiState.contact != null,
                        isSpamMarked = uiState.spamMark != null,
                        onDismiss = viewModel::onOverflowMenuDismissed,
                        onCopyName = viewModel::onCopyNameClicked,
                        onCopyNumber = viewModel::onCopyNumberClicked,
                        onCopyContact = viewModel::onCopyContactClicked,
                        onAddToContacts = {
                            viewModel.onOverflowMenuDismissed()
                            context.startExternalActivity(addContactIntent(uiState.phoneNumber))
                        },
                        onToggleSpamMark = viewModel::onToggleSpamMarkClicked,
                        onDeleteClicked = viewModel::onDeleteClicked,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val contact = uiState.contact
                        IdentityBlock(
                            contact = contact,
                            phoneNumber = uiState.phoneNumber,
                            onNumberLongPress = viewModel::onNumberLongPressed,
                        )
                        if (uiState.spamMark != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            SpamMarkChip(onUnmark = viewModel::onUnmarkSpamClicked)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        ActionRow(
                            onCall = onCall,
                            onMessage = onMessage,
                            onWhatsApp = onWhatsApp,
                            onVideo = onVideo,
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        NumberCard(
                            phoneNumber = uiState.phoneNumber,
                            onMessage = onMessage,
                            onNumberLongPress = viewModel::onNumberLongPressed,
                        )

                        if (contact != null && (contact.organization != null || contact.email != null)) {
                            Spacer(modifier = Modifier.height(12.dp))
                            DetailCard(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) {
                                contact.organization?.let { AboutRow(icon = Icons.Filled.Work, text = it) }
                                contact.email?.let { AboutRow(icon = Icons.Filled.Mail, text = it) }
                            }
                        }

                        CallHistorySection(callHistory = uiState.callHistory)

                        BlockAndReportRow(isBlocked = uiState.isBlocked, onClick = onBlockAndReport)

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (uiState.showReportSheet) {
        SpamCategoryPickerSheet(
            onDismissRequest = viewModel::onReportSheetDismissed,
            onCategorySelected = { category ->
                viewModel.onReportCategorySelected(category)
                onNavigateToBlockConfirm(uiState.phoneNumber)
            },
        )
    }

    if (uiState.showDeleteConfirmDialog) {
        val contactName = uiState.contact?.name ?: stringResource(R.string.contact_detail_delete_fallback_name)
        AlertDialog(
            onDismissRequest = viewModel::onDeleteCancelled,
            title = { Text(stringResource(R.string.contact_detail_delete_dialog_title, contactName)) },
            text = { Text(stringResource(R.string.contact_detail_delete_dialog_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmDelete, enabled = !uiState.isDeleting) {
                    Text(text = stringResource(R.string.contact_detail_dialog_delete_button), color = MaterialTheme.extendedColors.spam)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteCancelled, enabled = !uiState.isDeleting) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ContactOverflowMenu(
    expanded: Boolean,
    hasContact: Boolean,
    isSpamMarked: Boolean,
    onDismiss: () -> Unit,
    onCopyName: () -> Unit,
    onCopyNumber: () -> Unit,
    onCopyContact: () -> Unit,
    onAddToContacts: () -> Unit,
    onToggleSpamMark: () -> Unit,
    onDeleteClicked: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // Copy name/Copy contact only make sense when there's a real saved name — an unsaved
        // Recents number just gets "Copy number" (and no Delete: there's no contact record to
        // remove).
        if (hasContact) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.contact_detail_copy_name)) },
                leadingIcon = { Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null) },
                onClick = onCopyName,
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.contact_detail_copy_number)) },
            leadingIcon = { Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = onCopyNumber,
        )
        // An unsaved number's only route to the system "new contact" form now that the action row
        // no longer has a Save button.
        if (!hasContact) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.contact_detail_add_to_contacts)) },
                leadingIcon = { Icon(imageVector = Icons.Filled.PersonAdd, contentDescription = null) },
                onClick = onAddToContacts,
            )
        }
        // Spam-marking only needs the phone number, not a saved contact record, so — unlike
        // Copy name/Copy contact/Delete — it stays available for unsaved numbers too.
        DropdownMenuItem(
            text = {
                Text(
                    if (isSpamMarked) {
                        stringResource(R.string.contact_detail_unmark_spam)
                    } else {
                        stringResource(R.string.contact_detail_mark_spam)
                    },
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.spam,
                )
            },
            onClick = onToggleSpamMark,
        )
        if (hasContact) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.contact_detail_copy_contact)) },
                leadingIcon = { Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null) },
                onClick = onCopyContact,
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.contact_detail_delete_contact), color = MaterialTheme.extendedColors.spam) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.spam,
                    )
                },
                onClick = onDeleteClicked,
            )
        }
    }
}

private val DetailCardShape = RoundedCornerShape(18.dp)
private val ActionButtonShape = RoundedCornerShape(20.dp)

/** WhatsApp's brand green — deliberately a fixed literal, not a theme token. */
private val WhatsAppGreen = Color(0xFF25D366)

@Composable
private fun IdentityBlock(contact: Contact?, phoneNumber: String, onNumberLongPress: () -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))

    val avatarModifier = Modifier
        .size(104.dp)
        .clip(CircleShape)
    if (contact?.photoUri != null) {
        AsyncImage(model = contact.photoUri, contentDescription = null, modifier = avatarModifier)
    } else {
        Box(
            modifier = avatarModifier.background(MaterialTheme.extendedColors.accentContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (contact != null) {
                Text(
                    text = initialsOf(contact.name),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = contact?.name ?: stringResource(R.string.active_call_unknown_caller),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = stringResource(
            R.string.contact_detail_number_subtitle,
            stringResource(R.string.contact_detail_number_label),
            formatPhoneNumberForDisplay(phoneNumber),
        ),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.copyNumberOnLongPress(onNumberLongPress),
    )
}

@Composable
private fun ActionRow(
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onWhatsApp: () -> Unit,
    onVideo: () -> Unit,
) {
    val outlinedBackground = MaterialTheme.colorScheme.surface
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val onOutlined = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally)) {
        ActionButton(
            iconRes = R.drawable.ic_call,
            iconTint = MaterialTheme.colorScheme.onPrimary,
            label = stringResource(R.string.contact_detail_action_call),
            background = MaterialTheme.colorScheme.primary,
            onClick = onCall,
        )
        ActionButton(
            iconRes = R.drawable.ic_message,
            iconTint = onOutlined,
            label = stringResource(R.string.contact_detail_action_message),
            background = outlinedBackground,
            borderColor = outlineColor,
            onClick = onMessage,
        )
        ActionButton(
            iconRes = R.drawable.ic_whatsapp,
            // Full-colour logo asset (green bubble + white handset) — tinting would flatten it.
            iconTint = Color.Unspecified,
            label = stringResource(R.string.contact_detail_action_whatsapp),
            background = WhatsAppGreen,
            onClick = onWhatsApp,
        )
        ActionButton(
            iconRes = R.drawable.ic_videocam,
            iconTint = onOutlined,
            label = stringResource(R.string.contact_detail_action_video),
            background = outlinedBackground,
            borderColor = outlineColor,
            onClick = onVideo,
        )
    }
}

@Composable
private fun ActionButton(
    @DrawableRes iconRes: Int,
    iconTint: Color,
    label: String,
    background: Color,
    onClick: () -> Unit,
    borderColor: Color? = null,
) {
    // Only the squircle is the touch target (so the ripple matches its shape and never clips the
    // label); merging lets TalkBack read the label as the button's name.
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(ActionButtonShape)
                .background(background)
                .let { if (borderColor != null) it.border(1.dp, borderColor, ActionButtonShape) else it }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.extendedColors.onSurfaceSoft,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun DetailCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = DetailCardShape)
            .clip(DetailCardShape)
            .background(MaterialTheme.colorScheme.surface)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .then(modifier),
    ) {
        content()
    }
}

@Composable
private fun IconChip(icon: ImageVector, tint: Color, background: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(ChipShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun NumberCard(phoneNumber: String, onMessage: () -> Unit, onNumberLongPress: () -> Unit) {
    DetailCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(
                icon = Icons.Filled.Call,
                tint = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.extendedColors.accentContainer,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatPhoneNumberForDisplay(phoneNumber),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.copyNumberOnLongPress(onNumberLongPress),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.contact_detail_number_label),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.extendedColors.accentContainer)
                    .clickable(role = Role.Button, onClick = onMessage),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_message),
                    contentDescription = stringResource(R.string.contact_detail_action_message),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun BlockAndReportRow(isBlocked: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.error
    DetailCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(icon = Icons.Filled.Block, tint = color, background = color.copy(alpha = 0.14f))
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = if (isBlocked) {
                    stringResource(R.string.contact_detail_action_unblock)
                } else {
                    stringResource(R.string.contact_detail_block_report_spam)
                },
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = color,
            )
        }
    }
}

/**
 * Long-press-to-copy on the displayed number, with the same LongPress haptic SwipeActionRow uses
 * for its trigger. The semantics action exposes it to TalkBack, which can't see pointerInput.
 */
@Composable
private fun Modifier.copyNumberOnLongPress(onLongPress: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.contact_detail_copy_number)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    return this
        .semantics {
            onLongClick(label = label) {
                currentOnLongPress()
                true
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnLongPress()
                },
            )
        }
}

private fun smsIntent(phoneNumber: String): Intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber"))

private fun addContactIntent(phoneNumber: String): Intent =
    Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
        putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
    }

/** This device's own local spam mark — deliberately worded as this device's opinion (never
 * "reported by N users" or similar), since no community/shared data exists behind it. */
@Composable
private fun SpamMarkChip(onUnmark: () -> Unit) {
    val color = MaterialTheme.extendedColors.spam
    Row(
        modifier = Modifier
            .clip(ChipShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Flag,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.contact_detail_spam_marked_you),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.contact_detail_undo),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.clickable(onClick = onUnmark),
        )
    }
}

private val CallHistoryCardShape = RoundedCornerShape(20.dp)
private val FilterTabsShape = RoundedCornerShape(14.dp)
private val FilterTabShape = RoundedCornerShape(11.dp)

private enum class CallHistoryFilter { ALL, MISSED }

@Composable
private fun CallHistoryFilter.label(): String = when (this) {
    CallHistoryFilter.ALL -> stringResource(R.string.contact_detail_filter_all)
    CallHistoryFilter.MISSED -> stringResource(R.string.contact_detail_filter_missed)
}

@Composable
private fun CallHistorySection(callHistory: List<CallHistoryEntry>) {
    var filter by remember { mutableStateOf(CallHistoryFilter.ALL) }

    Spacer(modifier = Modifier.height(32.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.contact_detail_call_history_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.extendedColors.onSurfaceFaint,
        )
        Spacer(modifier = Modifier.width(8.dp))
        CountBadge(count = callHistory.size)
    }

    CallHistoryFilterTabs(
        selected = filter,
        onSelect = { filter = it },
        modifier = Modifier.padding(bottom = 14.dp),
    )

    val filteredHistory = remember(callHistory, filter) {
        when (filter) {
            CallHistoryFilter.ALL -> callHistory
            CallHistoryFilter.MISSED -> callHistory.filter { it.callType == CallType.MISSED }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = CallHistoryCardShape)
            .clip(CallHistoryCardShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (filteredHistory.isEmpty()) {
            CallHistoryEmptyState(
                message = if (filter == CallHistoryFilter.MISSED) {
                    stringResource(R.string.contact_detail_no_missed_calls)
                } else {
                    stringResource(R.string.contact_detail_no_call_history)
                },
            )
        } else {
            // Grouped (not a flat forEach) so every day-group label gets identical top/bottom
            // padding from its own Modifier, regardless of whether it's the first group in the
            // card or a later one — the previous version special-cased the first row/group and
            // ended up with mismatched spacing.
            val grouped = remember(filteredHistory) {
                filteredHistory.groupBy { formatRelativeDayLabel(it.timestamp) }
            }
            grouped.forEach { (dayLabel, entriesForDay) ->
                DayGroupLabel(text = dayLabel)
                entriesForDay.forEachIndexed { index, entry ->
                    CallHistoryRow(entry)
                    // Divider between rows within a day group only — the next group's own
                    // label already acts as the separator, so no divider directly above it.
                    if (index != entriesForDay.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(ChipShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.extendedColors.onSurfaceSoft,
        )
    }
}

@Composable
private fun CallHistoryFilterTabs(
    selected: CallHistoryFilter,
    onSelect: (CallHistoryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(FilterTabsShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        CallHistoryFilter.values().forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .let { if (isSelected) it.shadow(elevation = 1.dp, shape = FilterTabShape) else it }
                    .clip(FilterTabShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = tab.label(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.extendedColors.onSurfaceSoft
                    },
                )
            }
        }
    }
}

@Composable
private fun DayGroupLabel(text: String) {
    // Fixed, unconditional padding on every instance (never varies for the first group vs a
    // later one) is what keeps the spacing identical throughout the list.
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        ),
        color = MaterialTheme.extendedColors.onSurfaceFaint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun CallHistoryEmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.CallMissed,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.onSurfaceFaint,
            modifier = Modifier.size(32.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.onSurfaceFaint,
        )
    }
}

@Composable
private fun CallHistoryRow(entry: CallHistoryEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, chipColor) = when (entry.callType) {
            CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to MaterialTheme.extendedColors.success
            CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to MaterialTheme.colorScheme.primary
            CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to MaterialTheme.extendedColors.spam
            CallType.OTHER -> Icons.AutoMirrored.Filled.CallReceived to MaterialTheme.extendedColors.success
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(ChipShape)
                .background(chipColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = chipColor,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val label = when (entry.callType) {
                CallType.INCOMING -> stringResource(R.string.contact_detail_call_type_incoming)
                CallType.OUTGOING -> stringResource(R.string.contact_detail_call_type_outgoing)
                CallType.MISSED -> stringResource(R.string.call_status_missed)
                CallType.OTHER -> stringResource(R.string.contact_detail_call_type_other)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
                color = if (entry.callType == CallType.MISSED) {
                    MaterialTheme.extendedColors.spam
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTimeOfDay(entry.timestamp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.extendedColors.onSurfaceFaint,
                )
                entry.simLabel?.let { simLabel ->
                    Spacer(modifier = Modifier.width(8.dp))
                    SimCallLogIndicator(label = simLabel, color = entry.simColor)
                }
            }
        }

        Box(
            modifier = Modifier.width(44.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = if (entry.callType == CallType.MISSED) {
                    stringResource(R.string.contact_detail_duration_dash)
                } else {
                    formatCallDuration(entry.durationSeconds)
                },
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.extendedColors.onSurfaceSoft,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun formatCallDuration(durationSeconds: Long): String {
    if (durationSeconds <= 0L) return stringResource(R.string.contact_detail_duration_dash)
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return if (minutes > 0) {
        stringResource(R.string.contact_detail_duration_minutes_seconds, minutes, seconds)
    } else {
        stringResource(R.string.contact_detail_duration_seconds, seconds)
    }
}

@Composable
private fun AboutRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.onSurfaceFaint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
