package com.nezzar.nfcattendance.ui

import com.nezzar.nfcattendance.data.VisualStyle

import com.nezzar.nfcattendance.data.PlayingCards

import androidx.compose.ui.unit.sp

import androidx.compose.ui.unit.Dp

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.geometry.Offset

import androidx.compose.foundation.border

import androidx.compose.foundation.background

import androidx.compose.foundation.Canvas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import kotlinx.coroutines.delay

/** The Material "emphasized decelerate" curve, used for anything the eye follows. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** A springy, physical curve for anything the finger touches. */
const val MotionTouchMs = 220
const val MotionScreenMs = 300
const val MotionStaggerMs = 30

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The workhorse surface: rounded, slightly raised, no hard borders. */
@Composable
fun BrandCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = container,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

/**
 * Press-in feedback with a spring: a card the finger touches shrinks a hair and
 * springs back. Applied together with [cardClick] so the ripple and the scale
 * share one interaction source.
 */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.98f): Modifier {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return this.scale(scale)
}

/** Click that keeps the default ripple but lets [pressScale] observe the press. */
@Composable
fun Modifier.cardClick(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interaction,
    indication = LocalIndication.current,
    enabled = enabled,
    onClick = onClick,
)

/** Big bold screen title. It shrinks and lifts away as the list under it scrolls. */
@Composable
fun CollapsingTitle(title: String, subtitle: String?, listState: LazyListState) {
    val shrink = if (listState.firstVisibleItemIndex > 0) {
        1f
    } else {
        (listState.firstVisibleItemScrollOffset / 260f).coerceIn(0f, 1f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = 1f - 0.16f * shrink
                scaleX = scale
                scaleY = scale
                alpha = 1f - shrink
                transformOrigin = TransformOrigin(0f, 0f)
            }
            .padding(top = 6.dp, bottom = 10.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Big number plus caption - the app's stats language. */
@Composable
fun StatTile(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 320, easing = EmphasizedDecelerate),
        label = "stat",
    )
    val container by animateColorAsState(
        targetValue = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = MotionTouchMs, easing = EmphasizedDecelerate),
        label = "statContainer",
    )
    val content by animateColorAsState(
        targetValue = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(durationMillis = MotionTouchMs, easing = EmphasizedDecelerate),
        label = "statContent",
    )
    val caption by animateColorAsState(
        targetValue = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = MotionTouchMs, easing = EmphasizedDecelerate),
        label = "statCaption",
    )
    Surface(color = container, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text = animated.toString(), style = MaterialTheme.typography.headlineSmall, color = content)
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = caption,
                // Two lines so a large system font earns a wrap instead of a
                // truncated caption ("UNMATCH" at font_scale 1.3).
                maxLines = 2,
            )
        }
    }
}

/**
 * Label on the left, value on the right. The two columns share the row instead of
 * the label owning a fixed 150.dp box, so a long label and a long value wrap the
 * same way at any font scale.
 */
@Composable
fun KeyValueRow(label: String, value: String, valueStyle: TextStyle? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.46f),
        )
        Crossfade(
            targetState = value,
            animationSpec = tween(durationMillis = MotionTouchMs, easing = EmphasizedDecelerate),
            label = "keyValue",
            modifier = Modifier.weight(0.54f),
        ) { current ->
            Text(
                text = current,
                style = valueStyle ?: MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * What an export actually produced: the file's name (not a 79-character absolute
 * path), where it landed, and the one thing you would want next - Share.
 */
@Composable
fun ExportResult(path: String?, note: String?, onShare: (() -> Unit)?) {
    // The card grows into its result instead of blinking it on: an export is
    // read at arm's length, in the middle of a class.
    AnimatedVisibility(
        visible = path != null,
        enter = fadeIn(tween(MotionTouchMs)) +
            slideInVertically(tween(MotionScreenMs, easing = EmphasizedDecelerate)) { it / 3 },
    ) {
        if (path != null) {
            Column {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(text = path.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge)
                        Note("Saved in " + path.substringBeforeLast('/').substringAfterLast('/') + ".")
                    }
                }
            }
        }
    }
    AnimatedVisibility(
        visible = note != null,
        enter = fadeIn(tween(MotionTouchMs)) +
            slideInVertically(tween(MotionScreenMs, easing = EmphasizedDecelerate)) { it / 3 },
    ) {
        if (note != null) {
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Export note: " + note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    AnimatedVisibility(
        visible = onShare != null,
        enter = fadeIn(tween(MotionTouchMs)) +
            slideInVertically(tween(MotionScreenMs, easing = EmphasizedDecelerate)) { it / 3 },
    ) {
        if (onShare != null) {
            Column {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Share this .xlsx")
                }
            }
        }
    }
}

/** A small rounded badge. */
@Composable
fun Pill(text: String, accent: Boolean = false) {
    Surface(
        color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Muted supporting sentence. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * An empty state that still has somewhere to go: mark, one-line reason, and the
 * action that fills it. A padded icon round gives the block a focal point, so an
 * empty screen does not read as a broken one.
 */
@Composable
fun EmptyState(
    icon: Int,
    title: String,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    BrandCard {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = CircleShape) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Note(body)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.height(50.dp),
            ) { Text(actionLabel) }
        }
    }
}

/** Status line that cross-fades instead of repainting. */
@Composable
fun AnimatedStatusLine(
    text: String,
    emphasise: Boolean,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        targetState = text,
        animationSpec = tween(durationMillis = MotionTouchMs, easing = EmphasizedDecelerate),
        label = "status",
        modifier = modifier,
    ) { value ->
        Text(
            text = value,
            style = style,
            color = if (emphasise) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** One row of an assembling list: fades and slides in, staggered by index. */
@Composable
fun StaggeredRow(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val delayMs = index.coerceAtMost(12) * MotionStaggerMs
        if (delayMs > 0) delay(delayMs.toLong())
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(200, easing = EmphasizedDecelerate)) +
            slideInVertically(tween(200, easing = EmphasizedDecelerate)) { it / 3 },
    ) {
        content()
    }
}

/**
 * A short list of facts, each behind a quiet dot. A line may lead with
 * "Lead - detail": the lead is drawn in the fuller ink and the rest dimmed, so a
 * bullet can be skimmed without being read twice.
 */
@Composable
fun Points(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(14.dp),
                )
                Text(
                    text = buildAnnotatedString {
                        val split = line.indexOf(" - ")
                        if (split <= 0) {
                            append(line)
                        } else {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                                append(line.take(split))
                            }
                            append(line.substring(split))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * A label that swaps itself instead of switching: Start / Pause / End read as one
 * control changing its mind, not as three different buttons.
 */
@Composable
fun SwapLabel(text: String, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(tween(MotionTouchMs, easing = EmphasizedDecelerate)) +
                slideInVertically(tween(MotionTouchMs, easing = EmphasizedDecelerate)) { it / 2 })
                .togetherWith(
                    fadeOut(tween(140)) +
                        slideOutVertically(tween(140)) { -it / 2 }
                )
        },
        label = "swapLabel",
        modifier = modifier,
    ) { current ->
        Text(text = current, maxLines = 1)
    }
}