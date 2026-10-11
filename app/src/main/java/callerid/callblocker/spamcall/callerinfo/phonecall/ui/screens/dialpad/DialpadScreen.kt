package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.dialpad

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.Contact
import callerid.callblocker.spamcall.callerinfo.phonecall.domain.model.RecentCallEntry
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatDialpadInput
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.normalizeDialedNumber
import coil.compose.AsyncImage

private data class DialpadKeyData(val digit: String, val letters: String)

// Key 1 has no voicemail glyph: the app has no voicemail feature to reach from it.
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
    DialpadKeyData("0", "+"),
    DialpadKeyData("#", ""),
)

private val DtmfTones = mapOf(
    "0" to ToneGenerator.TONE_DTMF_0,
    "1" to ToneGenerator.TONE_DTMF_1,
    "2" to ToneGenerator.TONE_DTMF_2,
    "3" to ToneGenerator.TONE_DTMF_3,
    "4" to ToneGenerator.TONE_DTMF_4,
    "5" to ToneGenerator.TONE_DTMF_5,
    "6" to ToneGenerator.TONE_DTMF_6,
    "7" to ToneGenerator.TONE_DTMF_7,
    "8" to ToneGenerator.TONE_DTMF_8,
    "9" to ToneGenerator.TONE_DTMF_9,
    "*" to ToneGenerator.TONE_DTMF_S,
    "#" to ToneGenerator.TONE_DTMF_P,
)

private const val DTMF_TONE_DURATION_MS = 50

// Relative to the DTMF stream's own volume (the in-call/system volume the user already set).
private const val DTMF_TONE_VOLUME = 80

/** The system "Dial pad tones" toggle (Settings > Sound) — the same one the stock dialer honours. */
private fun dialpadTonesEnabled(context: Context): Boolean =
    Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1

/**
 * Per-key press feedback: a short DTMF tone plus a KEYBOARD_TAP haptic, like the stock dialer.
 * Neither is forced — the haptic goes through View.performHapticFeedback, which already respects
 * the system touch-feedback setting, and the tone only plays with "Dial pad tones" on and the
 * ringer in normal mode (not silent/vibrate).
 */
@Composable
private fun rememberKeyPressFeedback(): (digit: String) -> Unit {
    val context = LocalContext.current
    val view = LocalView.current
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    // Can throw if the audio system refuses a new tone generator; the keypad then just stays silent.
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_DTMF, DTMF_TONE_VOLUME)
        } catch (e: RuntimeException) {
            null
        }
    }
    DisposableEffect(toneGenerator) {
        onDispose { toneGenerator?.release() }
    }
    return remember(view, toneGenerator) {
        { digit ->
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val tone = DtmfTones[digit]
            if (tone != null &&
                toneGenerator != null &&
                audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
                dialpadTonesEnabled(context)
            ) {
                toneGenerator.startTone(tone, DTMF_TONE_DURATION_MS)
            }
        }
    }
}

private val KeypadSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val DialpadKeyShape = RoundedCornerShape(30.dp)
private val BackspaceShape = RoundedCornerShape(10.dp)
private val SuggestedCardShape = RoundedCornerShape(18.dp)

// Google Phone's universal call-affordance green — deliberately fixed, not the app's primary.
private val CallGreenLight = Color(0xFF2DA84B)
private val CallGreenDark = Color(0xFF34C063)

