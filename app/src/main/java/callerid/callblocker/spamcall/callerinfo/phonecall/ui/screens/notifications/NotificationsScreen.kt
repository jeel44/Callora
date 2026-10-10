package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors

private data class NotificationEntry(
    val icon: ImageVector,
    val iconColor: @Composable () -> Color,
    val title: String,
    val timestamp: String,
)

/** UI-only notifications screen — rows below are static hardcoded sample data. */
@Composable
fun NotificationsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val notifications = listOf(
        NotificationEntry(
            icon = Icons.Filled.Report,
            iconColor = { MaterialTheme.extendedColors.spam },
            title = stringResource(R.string.notifications_sample_spam_blocked),
            timestamp = stringResource(R.string.notifications_sample_time_2h_ago),
        ),
        NotificationEntry(
            icon = Icons.Filled.Shield,
            iconColor = { MaterialTheme.extendedColors.success },
            title = stringResource(R.string.notifications_sample_weekly_summary),
            timestamp = stringResource(R.string.notifications_sample_time_1d_ago),
        ),
        NotificationEntry(
            icon = Icons.Filled.Update,
            iconColor = { MaterialTheme.colorScheme.primary },
            title = stringResource(R.string.notifications_sample_db_updated),
            timestamp = stringResource(R.string.notifications_sample_time_2d_ago),
        ),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Header lives in the content Column below, not the topBar slot: see HomeScreen's Scaffold for
        // why (topBar-height-derived content padding goes stale on a cold-start inset change).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            NotificationsTopBar(onBack = onBack)
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(notifications) { entry -> NotificationRow(entry = entry) }
                }
            }
        }
    }
}

@Composable
private fun NotificationsTopBar(onBack: () -> Unit) {
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
private fun NotificationRow(entry: NotificationEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = null,
            tint = entry.iconColor(),
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = entry.timestamp,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.extendedColors.onSurfaceFaint,
            )
        }
    }
}
