package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.util.formatPhoneNumberForDisplay
import coil.compose.AsyncImage
import java.util.Locale

private val SquircleShape = RoundedCornerShape(20.dp)

/** Content drawn on the solid red/green call-action fills (Decline/Answer/End call). */
private val OnCallActionColor = Color.White

/** Full-screen backdrop shared by incoming/outgoing/active call screens — the theme's own canvas
 * (light/dark aware), with a faint red wash from the top for locally-marked spam calls so they
 * still read as distinct at a glance, not just via the tag. */
@Composable
fun CallScreenBackground(
    isSpam: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val canvas = MaterialTheme.colorScheme.background
    val backdrop = if (isSpam) {
        Modifier.background(
            Brush.verticalGradient(
                0f to MaterialTheme.extendedColors.spam.copy(alpha = 0.14f),
                0.55f to canvas,
            ),
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(canvas)
            .then(backdrop),
        content = content,
    )
}

/** Top-of-screen "Phone" app label (small phone glyph + label) — sits just under the system
 * status bar, which itself supplies the time/signal/battery. [trailing] hosts e.g. Minimize. */
@Composable
fun CallAppLabelRow(
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.mutedText,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.call_app_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.extendedColors.mutedText,
        )
        Spacer(modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Circular avatar — contact photo via Coil when available, otherwise the colored initial-letter
 * circle (same primary fill as Home's call-log avatars), otherwise a neutral unknown-caller glyph
 * (or a spam glyph for a locally-marked spam number with no resolvable name). */
@Composable
fun CallerAvatar(
    photoUri: String?,
    initialsSource: String?,
    isSpam: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 172.dp,
    // Contact lookup still pending — draws a neutral circle with no glyph, rather than a
    // number-derived glyph that would then swap to the contact's own initial/photo.
    isResolving: Boolean = false,
) {
    val initial = initialsSource?.firstOrNull { it.isLetter() }?.uppercaseChar()
    val backgroundColor = when {
        isResolving || photoUri != null -> MaterialTheme.colorScheme.surfaceVariant
        initial == null && isSpam -> MaterialTheme.extendedColors.spam.copy(alpha = 0.15f)
        initial == null -> MaterialTheme.colorScheme.surfaceVariant
        isSpam -> MaterialTheme.extendedColors.spam
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isResolving -> Unit
            photoUri != null -> AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
            initial != null -> Text(
                text = initial.toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = (size.value * 0.4f).sp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
            isSpam -> Icon(
                imageVector = Icons.Filled.ReportProblem,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.spam,
                modifier = Modifier.size(size * 0.42f),
            )
            else -> Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.mutedText,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

/** Name/number + avatar block shared by Incoming, Outgoing Dialing and Active Call — the one
 * place that decides what caller identity a call screen paints, so no surface can drift back to
 * rendering the raw number before the contact lookup settles. While [isResolving] it shows a
 * neutral placeholder; afterwards it swaps once, straight to the final state (name over number,
 * or number alone when genuinely not a contact). Always lays out exactly two text lines (the
 * second left empty when there's nothing to put there) so that one swap never reflows anything
 * below it — the avatar, tag and buttons stay exactly where they were. [statusText] (e.g.
 * "Calling…") sits small and muted above the name; [showAvatar] lets Active Call drop the avatar
 * while its in-call keypad is open. */
@Composable
fun CallerIdentityHeader(
    isResolving: Boolean,
    displayName: String?,
    phoneNumber: String?,
    photoUri: String?,
    isSpam: Boolean,
    unknownLabel: String,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    showAvatar: Boolean = true,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (statusText != null) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.extendedColors.mutedText,
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        val formattedNumber = phoneNumber?.let { formatPhoneNumberForDisplay(it) }
        val primaryText = when {
            isResolving -> ""
            displayName != null -> displayName
            else -> formattedNumber ?: unknownLabel
        }
        val secondaryText = if (!isResolving && displayName != null) formattedNumber.orEmpty() else ""
        Text(
            text = primaryText,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = secondaryText,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = MaterialTheme.extendedColors.mutedText,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        if (showAvatar) {
            Spacer(modifier = Modifier.height(28.dp))
            CallerAvatar(
                photoUri = photoUri,
                initialsSource = displayName ?: phoneNumber,
                isSpam = isSpam,
                isResolving = isResolving,
            )
        }
    }
}

/** Small pill-shaped context tag below the caller info block: "Saved contact" (blue) for known
 * contacts, "Marked spam by you before" (red) for locally-marked spam numbers. Callers simply
 * don't render this composable at all for genuinely unknown numbers. */
@Composable
fun ContextTag(text: String, isSpam: Boolean, modifier: Modifier = Modifier) {
    val tint = if (isSpam) MaterialTheme.extendedColors.spam else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

/** Subtle "which SIM/line" indicator shown beneath the caller info on Incoming/Active Call —
 * [label] is only ever passed non-null on a genuinely multi-SIM device (see
 * PhoneAccounts.resolveAccountLabel), so single-SIM users never see this at all. Deliberately
 * muted/neutral (not colored like [ContextTag]) so it reads as secondary to the caller-ID
 * information rather than competing with the Saved-contact/Spam tag. */
@Composable
fun SimAccountLabel(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.SimCard,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.mutedText,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.extendedColors.mutedText,
        )
    }
}

/** Outlined, fully-rounded secondary pill (Incoming's "Urgent?" / "Message" / "Block"). */
@Composable
fun CallSecondaryPillButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Large solid pill (radius = half height) for Decline/Answer — icon + optional label in white.
 * Width comes from [modifier] (Incoming gives each one weight(1f) in a row). */
@Composable
fun CallOvalActionButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    height: Dp = 68.dp,
    iconRotation: Float = 0f,
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(containerColor)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (label == null) contentDescription else null,
            tint = OnCallActionColor,
            modifier = Modifier
                .size(28.dp)
                .rotate(iconRotation),
        )
        if (label != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = OnCallActionColor,
            )
        }
    }
}

/** The single large red end-call pill shared by Outgoing Dialing and Active Call — a phone
 * glyph rotated 135° into the hang-up pose, no label. */
@Composable
fun EndCallButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    CallOvalActionButton(
        icon = Icons.Filled.Call,
        contentDescription = stringResource(R.string.active_call_end_call_label),
        containerColor = MaterialTheme.extendedColors.callMissed,
        onClick = onClick,
        height = 66.dp,
        iconRotation = 135f,
        modifier = modifier.width(196.dp),
    )
}

/** Rounded-rect ("squircle") in-call control with its label underneath — Keypad/Mute/Speaker/More.
 * Fills with the primary color while toggled on; dimmed and inert when ![enabled]. */
@Composable
fun CallSquircleButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier.alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(SquircleShape)
                .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extendedColors.onSurfaceSoft,
        )
    }
}

/** Formats elapsed seconds as `mm:ss` for the active-call timer. */
fun formatCallTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