/**
 * Manual numeric dialpad — reached from Home's FAB. With nothing typed, shows Suggested contacts
 * (see [DialpadViewModel.suggestions]); once digits are typed, a live T9-style match list instead.
 * Tapping a match or suggestion row just fills the field with that number rather than calling
 * directly, so the call button still hands the typed digits to [onCall]; a suggestion's own call
 * button dials it straight away.
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
    val suggestions by viewModel.suggestions.collectAsState()

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
        // Only the horizontal insets apply to the whole column: the keypad sheet takes the bottom
        // (navigation bar) inset itself so its surface runs down behind the gesture bar.
        val layoutDirection = LocalLayoutDirection.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                ),
        ) {
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                if (typedNumber.isNotEmpty()) {
                    if (matchingContacts.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(matchingContacts, key = { it.phoneNumber }) { contact ->
                                MatchingContactRow(
                                    contact = contact,
                                    onClick = { typedNumber = contact.phoneNumber },
                                )
                            }
                        }
                    }
                } else if (suggestions.isNotEmpty()) {
                    SuggestedSection(
                        suggestions = suggestions,
                        onSelect = { typedNumber = it.phoneNumber },
                        onCall = { onCall(normalizeDialedNumber(it.phoneNumber)) },
                    )
                }
            }

            KeypadSheet(
                bottomInset = innerPadding.calculateBottomPadding(),
                typedNumber = typedNumber,
                onDigit = { typedNumber += it },
                onBackspace = { typedNumber = typedNumber.dropLast(1) },
                onClear = { typedNumber = "" },
                // Dials what's displayed: +91-normalized for a complete Indian number,
                // otherwise exactly what was typed (see normalizeDialedNumber).
                onCall = { if (typedNumber.isNotEmpty()) onCall(normalizeDialedNumber(typedNumber)) },
            )
        }
    }
}

@Composable
private fun SuggestedSection(
    suggestions: List<RecentCallEntry>,
    onSelect: (RecentCallEntry) -> Unit,
    onCall: (RecentCallEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.dialpad_suggested_title),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 10.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = SuggestedCardShape)
                .clip(SuggestedCardShape)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            suggestions.forEachIndexed { index, entry ->
                SuggestedContactRow(entry = entry, onClick = { onSelect(entry) }, onCall = { onCall(entry) })
                if (index != suggestions.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SuggestedContactRow(entry: RecentCallEntry, onClick: () -> Unit, onCall: () -> Unit) {
    val name = entry.name.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarModifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
        if (entry.photoUri != null) {
            AsyncImage(model = entry.photoUri, contentDescription = null, modifier = avatarModifier)
        } else {
            Box(
                modifier = avatarModifier.background(MaterialTheme.extendedColors.accentContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (name.firstOrNull { it.isLetter() }?.uppercaseChar() ?: '#').toString(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.contact_detail_number_subtitle,
                    stringResource(R.string.contact_detail_number_label),
                    entry.formattedPhoneNumber,
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.extendedColors.accentContainer)
                .clickable(role = Role.Button, onClick = onCall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = stringResource(R.string.dialpad_call_content_description),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
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
                text = remember(contact.phoneNumber) { formatPhoneNumberForDisplay(contact.phoneNumber) },
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.extendedColors.onSurfaceFaint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun KeypadSheet(
    bottomInset: Dp,
    typedNumber: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onCall: () -> Unit,
) {
    val onKeyPress = rememberKeyPressFeedback()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = KeypadSheetShape)
            .clip(KeypadSheetShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + bottomInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                text = remember(typedNumber) { formatDialpadInput(typedNumber) },
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    // Keeps long numbers clear of the backspace button on the right.
                    .padding(horizontal = 44.dp),
            )
            if (typedNumber.isNotEmpty()) {
                BackspaceButton(
                    onClick = onBackspace,
                    onClear = onClear,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DialpadKeys.chunked(3).forEach { rowKeys ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowKeys.forEach { key ->
                        DialpadKey(
                            digit = key.digit,
                            letters = key.letters,
                            onPress = { onKeyPress(key.digit) },
                            onClick = { onDigit(key.digit) },
                            // Long-press 0 types "+", as in Google's dialer — the only
                            // way to enter an international number by hand.
                            onLongClick = if (key.digit == "0") {
                                { onDigit("+") }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CallButton(enabled = typedNumber.isNotEmpty(), onClick = onCall)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadKey(
    digit: String,
    letters: String,
    onPress: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    // Feedback fires on touch-down, not on release like onClick, so it lands the instant a finger
    // hits the key, as on a real dialer.
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnPress by rememberUpdatedState(onPress)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Press) currentOnPress() }
    }
    Box(
        modifier = modifier
            .height(62.dp)
            .clip(DialpadKeyShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Always laid out (blank for 1, * and #) so every digit sits at the same height.
            Text(
                text = letters.ifEmpty { " " },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackspaceButton(onClick: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(BackspaceShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, BackspaceShape)
            .combinedClickable(onClick = onClick, onLongClick = onClear),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.dialpad_delete_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CallButton(enabled: Boolean, onClick: () -> Unit) {
    // The in-app theme can differ from the system's, so dark is read off the actual canvas colour.
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        isDark -> CallGreenDark
        else -> CallGreenLight
    }
    val contentColor = when {
        !enabled -> MaterialTheme.extendedColors.onSurfaceFaint
        isDark -> MaterialTheme.colorScheme.onPrimary
        else -> Color.White
    }
    Row(
        modifier = Modifier
            .height(56.dp)
            .clip(DialpadKeyShape)
            .background(containerColor)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.contact_detail_action_call),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = contentColor,
        )
    }
}
