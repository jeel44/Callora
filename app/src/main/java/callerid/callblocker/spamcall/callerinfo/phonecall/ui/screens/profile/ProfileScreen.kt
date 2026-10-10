package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay

/**
 * Read-only view of the device's own "me" contact card (ContactsContract.Profile via
 * [ProfileViewModel]) — not any app-stored data, and not editable here. Many devices never have
 * this filled in, so an empty profile is a normal, expected state rather than an error.
 */
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Header lives in the content Column below, not the topBar slot: see HomeScreen's Scaffold for
        // why (topBar-height-derived content padding goes stale on a cold-start inset change).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            ProfileTopBar(onBack = onBack)
            Box(modifier = Modifier.weight(1f)) {
                if (!uiState.isLoaded) return@Scaffold

                val hasProfile = uiState.displayName != null || uiState.phoneNumber != null

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(48.dp))

                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasProfile) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (hasProfile) {
                            Text(
                                text = (uiState.displayName?.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '?').toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = MaterialTheme.extendedColors.onSurfaceFaint,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (hasProfile) {
                        Text(
                            text = uiState.displayName ?: stringResource(R.string.profile_unnamed),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        val phoneNumber = uiState.phoneNumber
                        if (phoneNumber != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = formatPhoneNumberForDisplay(phoneNumber),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                color = MaterialTheme.extendedColors.onSurfaceSoft,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.profile_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.profile_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                            color = MaterialTheme.extendedColors.onSurfaceFaint,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTopBar(onBack: () -> Unit) {
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
            text = stringResource(R.string.profile_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
