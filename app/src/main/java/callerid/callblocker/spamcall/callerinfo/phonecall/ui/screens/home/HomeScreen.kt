package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptKind
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.CallType
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.RecentCallEntry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.BottomNavDestination
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CalloraBottomNavBar
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.PermissionPreviewBottomSheet
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.PermissionPromptCard
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.SwipeActionRow
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CardShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatRelativeCallTimestamp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenNotifications: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSpamCenter: () -> Unit,
    onOpenContactDetail: (phoneNumber: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAllContacts: () -> Unit,
    onOpenDialpad: () -> Unit,
    onCall: (phoneNumber: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onScreenResumed() }

    fun requestCallLogPermissions() {
        callLogPermissionLauncher.launch(
            arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE),
        )
    }

    // Just-in-time call-log permission popup — shown once per install on first visit to Home,
    // regardless of current grant state (RoleManager's dialer-role prompt can silently
    // auto-grant this on some devices; the popup should still get its one-time appearance so the
    // user sees it, just with "Got it" instead of "Allow access" — see
    // PermissionPreviewBottomSheet). PermissionPromptRepository's flag is what actually prevents
    // it reappearing on later visits.
    var showCallLogPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!PermissionPromptRepository().hasBeenShown(context, PermissionPromptKind.CALL_LOG)) {
            showCallLogPrompt = true
        }
    }

    // Home is reached constantly via back-navigation (Contacts/Spam Center/Settings/Contact
    // Detail all return here) — onScreenResumed() re-checks permissions but, unlike refresh(),
    // never blows away already-loaded data or shows the spinner just because the screen resumed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Infinite scroll: fetch the next page once the user scrolls within a few rows of the end of
    // whatever's currently loaded. Comparing against the LazyColumn's total item count (rather
    // than computing an exact header-item offset for the RECENT section) is an approximation,
    // but the header/footer items above and below the call rows are a small fixed count, so
    // "near the end of the whole list" reliably means "near the end of the loaded call history".
    // loadMore() itself is idempotent (guarded on isLoadingMore/hasMoreCalls), so firing this a
    // few times during a fast fling is harmless.
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow null
            lastVisibleIndex to layoutInfo.totalItemsCount
        }.collect { visible ->
            val (lastVisibleIndex, totalItemsCount) = visible ?: return@collect
            if (lastVisibleIndex >= totalItemsCount - 5) {
                viewModel.loadMore()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeTopBar(ownerName = uiState.ownerName, onOpenNotifications = onOpenNotifications)
        },
        bottomBar = {
            CalloraBottomNavBar(
                selected = BottomNavDestination.HOME,
                onOpenHome = {},
                onOpenContacts = onOpenAllContacts,
                onOpenSpamCenter = onOpenSpamCenter,
                onOpenSettings = onOpenSettings,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenDialpad,
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(imageVector = Icons.Filled.Dialpad, contentDescription = stringResource(R.string.home_dialpad_content_description))
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item { SearchBar(onClick = onOpenSearch) }
            item { Spacer(modifier = Modifier.height(4.dp)) }
            item { StatsCard(onClick = onOpenSpamCenter) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                Text(
                    text = stringResource(R.string.home_recent_section_title),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                    color = MaterialTheme.extendedColors.onSurfaceFaint,
                )
            }
            if (!uiState.hasCallLogPermission) {
                item {
                    PermissionPromptCard(
                        message = stringResource(R.string.home_call_log_permission_message),
                        onGrantClick = { requestCallLogPermissions() },
                    )
                }
            } else if (!uiState.isLoading && uiState.recentCalls.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.home_no_recent_calls),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.onSurfaceFaint,
                    )
                }
            } else {
                items(uiState.recentCalls, key = { call -> call.id }) { call ->
                    // Memoized on phoneNumber (not recreated fresh every time this item content
                    // runs) so RecentCallRow's onClick/onCall params stay referentially stable
                    // across unrelated recompositions (e.g. isLoadingMore flipping during infinite
                    // scroll) — otherwise every visible row would fail Compose's skip check on
                    // these lambda params and fully recompose on every such state change, which is
                    // exactly what happens while scrolling near the end of the loaded list.
                    val onRowClick = remember(call.phoneNumber) { { onOpenContactDetail(call.phoneNumber) } }
                    val onRowCall = remember(call.phoneNumber) { { onCall(call.phoneNumber) } }
                    RecentCallRow(
                        call = call,
                        onClick = onRowClick,
                        onCall = onRowCall,
                    )
                }
                if (uiState.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (showCallLogPrompt) {
        PermissionPreviewBottomSheet(
            lottieAsset = "calllog_permission_preview.json",
            heading = stringResource(R.string.home_call_log_permission_heading),
            subtext = stringResource(R.string.home_call_log_permission_subtext),
            permissions = arrayOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_PHONE_STATE),
            onAllowAccess = {
                showCallLogPrompt = false
                coroutineScope.launch {
                    PermissionPromptRepository().markShown(context, PermissionPromptKind.CALL_LOG)
                }
                requestCallLogPermissions()
            },
            onNotNow = {
                showCallLogPrompt = false
                coroutineScope.launch {
                    PermissionPromptRepository().markShown(context, PermissionPromptKind.CALL_LOG)
                }
            },
        )
    }
}

