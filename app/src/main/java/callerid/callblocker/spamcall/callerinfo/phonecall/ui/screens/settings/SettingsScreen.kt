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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.data.repository.ThemePreferencesRepository
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.BottomNavDestination
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.components.CalloraBottomNavBar
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ThemeState
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.calloraSwitchColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors

/**
 * Settings screen. The dark-theme toggle flips [ThemeState.isDarkMode], which
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
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val languageDisplayName by viewModel.languageDisplayName.collectAsState()
    val spamMarkedCount by viewModel.spamMarkedCount.collectAsState()
    val afterCallScreenEnabled by viewModel.afterCallScreenEnabled.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Header lives in the content Column below, not the topBar slot: see HomeScreen's Scaffold for
        // why (topBar-height-derived content padding goes stale on a cold-start inset change).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            CalloraBottomNavBar(
                selected = BottomNavDestination.SETTINGS,
                onOpenHome = onOpenHome,
                onOpenContacts = onOpenAllContacts,
                onOpenSettings = {},
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar()
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { SectionLabel(text = stringResource(R.string.settings_section_protection)) }
                    item {
                        SettingsGroup {
                            SettingsRow(
                                icon = Icons.Filled.RemoveModerator,
                                iconTint = MaterialTheme.extendedColors.spamTag,
                                iconContainer = MaterialTheme.extendedColors.spamTagContainer,
                                title = stringResource(R.string.spam_center_title),
                                onClick = onOpenSpamCenter,
                                trailing = {
                                    if (spamMarkedCount > 0) {
                                        CountBadge(count = spamMarkedCount)
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    ChevronIcon()
                                },
                            )
                            SettingsDivider()
                            SettingsRow(
                                icon = Icons.Filled.Block,
                                title = stringResource(R.string.settings_row_blocked_numbers),
                                onClick = onOpenBlockedList,
                            )
                        }
                    }

                    item { SectionLabel(text = stringResource(R.string.settings_section_preferences)) }
                    item {
                        SettingsGroup {
                            SettingsRow(
                                icon = Icons.Filled.DarkMode,
                                title = stringResource(R.string.settings_row_dark_theme),
                                trailing = {
                                    Switch(
                                        checked = ThemeState.isDarkMode,
                                        onCheckedChange = {
                                            ThemeState.isDarkMode = it
                                            ThemePreferencesRepository.setDarkMode(context, it)
                                        },
                                        colors = calloraSwitchColors(),
                                    )
                                },
                            )
                            SettingsDivider()
                            SettingsRow(
                                icon = Icons.Outlined.Smartphone,
                                title = stringResource(R.string.settings_row_after_call),
                                subtitle = stringResource(R.string.settings_row_after_call_subtitle),
                                trailing = {
                                    Switch(
                                        checked = afterCallScreenEnabled,
                                        onCheckedChange = viewModel::setAfterCallScreenEnabled,
                                        colors = calloraSwitchColors(),
                                    )
                                },
                            )
                            SettingsDivider()
                            SettingsRow(
                                icon = Icons.Filled.Translate,
                                title = stringResource(R.string.settings_row_language),
                                subtitle = languageDisplayName,
                                onClick = onOpenLanguageSelect,
                            )
                            SettingsDivider()
                            SettingsRow(
                                icon = Icons.Filled.Notifications,
                                title = stringResource(R.string.settings_row_notifications),
                                onClick = onOpenNotifications,
                            )
                        }
                    }

                    item { SectionLabel(text = stringResource(R.string.settings_section_support)) }
                    item {
                        SettingsGroup {
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
                            SettingsDivider()
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
                            SettingsDivider()
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
                            SettingsDivider()
                            SettingsRow(
                                icon = Icons.Filled.Star,
                                iconTint = MaterialTheme.extendedColors.rating,
                                iconContainer = MaterialTheme.extendedColors.ratingContainer,
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
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
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
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        ),
        color = MaterialTheme.extendedColors.mutedText,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

/** One rounded card per section; rows inside are separated by [SettingsDivider]. */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.extendedColors.listDivider),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconContainer: Color = MaterialTheme.extendedColors.accentContainer,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = { ChevronIcon() },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(iconContainer, IconTileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.extendedColors.mutedText,
                )
            }
        }
        trailing()
    }
}

private val IconTileShape = RoundedCornerShape(12.dp)

@Composable
private fun ChevronIcon() {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.extendedColors.mutedText,
        modifier = Modifier.size(16.dp),
    )
}

/** Pill-shaped count, in the same spam-tag color pair as the Spam Center row's icon tile. */
@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier = Modifier
            .heightIn(min = 22.dp)
            .widthIn(min = 22.dp)
            .background(MaterialTheme.extendedColors.spamTagContainer, CircleShape)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.extendedColors.spamTag,
        )
    }
}
