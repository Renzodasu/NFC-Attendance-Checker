package com.nezzar.nfcattendance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.nfc.NfcState
import com.nezzar.nfcattendance.ui.theme.BrandAmber
import com.nezzar.nfcattendance.ui.theme.LightAmber

/**
 * The NFC light: one small circle that stands for the phone's reader, drawn
 * wherever the app reports on it.
 *
 *   green   the adapter is on and reading
 *   amber   the phone has NFC but it is switched off in system settings
 *   black   no NFC hardware at all - outlined, so it still reads as a circle
 *
 * While the adapter is on and the app is actually listening - registering a
 * card, or a live session - the dot glows and breathes like the chosen card
 * does, and flares once for every card that lands. It is the same pulse, at the
 * same beat, so the card and the light agree about what just happened.
 */
@Composable
fun NfcLight(state: AppState, modifier: Modifier = Modifier, dot: Dp = 10.dp) {
    NfcLight(
        nfc = state.nfcState,
        glow = state.cardGlow,
        listening = state.listeningForCards,
        modifier = modifier,
        dot = dot,
    )
}

/**
 * The same light from plain values, so it can be drawn outside a full [AppState]
 * and looked at on its own in a preview.
 */
@Composable
fun NfcLight(
    nfc: NfcState,
    glow: CardGlow,
    listening: Boolean,
    modifier: Modifier = Modifier,
    dot: Dp = 10.dp,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colour = when (nfc) {
        NfcState.ENABLED -> MaterialTheme.colorScheme.primary
        NfcState.DISABLED -> if (dark) BrandAmber else LightAmber
        NfcState.UNSUPPORTED -> Color.Black
    }
    val reading = nfc == NfcState.ENABLED

    Box(
        // Room for the halo to breathe outside the dot, which is drawn over it.
        modifier = modifier
            .size(dot + 16.dp)
            .nfcLightGlow(colour = colour, glow = glow, enabled = reading && listening, dot = dot),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(dot)
                .background(colour, CircleShape)
                // A black dot on a black page still has to read as a circle.
                .border(
                    width = 1.dp,
                    color = if (!reading && nfc == NfcState.UNSUPPORTED) {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    } else {
                        Color.Transparent
                    },
                    shape = CircleShape,
                ),
        )
    }
}

/**
 * The halo: the same quiet breath and per-card swell the chosen card uses, in
 * miniature. Returns this modifier untouched whenever the reader is not reading
 * - which is what makes the glow stop the moment NFC is off or the session ends.
 */
@Composable
private fun Modifier.nfcLightGlow(
    colour: Color,
    glow: CardGlow,
    enabled: Boolean,
    dot: Dp,
): Modifier {
    if (!enabled || !glow.standby) return this

    val transition = rememberInfiniteTransition(label = "nfcLight")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "nfcBreath",
    )
    val swell = remember { Animatable(0f) }
    LaunchedEffect(glow.beat) {
        if (glow.beat == 0L) return@LaunchedEffect
        swell.snapTo(1f)
        swell.animateTo(0f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
    }

    val landed = swell.value
    val quiet = 0.62f + 0.38f * breath

    return this.drawBehind {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = dot.toPx() / 2f
        val reach = 6.dp.toPx() * quiet + 16.dp.toPx() * landed
        val strength = 0.30f * quiet + 0.55f * landed

        // Even layers, each fainter and wider than the last: one soft bloom
        // rather than concentric stripes.
        val layers = 12
        for (layer in 0 until layers) {
            val t = layer / (layers - 1f)
            val alpha = (1f - t) * (1f - t) * strength * 0.55f
            if (alpha < 0.004f) continue
            drawCircle(
                color = colour.copy(alpha = alpha),
                radius = radius + t * reach,
                center = centre,
                style = Stroke(width = reach / layers * 2.6f + 0.6.dp.toPx()),
            )
        }

        repeat(3) { index ->
            val phase = (breath + index / 3f) % 1f
            val alpha = (1f - phase) * (1f - phase) * (0.30f + 0.30f * landed)
            drawCircle(
                color = colour.copy(alpha = alpha),
                radius = radius + phase * (reach + 4.dp.toPx()),
                center = centre,
                style = Stroke(width = 1.1.dp.toPx() + 1.4.dp.toPx() * landed),
            )
        }
    }
}
