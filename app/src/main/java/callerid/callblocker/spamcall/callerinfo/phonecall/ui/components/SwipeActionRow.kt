package callerid.callblocker.spamcall.callerinfo.phonecall.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import callerid.callblocker.spamcall.callerinfo.phonecall.R
import callerid.callblocker.spamcall.callerinfo.phonecall.ui.theme.ListItemShape
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TRIGGER_FRACTION = 0.6f
private val RevealCallColor = Color(0xFF2E7D32)
private val RevealMessageColor = Color(0xFF1565C0)

/**
 * Wraps a list row with swipe-right-to-call / swipe-left-to-message — a reveal-and-act pattern,
 * not a dismiss/removal, so this is a custom drag (via [Animatable]) rather than
 * SwipeToDismissBox: the row must always spring back to its resting position, never actually
 * leave the list. Shared between the Recents list (HomeScreen) and All Contacts so both behave
 * identically instead of duplicating the gesture logic.
 *
 * The action only fires once the drag crosses [TRIGGER_FRACTION] of the row's width; releasing
 * before that just snaps back with no action taken.
 */
@Composable
fun SwipeActionRow(
    enabled: Boolean,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = ListItemShape,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    var rowWidthPx by remember { mutableStateOf(0f) }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val triggerPx = rowWidthPx * TRIGGER_FRACTION
    val progress = if (triggerPx > 0f) (abs(offsetX.value) / triggerPx).coerceIn(0f, 1f) else 0f
    val triggered = triggerPx > 0f && abs(offsetX.value) >= triggerPx
    val iconScale = if (triggered) 1.3f else 0.85f + 0.3f * progress
    val iconAlpha = 0.5f + 0.5f * progress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width.toFloat() },
    ) {
        // Reveal background — only one side is ever visible at a time, based on drag direction.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .background(
                    when {
                        offsetX.value > 0f -> RevealCallColor
                        offsetX.value < 0f -> RevealMessageColor
                        else -> Color.Transparent
                    },
                ),
        ) {
            if (offsetX.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 24.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = stringResource(R.string.dialpad_call_content_description),
                        tint = Color.White.copy(alpha = iconAlpha),
                        modifier = Modifier
                            .size(26.dp)
                            .scale(iconScale),
                    )
                }
            } else if (offsetX.value < 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = stringResource(R.string.incoming_message_label),
                        tint = Color.White.copy(alpha = iconAlpha),
                        modifier = Modifier
                            .size(26.dp)
                            .scale(iconScale),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(rowWidthPx) {
                    var wasTriggered = false
                    detectHorizontalDragGestures(
                        onDragStart = { wasTriggered = false },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newValue = (offsetX.value + dragAmount).coerceIn(-rowWidthPx, rowWidthPx)
                            scope.launch { offsetX.snapTo(newValue) }
                            val nowTriggered = rowWidthPx > 0f && abs(newValue) >= rowWidthPx * TRIGGER_FRACTION
                            // Fires once right as the drag crosses the trigger distance — the
                            // scale/brighten visual feedback already tracks progress continuously,
                            // this is just the extra tactile cue for "releasing now will act".
                            if (nowTriggered && !wasTriggered) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            wasTriggered = nowTriggered
                        },
                        onDragEnd = {
                            val finalValue = offsetX.value
                            val didTrigger = rowWidthPx > 0f && abs(finalValue) >= rowWidthPx * TRIGGER_FRACTION
                            scope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                            if (didTrigger) {
                                if (finalValue > 0f) onCall() else onMessage()
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, animationSpec = spring()) }
                        },
                    )
                },
        ) {
            content()
        }
    }
}
