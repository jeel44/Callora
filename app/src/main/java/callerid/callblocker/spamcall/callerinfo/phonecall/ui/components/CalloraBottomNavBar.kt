package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors

/** The four bottom-nav destinations shared by [Home][HOME] and [Settings][SETTINGS]. */
enum class BottomNavDestination { HOME, CONTACTS, SPAM, SETTINGS }

private data class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/** Bottom navigation bar shared across main-tab screens (Home, Settings, ...). */
@Composable
fun CalloraBottomNavBar(
    selected: BottomNavDestination,
    onOpenHome: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenSpamCenter: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val items = listOf(
        BottomNavItem(
            label = stringResource(R.string.nav_home),
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            selected = selected == BottomNavDestination.HOME,
            onClick = onOpenHome,
        ),
        BottomNavItem(
            label = stringResource(R.string.nav_contacts),
            selectedIcon = Icons.Filled.Contacts,
            unselectedIcon = Icons.Outlined.Contacts,
            selected = selected == BottomNavDestination.CONTACTS,
            onClick = onOpenContacts,
        ),
        BottomNavItem(
            label = stringResource(R.string.nav_spam),
            selectedIcon = Icons.Filled.Shield,
            unselectedIcon = Icons.Outlined.Shield,
            selected = selected == BottomNavDestination.SPAM,
            onClick = onOpenSpamCenter,
        ),
        BottomNavItem(
            label = stringResource(R.string.nav_settings),
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            selected = selected == BottomNavDestination.SETTINGS,
            onClick = onOpenSettings,
        ),
    )

    // Flat style: a hairline on top instead of elevation; active tab = primary blue, filled icon,
    // bold label; inactive = muted gray, outlined icon, regular label.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.extendedColors.listDivider),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val tint = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.mutedText
                Column(
                    modifier = Modifier
                        .clickable(onClick = item.onClick)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = if (item.selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Normal,
                        color = tint,
                    )
                }
            }
        }
    }
}
