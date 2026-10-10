package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.dialpad

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatDialpadInput
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.normalizeDialedNumber

private data class DialpadKeyData(val digit: String, val letters: String)

private val DialpadKeys = listOf(
    DialpadKeyData("1", ""),
    DialpadKeyData("2", "ABC"),
    DialpadKeyData("3", "DEF"),
    DialpadKeyData("4", "GHI"),
    DialpadKeyData("5", "JKL"),
    DialpadKeyData("6", "MNO"),
    DialpadKeyData("7", "PQRS"),
    DialpadKeyData("8", "TUV"),
    DialpadKeyData("9", "WXYZ"),
    DialpadKeyData("*", ""),
    DialpadKeyData("0", ""),
    DialpadKeyData("#", ""),
)

/**
 * Manual numeric dialpad — reached from Home's FAB. Shows a live T9-style match list (via
 * [DialpadViewModel]) as digits are typed; tapping a match just fills the field with that
 * contact's number rather than calling directly, so the call button still hands the (now
 * possibly contact-sourced) typed digits to [onCall].
 */
@Composable
fun DialpadScreen(
    onBack: () -> Unit,
    onCall: (phoneNumber: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DialpadViewModel = viewModel(),
) {
    var typedNumber by remember { mutableStateOf("") }
    val matchingContacts by viewModel.matchingContacts.collectAsState()

    LaunchedEffect(typedNumber) {
        viewModel.onTypedDigitsChanged(typedNumber)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // Header lives in the content Column below, not the topBar slot: see HomeScreen's Scaffold for
        // why (topBar-height-derived content padding goes stale on a cold-start inset change).
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.dialpad_back_content_description),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = formatDialpadInput(typedNumber),
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }

                    if (typedNumber.isNotEmpty() && matchingContacts.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(matchingContacts, key = { it.phoneNumber }) { contact ->
                                MatchingContactRow(
                                    contact = contact,
                                    onClick = { typedNumber = contact.phoneNumber },
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DialpadKeys.chunked(3).forEach { rowKeys ->
                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                rowKeys.forEach { key ->
                                    DialpadKey(
                                        digit = key.digit,
                                        letters = key.letters,
                                        onClick = { typedNumber += key.digit },
                                        // Long-press 0 types "+", as in Google's dialer — the only
                                        // way to enter an international number by hand.
                                        onLongClick = if (key.digit == "0") {
                                            { typedNumber += "+" }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (typedNumber.isNotEmpty()) {
                                BackspaceButton(
                                    onClick = { typedNumber = typedNumber.dropLast(1) },
                                    onClear = { typedNumber = "" },
                                )
                            }
                        }

                        CallButton(
                            enabled = typedNumber.isNotEmpty(),
                            // Dials what's displayed: +91-normalized for a complete Indian number,
                            // otherwise exactly what was typed (see normalizeDialedNumber).
                            onClick = { if (typedNumber.isNotEmpty()) onCall(normalizeDialedNumber(typedNumber)) },
                        )

                        Box(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/** One row in the T9-style live match list — tapping fills [typedNumber] with this contact's
 * number rather than placing the call directly, so the user can still confirm/edit before
 * hitting the call button. */
@Composable
private fun MatchingContactRow(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ListItemShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (contact.name.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#').toString(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = formatPhoneNumberForDisplay(contact.phoneNumber),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.extendedColors.onSurfaceFaint,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadKey(digit: String, letters: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                    color = MaterialTheme.extendedColors.onSurfaceFaint,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackspaceButton(onClick: () -> Unit, onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onClear),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.dialpad_delete_content_description),
            tint = MaterialTheme.extendedColors.onSurfaceFaint,
        )
    }
}

@Composable
private fun CallButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = stringResource(R.string.dialpad_call_content_description),
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.extendedColors.onSurfaceFaint,
            modifier = Modifier.size(30.dp),
        )
    }
}
