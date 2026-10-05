package com.nezzar.nfcattendance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * What the chosen class card is doing right now.
 *
 * [standby] is true while the app is listening for cards - registering, or a live
 * session - and the card answers with a slow, quiet pulse. [beat] is the instant a
 * card actually landed; each new beat swells the glow once, so a real register or
 * scan reads bigger than the idle pulse.
 */
@Immutable
data class CardGlow(val standby: Boolean = false, val beat: Long = 0L) {
    companion object {
        /** Nothing is running, so the card keeps its still shadow. */
        val Idle = CardGlow()
    }
}

/**
 * The live glow around a class card, drawn on the card itself: a soft rim that
 * breathes while the app listens for cards, three rings that radiate off the
 * card's edge, and one swell for every card that lands.
 *
 * Everything is drawn in the accent colour on the card's own canvas, so it needs
 * no permission, no dependency and no extra view - and it stops dead the moment
 * registering ends or the session stops.
 */
@Composable
fun Modifier.cardGlow(accent: Color, glow: CardGlow, enabled: Boolean): Modifier {
    if (!enabled || !glow.standby) return this

    // The slow breath of the standby pulse: 1.8 s out, 1.8 s back.
    val transition = rememberInfiniteTransition(label = "cardGlow")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    // One swell per landed card: up at once, then away over a bit more than a
    // second, so the eye has time to catch which card the tap belonged to.
    val swell = remember { Animatable(0f) }
    LaunchedEffect(glow.beat) {
        if (glow.beat == 0L) return@LaunchedEffect
        swell.snapTo(1f)
        swell.animateTo(0f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }

    val landed = swell.value
    // The breath dips but never goes dark: the card stays lit while it listens.
    val quiet = 0.62f + 0.38f * breath

    return this.drawBehind {
        val corner = 14.dp.toPx()
        // Standby reaches a clear ring past the card; a landed card throws the
        // light well past its shadow, which is the moment worth noticing.
        val reach = 14.dp.toPx() * quiet + 34.dp.toPx() * landed
        val strength = 0.22f * quiet + 0.55f * landed

        // The soft rim: a stack of strokes that fade with distance, which is what
        // stands in for a blur on every API level the app supports. Many wide, faint
        // strokes overlap into a gradient; a handful of them read as stripes once the
        // swell pushes the reach out.
        val layers = 14
        for (layer in 0 until layers) {
            val t = layer / (layers - 1f)
            val alpha = (1f - t) * (1f - t) * strength * 0.62f
            if (alpha < 0.004f) continue
            val spread = t * reach
            drawRoundRect(
                color = accent.copy(alpha = alpha),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2f, size.height + spread * 2f),
                cornerRadius = CornerRadius(corner + spread, corner + spread),
                style = Stroke(width = reach / layers * 2.8f),
            )
        }

        // The radiating waves: three rings leave the edge together and fade as they go.
        repeat(3) { index ->
            val phase = (breath + index / 3f) % 1f
            val spread = phase * (reach + 8.dp.toPx())
            val alpha = (1f - phase) * (1f - phase) * (0.26f + 0.30f * landed)
            drawRoundRect(
                color = accent.copy(alpha = alpha),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2f, size.height + spread * 2f),
                cornerRadius = CornerRadius(corner + spread, corner + spread),
                style = Stroke(width = 1.5.dp.toPx() + 2.dp.toPx() * landed),
            )
        }
    }
}
