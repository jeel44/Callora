package callerid.callblocker.spamcall.callerinfo.phonecall.ui.screens.call

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.extendedColors
import coil.compose.AsyncImage
import java.util.Locale

// Full-bleed call-screen gradients — intentionally hardcoded and independent of
// MaterialTheme.colorScheme: real phone-call UI always stays dark-styled regardless
// of the app's light/dark theme setting. Radial (not flat/linear) so spam calls read as visually
// distinct at a glance, not just via a small icon: brighter center-top, fading to near-black edges.
private val KnownRadialCenter = Color(0xFF1E3A5F)
private val KnownRadialMid = Color(0xFF0B1420)
private val KnownRadialEdge = Color(0xFF060B12)
private val SpamRadialCenter = Color(0xFF4A1E1E)
private val SpamRadialMid = Color(0xFF250F0F)
private val SpamRadialEdge = Color(0xFF0F0808)

private val AvatarShape = RoundedCornerShape(28.dp)
private val UtilityButtonShape = RoundedCornerShape(18.dp)

/** Full-screen radial gradient backdrop shared by incoming/outgoing/active call screens. */
@Composable
fun CallScreenBackground(
    isSpam: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = if (isSpam) {
        listOf(SpamRadialCenter, SpamRadialMid, SpamRadialEdge)
    } else {
        listOf(KnownRadialCenter, KnownRadialMid, KnownRadialEdge)
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val brush = Brush.radialGradient(
                    colors = colors,
                    // Center-top brighter (not dead-center) — matches a real phone dialer's
                    // call-screen glow sitting behind the avatar near the top of the screen.
                    center = Offset(size.width / 2f, size.height * 0.32f),
                    radius = size.maxDimension * 0.9f,
                )
                onDrawBehind { drawRect(brush) }
            },
        content = content,
    )
}

/** Small pill-shaped status label near the top of every call screen — "INCOMING CALL",
 * "OUTGOING CALL", or the live MM:SS timer for Active Call — with a small pulsing dot. */
@Composable
fun StatusPill(text: String, isSpam: Boolean, modifier: Modifier = Modifier, showPulsingDot: Boolean = true) {
    val backgroundColor = if (isSpam) MaterialTheme.extendedColors.spam.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f)
    val contentColor = Color.White
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPulsingDot) {
            PulsingDot(color = contentColor)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            ),
            color = contentColor,
        )
    }
}

/** A single dot that fades in/out forever — the pulsing indicator on [StatusPill]. */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, size: Dp = 7.dp) {
    val transition = rememberInfiniteTransition(label = "pulsing_dot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "pulsing_dot_alpha",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

/** Three dots pulsing in a staggered wave — used next to "Calling…"/"Ringing…" on Outgoing
 * Dialing so the status text reads as actively in-progress rather than a static label. */
@Composable
fun PulsingDotsRow(modifier: Modifier = Modifier, color: Color = Color.White) {
    val transition = rememberInfiniteTransition(label = "pulsing_dots_row")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulsing_dots_row_$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha)),
            )
        }
    }
}

/** Rounded-square avatar with a subtle lighter-toned ring border — contact photo via Coil when
 * available, otherwise a colored initial (or a spam icon for numbers with no resolvable name). */
@Composable
fun CallerAvatar(
    photoUri: String?,
    initialsSource: String?,
    isSpam: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    shape: Shape = AvatarShape,
) {
    val ringColor = Color.White.copy(alpha = 0.28f)
    val backgroundColor = if (isSpam) {
        MaterialTheme.extendedColors.spam.copy(alpha = 0.28f)
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor)
            .border(width = 2.dp, color = ringColor, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        val initial = initialsSource?.firstOrNull { it.isLetter() }?.uppercaseChar()
        when {
            photoUri != null -> AsyncImage(
                model = photoUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape),
            )
            isSpam && initial == null -> Icon(
                imageVector = Icons.Filled.ReportProblem,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.spam,
                modifier = Modifier.size(size * 0.42f),
            )
            else -> Text(
                text = initial?.toString() ?: "#",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
        }
    }
}

/** Small pill-shaped context tag below the caller info block: "Saved contact" (blue) for known
 * contacts, "Marked spam by you before" (red) for locally-marked spam numbers. Callers simply
 * don't render this composable at all for genuinely unknown numbers. */
@Composable
fun ContextTag(text: String, isSpam: Boolean, modifier: Modifier = Modifier) {
    val tint = if (isSpam) MaterialTheme.extendedColors.spam else Color(0xFF4FA3FF)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.22f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.92f),
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
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.SimCard,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.65f),
        )
    }
}

/** Icon-in-circle + label used for the big Decline/Answer/End-call actions (unchanged shape —
 * still a filled circle per the approved design, unlike [UtilityIconButton]'s rounded square). */
@Composable
fun CallActionButton(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(containerColor),
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = contentColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/** Rounded-square utility button (Mute/Speaker/Keypad/…) that visually inverts — white background,
 * dark icon — while toggled on, rather than the filled-circle style of [CallActionButton]. */
@Composable
fun UtilityIconButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(size)
                .clip(UtilityButtonShape)
                .background(if (isActive) Color.White else Color.White.copy(alpha = 0.15f)),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFF0B1420) else Color.White,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

/** Formats elapsed seconds as `mm:ss` for the active-call timer. */
fun formatCallTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
