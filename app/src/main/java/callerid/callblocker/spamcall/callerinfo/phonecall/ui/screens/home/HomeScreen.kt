package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptKind
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.BottomNavDestination
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CalloraBottomNavBar
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CallLogDayHeader
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CallLogDivider
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CallLogRow
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.HomeBannerAdSlot
import callerid.callblocker.spamcall.callerinfo.phonecall.ads.heightDp
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.PermissionPreviewBottomSheet
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.PermissionPromptCard
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.SwipeActionRow
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
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
    val listItems by viewModel.listItems.collectAsState()
    val bannerSlotHeightDp = viewModel.bannerAdController.state.collectAsState().value.heightDp
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
    // few times during a fast fling is harmless. The raw call count is read too: spam calls are
    // hidden from the list, so a page that turned out to be all spam adds no visible rows and the
    // layout alone would never re-trigger the check.
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow null
            Triple(lastVisibleIndex, layoutInfo.totalItemsCount, uiState.recentCalls.size)
        }.collect { visible ->
            val (lastVisibleIndex, totalItemsCount) = visible ?: return@collect
            if (lastVisibleIndex >= totalItemsCount - 5) {
                viewModel.loadMore()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Flat list: rows sit straight on the page (white / near-black), no cards.
        containerColor = MaterialTheme.colorScheme.surface,
        // HomeTopBar is deliberately NOT in Scaffold's topBar slot. Scaffold derives the content's
        // top padding from the topBar's measured height inside its own SubcomposeLayout pass, and
        // HomeTopBar's height includes the status-bar inset (statusBarsPadding) — on a cold start
        // that inset can still be 0 on the first measure. The header then re-measured itself taller
        // once the inset arrived, but the content's padding stayed at the stale height (80dp vs
        // 103dp here) until some unrelated recomposition, so the search bar and everything under it
        // dropped ~23dp a frame after first paint. As a plain Column child above the list, the
        // header's size change re-lays-out its siblings in the same pass, so the two can't diverge.
        // contentWindowInsets drops only the top side because HomeTopBar already handles the status
        // bar itself; left at the default, a topBar-less Scaffold would pad the top by it a second
        // time. Horizontal/bottom stay, so landscape nav-bar/cutout insets still reach the content.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
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
                // Scaffold anchors the FAB just above the bottom nav — exactly where the banner
                // sits — so lift it by the banner's height rather than drawing over the ad.
                modifier = Modifier.padding(bottom = bannerSlotHeightDp.dp).size(56.dp),
            ) {
                Icon(imageVector = Icons.Filled.Dialpad, contentDescription = stringResource(R.string.home_dialpad_content_description))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            HomeTopBar(onOpenNotifications = onOpenNotifications)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp),
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                item { SearchBar(onClick = onOpenSearch) }
                if (!uiState.hasCallLogPermission) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        PermissionPromptCard(
                            message = stringResource(R.string.home_call_log_permission_message),
                            onGrantClick = { requestCallLogPermissions() },
                        )
                    }
                } else if (uiState.isLoading && uiState.recentCalls.isEmpty()) {
                    // Genuine first load only: HomeViewModel keeps isLoading false whenever it has a
                    // last-known snapshot to show. Nothing at all (not even this) while the on-disk
                    // snapshot is still being read — that takes milliseconds and usually has data.
                    if (!uiState.isRestoringSnapshot) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else if (listItems.isEmpty()) {
                    // Also covers a call log holding only spam calls — those live in Spam Center.
                    item {
                        Text(
                            text = stringResource(R.string.home_no_recent_calls),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.mutedText,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                } else {
                    items(listItems, key = { item -> item.key }, contentType = { item -> item::class }) { item ->
                        when (item) {
                            is HomeListItem.DayHeader -> CallLogDayHeader(timestamp = item.timestamp)
                            is HomeListItem.CallGroup -> {
                                val call = item.latest
                                // Memoized on phoneNumber (not recreated fresh every time this item
                                // content runs) so the row's lambda params stay referentially stable
                                // across unrelated recompositions (e.g. isLoadingMore flipping during
                                // infinite scroll) — otherwise every visible row would fail Compose's
                                // skip check and fully recompose on every such state change.
                                val onRowClick = remember(call.phoneNumber) { { onOpenContactDetail(call.phoneNumber) } }
                                val onRowCall = remember(call.phoneNumber) { { onCall(call.phoneNumber) } }
                                Column {
                                    if (!item.isFirstInDay) CallLogDivider()
                                    SwipeActionRow(
                                        enabled = call.phoneNumber.isNotBlank(),
                                        onCall = onRowCall,
                                        onMessage = {
                                            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${call.phoneNumber}")))
                                        },
                                        shape = RectangleShape,
                                    ) {
                                        CallLogRow(
                                            call = call,
                                            count = item.calls.size,
                                            onClick = onRowClick,
                                            // Opaque, so the swipe's reveal stays hidden until dragged.
                                            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                                        )
                                    }
                                }
                            }
                        }
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
            // Below the list, above the bottom nav (Scaffold's innerPadding already excludes the
            // nav bar): the list's weight(1f) shrinks to make room, so nothing scrolls under it.
            HomeBannerAdSlot(controller = viewModel.bannerAdController)
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
private fun HomeTopBar(onOpenNotifications: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
