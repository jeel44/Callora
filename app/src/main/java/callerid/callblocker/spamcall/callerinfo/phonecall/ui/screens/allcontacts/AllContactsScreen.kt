package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.allcontacts

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ContactsChangeSignal
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptKind
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.PermissionPreviewBottomSheet
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.PermissionPromptCard
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.SwipeActionRow
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.startExternalActivity
import coil.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Full device contact list, not capped like Home's recent-calls list. Backed by
 * [AllContactsViewModel] via Jetpack Paging 3 — [AllContactsViewModel.pagedContacts] is only ever
 * a handful of provider rows deep in memory at a time, however many contacts the device has. Owns
 * both search (now a provider-level, debounced, paginated query — see
 * [AllContactsViewModel.pagedContacts]) and an A-Z fast-scroll index — the standalone Search
 * screen/tab was folded into this one.
 */
@Composable
fun AllContactsScreen(
    onBack: () -> Unit,
    onOpenContactDetail: (phoneNumber: String) -> Unit,
    onCall: (phoneNumber: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AllContactsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val lazyPagingItems = viewModel.pagedContacts.collectAsLazyPagingItems()
    var queryField by remember { mutableStateOf(TextFieldValue(uiState.query)) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Mirrors HomeViewModel.onScreenResumed's non-destructive resume (see its kdoc): a full,
    // spinner-visible Pager reload is only justified when there's nothing valid on screen yet or
    // permission was just granted, not on every single resume. Contacts is reached constantly via
    // back-navigation (Contact Detail/Block Confirm return here) and this ViewModel/Pager survive
    // all of that, so unconditionally calling refresh() here was invalidating an already-loaded,
    // still-cached Pager on every visit and re-showing the spinner for no reason.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hadPermission = viewModel.uiState.value.hasContactsPermission
                viewModel.checkPermission()
                val hasPermission = viewModel.uiState.value.hasContactsPermission
                // ContactsChangeSignal: set by Contact Detail on a successful delete (build-2 QA
                // #11) - a real reason to refresh, same as the two existing ones below, without
                // going back to refreshing on every trivial resume.
                val changedElsewhere = ContactsChangeSignal.pendingRefresh
                if (lazyPagingItems.itemCount == 0 || (hasPermission && !hadPermission) || changedElsewhere) {
                    ContactsChangeSignal.pendingRefresh = false
                    lazyPagingItems.refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.checkPermission()
        lazyPagingItems.refresh()
    }

    // Just-in-time contacts permission popup — shown once per install on first visit to
    // Contacts, regardless of current grant state (RoleManager's dialer-role prompt can
    // silently auto-grant this on some devices; the popup should still get its one-time
    // appearance so the user sees it, just with "Got it" instead of "Allow access" — see
    // PermissionPreviewBottomSheet). PermissionPromptRepository's flag is what actually prevents
    // it reappearing on later visits.
    var showContactsPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!PermissionPromptRepository().hasBeenShown(context, PermissionPromptKind.CONTACTS)) {
            showContactsPrompt = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Header lives in the content Column below, not the topBar slot: see HomeScreen's Scaffold for
        // why (topBar-height-derived content padding goes stale on a cold-start inset change).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            AllContactsTopBar(onBack = onBack)
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (uiState.hasContactsPermission) {
                        AllContactsSearchField(
                            query = queryField,
                            onQueryChange = { newValue ->
                                queryField = newValue
                                viewModel.onQueryChange(newValue.text)
                            },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }

                    val refreshState = lazyPagingItems.loadState.refresh
                    when {
                        !uiState.hasContactsPermission -> {
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                Spacer(modifier = Modifier.height(16.dp))
                                PermissionPromptCard(
                                    message = stringResource(R.string.contacts_permission_message),
                                    onGrantClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                                )
                            }
                        }
                        // First page of a (re)load still in flight — mirrors the old isLoading spinner.
                        // Note this also fires on every search-query change, since a new query means a
                        // brand new PagingSource/Pager (filtering now happens at the query level, not as
                        // an in-memory transform on top of an already-loaded list — see
                        // AllContactsViewModel.pagedContacts) — so unlike the pre-Paging in-memory filter,
                        // changing the search text can briefly show this spinner instead of keeping the
                        // previous results on screen. Accepted tradeoff: keeping the old list visible
                        // during a query change would need materializing/holding a second full result
                        // set, which defeats the point of paginating in the first place.
                        refreshState is LoadState.Loading && lazyPagingItems.itemCount == 0 -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        refreshState is LoadState.Error && lazyPagingItems.itemCount == 0 -> {
                            AllContactsEmptyState(message = stringResource(R.string.contacts_error_load_failed))
                        }
                        lazyPagingItems.itemCount == 0 -> {
                            AllContactsEmptyState(
                                message = if (uiState.isSearching) {
                                    stringResource(R.string.contacts_no_matches, uiState.query)
                                } else {
                                    stringResource(R.string.contacts_none_found)
                                },
                            )
                        }
                        else -> {
                            AllContactsList(
                                lazyPagingItems = lazyPagingItems,
                                letterIndices = uiState.letterIndices,
                                isSearching = uiState.isSearching,
                                onOpenContactDetail = onOpenContactDetail,
                                onCall = onCall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showContactsPrompt) {
        PermissionPreviewBottomSheet(
            lottieAsset = "contacts_permission_preview.json",
            heading = stringResource(R.string.contacts_permission_heading),
            subtext = stringResource(R.string.contacts_permission_subtext),
            permissions = arrayOf(Manifest.permission.READ_CONTACTS),
            onAllowAccess = {
                showContactsPrompt = false
                coroutineScope.launch {
                    PermissionPromptRepository().markShown(context, PermissionPromptKind.CONTACTS)
                }
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onNotNow = {
                showContactsPrompt = false
                coroutineScope.launch {
                    PermissionPromptRepository().markShown(context, PermissionPromptKind.CONTACTS)
                }
            },
        )
    }
}

private fun contactSectionLetter(contact: Contact): String =
    contact.name.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"

@Composable
private fun AllContactsList(
    lazyPagingItems: LazyPagingItems<Contact>,
    letterIndices: Map<String, Int>,
    isSearching: Boolean,
    onOpenContactDetail: (phoneNumber: String) -> Unit,
    onCall: (phoneNumber: String) -> Unit,
) {
    // lazyPagingItems streams pages straight from ContactsPagingSource — no full in-memory list
    // and no client-side scan/group here, regardless of how many contacts exist on the device.
    // Section headers are rendered inline (comparing each row against its predecessor via
    // peek(), which doesn't count as a load-triggering access) rather than as separate flattened
    // list entries, since Paging has no concept of "insert a synthetic header row" — letterIndices
    // (from ContactsRepository.getAlphabetIndex, same table/filter/sort as the unfiltered page
    // query) already lines up with these items' indices, so the A-Z bar needs no extra header-
    // offset bookkeeping the way the pre-Paging flattened-entry-list design did.
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 20.dp, end = if (isSearching) 20.dp else 6.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                count = lazyPagingItems.itemCount,
                key = lazyPagingItems.itemKey { "row_${it.phoneNumber}_${it.name}" },
            ) { index ->
                val contact = lazyPagingItems[index] ?: return@items
                Column {
                    if (!isSearching) {
                        val letter = contactSectionLetter(contact)
                        val previousLetter = if (index == 0) {
                            null
                        } else {
                            lazyPagingItems.peek(index - 1)?.let(::contactSectionLetter)
                        }
                        if (letter != previousLetter) {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                                color = MaterialTheme.extendedColors.onSurfaceFaint,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                    }
                    AllContactRow(
                        contact = contact,
                        onClick = { onOpenContactDetail(contact.phoneNumber) },
                        onCall = { onCall(contact.phoneNumber) },
                    )
                }
            }
            if (lazyPagingItems.loadState.append is LoadState.Loading) {
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

        if (!isSearching) {
            AlphabetIndexPill(
                availableLetters = letterIndices.keys,
                onLetterSelected = { letter ->
                    val targetIndex = letterIndices[letter]
                        ?: letterIndices.keys.filter { it >= letter }.minOrNull()?.let { letterIndices[it] }
                        ?: letterIndices.keys.maxOrNull()?.let { letterIndices[it] }
                    if (targetIndex != null) {
                        // scrollToItem (instant snap), not animateScrollToItem: the latter drives
                        // a real frame-by-frame spring scroll through every intervening row to
                        // get there (teleporting only in 100-item hops after already scrolling
                        // ~5000dp — see androidx.compose.foundation.lazy.layout
                        // .LazyLayoutScrollScope.animateScrollToItem), which on this Paging-backed
                        // list means composing/loading real intermediate pages inline with the
                        // animation for a far jump (e.g. A -> Z) — a visibly laggy response to a
                        // tap. scrollToItem does one direct remeasure instead, matching the
                        // instant iOS Contacts-style snap this pill is meant to feel like.
                        coroutineScope.launch { listState.scrollToItem(targetIndex) }
                    }
                },
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

private val AzLetters = ('A'..'Z').map { it.toString() }
private val AzLetterGroups = AzLetters.chunked(4)

private val AzTrackWidth = 18.dp
private val AzTouchWidth = 44.dp
private val AzGroupGap = 6.dp
private val AzBubbleSize = 52.dp
private val AzBubbleGap = 10.dp

/**
 * Section-grouped fast-scroll pill: letters cluster into groups of 4 with a small gap between
 * clusters for visual rhythm. Touch-down and every subsequent move resolve the finger's Y
 * position to the nearest letter and report it live (iOS Contacts-style scrubbing), not just on
 * release. Cell height is derived analytically from the measured track height rather than read
 * back from layout, so the hit-test always matches what's drawn even though the touch-capture
 * region is wider than the visual pill.
 */
@Composable
private fun AlphabetIndexPill(
    availableLetters: Set<String>,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var heightPx by remember { mutableStateOf(0f) }
    var activeLetter by remember { mutableStateOf<String?>(null) }
    var activeCenterPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val gapPx = with(density) { AzGroupGap.toPx() }
    val numGaps = AzLetterGroups.size - 1

    fun letterForY(y: Float): Pair<String, Float>? {
        if (heightPx <= 0f) return null
        val cellHeight = (heightPx - numGaps * gapPx) / AzLetters.size
        var cursor = 0f
        var bestLetter = AzLetters.first()
        var bestCenter = cellHeight / 2f
        var bestDist = Float.MAX_VALUE
        AzLetterGroups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) cursor += gapPx
            group.forEach { letter ->
                val center = cursor + cellHeight / 2f
                val dist = abs(y - center)
                if (dist < bestDist) {
                    bestDist = dist
                    bestLetter = letter
                    bestCenter = center
                }
                cursor += cellHeight
            }
        }
        return bestLetter to bestCenter
    }

    val pillShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(AzTouchWidth)
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                // detectDragGestures only fires once the touch exceeds slop, which misses a
                // plain tap with no movement — awaitEachGesture reacts on the initial press
                // itself, then keeps tracking while the finger stays down, covering both a tap
                // and a live scrub down the rail.
                awaitEachGesture {
                    val down = awaitFirstDown()
                    letterForY(down.position.y)?.let { (letter, center) ->
                        activeLetter = letter
                        activeCenterPx = center
                        onLetterSelected(letter)
                    }
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        // Only re-resolve while the finger is still down — the terminating
                        // (release) event still reaches this body once before the while-condition
                        // below ends the loop, so without this guard a plain tap fires
                        // onLetterSelected a second time at the same position, launching a
                        // second animateScrollToItem that interrupts/restarts the first
                        // mid-animation (the visible double-motion).
                        if (change.pressed) {
                            letterForY(change.position.y)?.let { (letter, center) ->
                                activeLetter = letter
                                activeCenterPx = center
                                onLetterSelected(letter)
                            }
                        }
                        change.consume()
                    } while (event.changes.any { it.pressed })
                    activeLetter = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(AzTrackWidth)
                .clip(pillShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.extendedColors.onSurfaceFaint.copy(alpha = 0.18f), pillShape),
        ) {
            AzLetterGroups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) Spacer(modifier = Modifier.height(AzGroupGap))
                group.forEach { letter ->
                    val isActive = letter == activeLetter
                    val isAvailable = availableLetters.contains(letter)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = 18.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.extendedColors.accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = letter,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = Color.White,
                                )
                            }
                        } else {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.extendedColors.onSurfaceFaint.copy(
                                    alpha = if (isAvailable) 1f else 0.4f,
                                ),
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = activeLetter != null,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        x = -(AzBubbleSize.toPx() + AzBubbleGap.toPx()).roundToInt(),
                        y = (activeCenterPx - AzBubbleSize.toPx() / 2f).roundToInt(),
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .size(AzBubbleSize)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.extendedColors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = activeLetter.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun AllContactsSearchField(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(ListItemShape),
        placeholder = {
            Text(
                text = stringResource(R.string.home_search_hint),
                color = MaterialTheme.extendedColors.onSurfaceFaint,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.onSurfaceFaint,
            )
        },
        singleLine = true,
        shape = ListItemShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun AllContactsEmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PersonSearch,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.onSurfaceFaint,
            modifier = Modifier.size(40.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.onSurfaceFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

@Composable
private fun AllContactsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.dialpad_back_content_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.contacts_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AllContactRow(contact: Contact, onClick: () -> Unit, onCall: () -> Unit) {
    val context = LocalContext.current
    SwipeActionRow(
        enabled = contact.phoneNumber.isNotBlank(),
        onCall = onCall,
        onMessage = {
            context.startExternalActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.phoneNumber}")))
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ListItemShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (contact.photoUri != null) {
                AsyncImage(
                    model = contact.photoUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape),
                )
            } else {
                val initial = contact.name.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#'
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
                    text = contact.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = contact.formattedPhoneNumber,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.extendedColors.onSurfaceFaint,
                )
            }
        }
    }
}
