package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.notificationsettings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptKind
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.PermissionPromptRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.PermissionPreviewBottomSheet
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import kotlinx.coroutines.launch

/**
 * Settings' Notifications sub-page — three visual/state-only toggles, backed by
 * [NotificationSettingsViewModel]/DataStore so the on/off state survives app restarts. Not wired
 * to any real notification-sending logic yet (none exists for these categories currently); this
 * only makes the row lead somewhere real instead of nowhere.
 */
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val notificationsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {}

    // Just-in-time notifications permission popup — the natural first stop for it, since this is
    // where "Notifications" in Settings leads. Only relevant on API 33+ (POST_NOTIFICATIONS
    // didn't exist before, so there's nothing to show pre-33). Shown once per install regardless
    // of current grant state — PermissionPreviewBottomSheet re-checks the live permission itself
    // to decide "Got it" vs "Allow access". PermissionPromptRepository's flag is what actually
    // prevents it reappearing on later visits.
    var showNotificationsPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionPromptRepository().hasBeenShown(context, PermissionPromptKind.NOTIFICATIONS)
        ) {
            showNotificationsPrompt = true
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { NotificationSettingsTopBar(onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ToggleRow(
                    icon = Icons.Filled.Call,
                    title = stringResource(R.string.notification_settings_missed_calls),
                    checked = uiState.missedCallsEnabled,
                    onCheckedChange = viewModel::setMissedCallsEnabled,
                )
            }
            item {
                ToggleRow(
                    icon = Icons.Filled.Report,
                    title = stringResource(R.string.notification_settings_spam_alerts),
                    checked = uiState.spamAlertsEnabled,
                    onCheckedChange = viewModel::setSpamAlertsEnabled,
                )
            }
            item {
                ToggleRow(
                    icon = Icons.Filled.Update,
                    title = stringResource(R.string.notification_settings_app_updates),
                    checked = uiState.appUpdatesEnabled,
                    onCheckedChange = viewModel::setAppUpdatesEnabled,
                )
            }
        }
    }

    if (showNotificationsPrompt) {
        PermissionPreviewBottomSheet(
            lottieAsset = "notification_permission_preview.json",
            heading = stringResource(R.string.notification_settings_permission_heading),
            subtext = stringResource(R.string.notification_settings_permission_subtext),
            permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            onAllowAccess = {
                showNotificationsPrompt = false
                coroutineScope.launch {
                    PermissionPromptRepository().markShown(context, PermissionPromptKind.NOTIFICATIONS)
                }
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onNotNow = {
                showNotificationsPrompt = false
                coroutineScope.launch {
                    PermissionPromptRepository().markShown(context, PermissionPromptKind.NOTIFICATIONS)
                }
            },
        )
    }
}

@Composable
private fun NotificationSettingsTopBar(onBack: () -> Unit) {
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
            text = stringResource(R.string.notifications_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
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
