package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors

/** The three bottom-nav destinations shared by [Home][HOME] and [Settings][SETTINGS]. HOME is
 * labelled "Recent" in the UI. Spam Center is no longer a tab — it's reached from Settings. */
enum class BottomNavDestination { HOME, CONTACTS, SETTINGS }

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
    onOpenSettings: () -> Unit,
) {
    val items = listOf(
        BottomNavItem(
            label = stringResource(R.string.nav_home),
            selectedIcon = Icons.Filled.History,
            unselectedIcon = Icons.Outlined.History,
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
            label = stringResource(R.string.nav_settings),
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            selected = selected == BottomNavDestination.SETTINGS,
            onClick = onOpenSettings,
        ),
    )

    // Floating pill: inset from the screen edges, rounded, shadowed. Active tab = filled icon in
    // a tinted squircle, primary ink, bold label; inactive = muted outlined icon, no squircle.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 10.dp, shape = PillShape)
                .background(MaterialTheme.colorScheme.surface, PillShape)
                .padding(vertical = 8.dp, horizontal = 6.dp),
        ) {
            items.forEach { item ->
                val tint = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.mutedText
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(SquircleShape)
                        .clickable(onClick = item.onClick)
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 32.dp)
                            .background(
                                color = if (item.selected) MaterialTheme.extendedColors.accentContainer else Color.Transparent,
                                shape = SquircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (item.selected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = if (item.selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = tint,
                    )
                }
            }
        }
    }
}

private val PillShape = RoundedCornerShape(28.dp)
private val SquircleShape = RoundedCornerShape(16.dp)
