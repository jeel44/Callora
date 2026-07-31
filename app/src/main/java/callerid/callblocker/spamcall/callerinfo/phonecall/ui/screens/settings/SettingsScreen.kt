package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.BottomNavDestination
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CalloraBottomNavBar
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.CardShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ThemeState
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay

/**
 * Settings screen. The profile row shows the real device-owner name/number where available
 * (via [SettingsViewModel]) and opens the dedicated Profile screen (onOpenProfile) for the full
 * read-only view; the dark-theme toggle flips [ThemeState.isDarkMode], which
 * [callerid.callblocker.spamcall.callerinfo.phonecall.MainActivity] reads to re-theme the whole
 * app; Share app / Rate us launch real Android share/Play-Store intents.
 */
@Composable
fun SettingsScreen(
    onOpenBlockedList: () -> Unit,
    onOpenLanguageSelect: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenAllContacts: () -> Unit,
    onOpenSpamCenter: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val languageDisplayName by viewModel.languageDisplayName.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SettingsTopBar() },
        bottomBar = {
            CalloraBottomNavBar(
                selected = BottomNavDestination.SETTINGS,
                onOpenHome = onOpenHome,
                onOpenContacts = onOpenAllContacts,
                onOpenSpamCenter = onOpenSpamCenter,
                onOpenSettings = {},
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ProfileRow(
                    ownerName = uiState.ownerName,
                    ownerPhoneNumber = uiState.ownerPhoneNumber,
                    onClick = onOpenProfile,
                )
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item { SectionLabel(text = stringResource(R.string.settings_section_protection)) }
            item {
                SettingsRow(
                    icon = Icons.Filled.Call,
                    title = stringResource(R.string.settings_row_caller_id_spam),
                    onClick = onOpenSpamCenter,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Filled.Block,
                    title = stringResource(R.string.settings_row_blocked_numbers),
                    onClick = onOpenBlockedList,
                )
            }

            item { SectionLabel(text = stringResource(R.string.settings_section_preferences)) }
            item {
                SettingsRow(
                    icon = Icons.Filled.DarkMode,
                    title = stringResource(R.string.settings_row_dark_theme),
                    trailing = {
                        Switch(
                            checked = ThemeState.isDarkMode,
                            onCheckedChange = { ThemeState.isDarkMode = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Filled.Translate,
                    title = stringResource(R.string.settings_row_language),
                    subtitle = languageDisplayName,
                    onClick = onOpenLanguageSelect,
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_row_notifications),
                    onClick = onOpenNotifications,
                )
            }

            item { SectionLabel(text = stringResource(R.string.settings_section_support)) }
            item {
                SettingsRow(
                    icon = Icons.Filled.PrivacyTip,
                    title = stringResource(R.string.settings_row_privacy_policy),
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://sites.google.com/view/caller-id-spam-detection/home"),
                                ),
                            )
                        } catch (e: ActivityNotFoundException) {
                            // No browser available — fail silently.
                        }
                    },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Help,
                    title = stringResource(R.string.settings_row_help_feedback),
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:tarangsutariya3440@gmail.com")).apply {
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_feedback_email_subject))
                                },
                            )
                        } catch (e: ActivityNotFoundException) {
                            // No email client available — fail silently.
                        }
                    },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Filled.Share,
                    title = stringResource(R.string.settings_row_share_app),
                    onClick = {
                        val playStoreUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.settings_share_text, playStoreUrl))
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, context.getString(R.string.settings_share_chooser_title)),
                        )
                    },
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Filled.Star,
                    title = stringResource(R.string.settings_row_rate_us),
                    onClick = {
                        val packageName = context.packageName
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                                    setPackage("com.android.vending")
                                },
                            )
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                                ),
                            )
                        }
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SettingsTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ProfileRow(ownerName: String?, ownerPhoneNumber: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (ownerName?.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '?').toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ownerName ?: stringResource(R.string.settings_profile_fallback_name),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            // No fake number when the device won't give us a real one — see
            // SettingsViewModel.getDevicePhoneNumber for why it's often unavailable.
            if (ownerPhoneNumber != null) {
                Text(
                    text = formatPhoneNumberForDisplay(ownerPhoneNumber),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.extendedColors.onSurfaceFaint,
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.onSurfaceFaint,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.extendedColors.onSurfaceFaint,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.onSurfaceFaint,
        )
    },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemShape)
            .background(MaterialTheme.colorScheme.surface)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.extendedColors.onSurfaceFaint,
                )
            }
        }
        trailing()
    }
}