@Composable
private fun HomeTopBar(ownerName: String?, onOpenNotifications: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val initial = ownerName?.firstOrNull { it.isLetter() }?.uppercaseChar()
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ownerName?.let { stringResource(R.string.home_greeting_with_name, it) }
                    ?: stringResource(R.string.home_greeting_default),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.home_protection_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extendedColors.onSurfaceFaint,
            )
        }

        IconButton(onClick = onOpenNotifications) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.home_notifications_content_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SearchBar(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.onSurfaceFaint,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.home_search_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.onSurfaceFaint,
        )
    }
}

@Composable
private fun StatsCard(onClick: () -> Unit) {
    // No real spam-detection stage exists yet — this used to show a fabricated
    // "18 spam calls blocked" number. Replaced with an honest "coming soon" state
    // rather than inventing a number; the card still routes to Spam Center.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.extendedColors.accent),
                ),
            )
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_spam_protection_label),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_coming_soon),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Icon(
            imageVector = Icons.Filled.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun RecentCallRow(call: RecentCallEntry, onClick: () -> Unit, onCall: () -> Unit) {
    val context = LocalContext.current
    SwipeActionRow(
        enabled = call.phoneNumber.isNotBlank(),
        onCall = onCall,
        onMessage = {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${call.phoneNumber}")))
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ListItemShape)
                .background(
                    if (call.isSpam) {
                        MaterialTheme.extendedColors.spam.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val displayName = call.name ?: call.formattedPhoneNumber

            // Open-Contact-Detail's clickable region is scoped to JUST the avatar/name/timestamp
            // (weight(1f), not the whole row) — the call icon below is a sibling OUTSIDE this
            // Row entirely, not nested inside it, so there's no overlap between "open contact
            // detail" and "place a call" at the same screen coordinates.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (call.photoUri != null) {
                    AsyncImage(
                        model = call.photoUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape),
                    )
                } else {
                    val initial = displayName.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#'
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initial.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (directionIcon, tint) = when (call.callType) {
                            CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived to MaterialTheme.extendedColors.onSurfaceFaint
                            CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade to MaterialTheme.extendedColors.onSurfaceFaint
                            CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed to MaterialTheme.extendedColors.spam
                            CallType.OTHER -> Icons.AutoMirrored.Filled.CallReceived to MaterialTheme.extendedColors.onSurfaceFaint
                        }
                        Icon(
                            imageVector = directionIcon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatRelativeCallTimestamp(call.timestamp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.extendedColors.onSurfaceFaint,
                        )
                    }
                }
            }

            if (call.isSpam) {
                Spacer(modifier = Modifier.width(8.dp))
                SpamBadge()
            }

            // Direct one-tap call, in addition to (not instead of) SwipeActionRow's swipe-to-call.
            // A sibling of (not nested inside) the clickable Row above — no background/filled
            // shape, matching the row's other small inline icons (e.g. the missed-call arrow).
            if (call.phoneNumber.isNotBlank()) {
                IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = stringResource(R.string.home_call_content_description, displayName),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** Filled danger-colored pill marking a call-log row whose number is on this device's local
 * spam list (see [HomeViewModel.annotateSpam]) — sits opposite the avatar, before the call
 * button, so it's visible without needing to open Contact Detail to learn the number was marked. */
@Composable
private fun SpamBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.extendedColors.spam)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.home_spam_badge),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

