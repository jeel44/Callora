package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.spamcenter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.RecentCallEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CallLogDayHeader
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CallLogDivider
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CallLogRow
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CardShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * spam_center screen — stats card and the 3 blocking-rule toggles are still local UI-only
 * state (no real spam-detection data exists to drive them yet). "Manage blocked list"'s
 * subtitle count is real, from BlockedNumbersRepository via [SpamCenterViewModel].
 */
@Composable
fun SpamCenterScreen(
    onClose: () -> Unit,
    onOpenBlockedList: () -> Unit,
    onOpenContactDetail: (phoneNumber: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpamCenterViewModel = viewModel(),
) {
    val blockedCount by viewModel.blockedCount.collectAsState()
    val spamCalls by viewModel.spamCalls.collectAsState()

    // The blocked count can change on the blocked_list screen (unblocking) or block_confirm
    // (blocking) — both reached from here — so re-check whenever this screen resumes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Header lives in the content Column below, not the topBar slot: see HomeScreen's Scaffold for
        // why (topBar-height-derived content padding goes stale on a cold-start inset change).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            SpamCenterTopBar(onClose = onClose)
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { StatsCard() }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item {
                        Text(
                            text = stringResource(R.string.spam_center_blocking_rules_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.extendedColors.onSurfaceFaint,
                        )
                    }
                    item {
                        var checked by remember { mutableStateOf(true) }
                        RuleRow(
                            icon = Icons.Filled.Shield,
                            title = stringResource(R.string.spam_center_rule_auto_block_title),
                            subtitle = stringResource(R.string.spam_center_rule_auto_block_subtitle),
                            checked = checked,
                            onCheckedChange = { checked = it },
                        )
                    }
                    item {
                        var checked by remember { mutableStateOf(true) }
                        RuleRow(
                            icon = Icons.Filled.Storefront,
                            title = stringResource(R.string.spam_center_rule_telemarketers_title),
                            subtitle = stringResource(R.string.spam_center_rule_telemarketers_subtitle),
                            checked = checked,
                            onCheckedChange = { checked = it },
                        )
                    }
                    item {
                        var checked by remember { mutableStateOf(false) }
                        RuleRow(
                            icon = Icons.Filled.Public,
                            title = stringResource(R.string.spam_center_rule_international_title),
                            subtitle = stringResource(R.string.spam_center_rule_international_subtitle),
                            checked = checked,
                            onCheckedChange = { checked = it },
                        )
                    }
                    item {
                        ManageBlockedListRow(blockedCount = blockedCount, onClick = onOpenBlockedList)
                    }
                    // Calls from spam-marked numbers. Home no longer shows these at all, so this
                    // is the one place they're listed. Hidden until the first load finishes.
                    spamCalls?.let { calls ->
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                        item {
                            Text(
                                text = stringResource(R.string.spam_center_spam_calls_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.extendedColors.onSurfaceFaint,
                            )
                        }
                        item { SpamCallsCard(calls = calls, onOpenContactDetail = onOpenContactDetail) }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SpamCenterTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.spam_center_title),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.spam_center_close_content_description),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StatsCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatBlock(
            value = "18",
            label = stringResource(R.string.spam_center_stat_blocked_week),
            valueColor = MaterialTheme.extendedColors.spam,
        )
        StatBlock(
            value = "142",
            label = stringResource(R.string.spam_center_stat_blocked_alltime),
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatBlock(value: String, label: String, valueColor: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
            color = valueColor,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.extendedColors.onSurfaceFaint,
        )
    }
}

@Composable
private fun RuleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.extendedColors.onSurfaceFaint,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun ManageBlockedListRow(blockedCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.spam_center_manage_blocked_list_title),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = LocalContext.current.resources.getQuantityString(
                    R.plurals.spam_center_manage_blocked_list_subtitle,
                    blockedCount,
                    blockedCount,
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.extendedColors.onSurfaceFaint,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.onSurfaceFaint,
        )
    }
}

/** Spam calls as the same flat rows Home uses (one card, day headers, dividers); tapping one
 * opens Contact Detail, where the number can be unmarked. */
@Composable
private fun SpamCallsCard(calls: List<RecentCallEntry>, onOpenContactDetail: (phoneNumber: String) -> Unit) {
    val zone = ZoneId.systemDefault()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        if (calls.isEmpty()) {
            Text(
                text = stringResource(R.string.spam_center_no_spam_calls),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.mutedText,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@Column
        }
        var previousDay: LocalDate? = null
        calls.forEach { call ->
            val day = Instant.ofEpochMilli(call.timestamp).atZone(zone).toLocalDate()
            if (day != previousDay) {
                CallLogDayHeader(timestamp = call.timestamp)
            } else {
                CallLogDivider()
            }
            previousDay = day
            CallLogRow(call = call, count = 1, onClick = { onOpenContactDetail(call.phoneNumber) })
        }
    }
}
