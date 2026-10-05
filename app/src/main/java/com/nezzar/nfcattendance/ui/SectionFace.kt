package com.nezzar.nfcattendance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import com.nezzar.nfcattendance.data.VisualStyle

/**
 * The selected class, drawn exactly the way the Sections shelf draws it - the same
 * 200 x 286 card, the same theme face, the same chosen treatment - centred, and it
 * flies in from the shelf card's own rectangle when you arrive from there.
 */
@Composable
fun SectionFaceCard(state: AppState, modifier: Modifier = Modifier) {
    val section = state.selectedSection ?: return
    val index = state.sections.indexOf(section).coerceAtLeast(0)

    val from = state.cardFlight
    val progress = remember(section.name, from) { Animatable(if (from == null) 1f else 0f) }
    LaunchedEffect(section.name, from) {
        if (from != null) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 380, easing = EmphasizedDecelerate))
            state.cardFlight = null
        }
    }
    val to = remember { arrayOfNulls<Rect>(1) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                // The card reports its own resting place; the flight is measured
                // against it, so nothing has to hard-code a coordinate.
                to[0] = coords.boundsInRoot()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                val start = from
                val end = to[0]
                if (start == null || end == null) {
                    return@graphicsLayer
                }
                val p = progress.value
                val scale = start.width / end.width.coerceAtLeast(1f)
                val blended = scale + (1f - scale) * p
                scaleX = blended
                scaleY = blended
                translationX = (start.left - end.left) * (1f - p) * (1f / blended)
                translationY = (start.top - end.top) * (1f - p) * (1f / blended)
                alpha = 0.35f + 0.65f * p
            },
        ) {
            when (state.visualStyle) {
                VisualStyle.PLAIN -> PlainSectionCard(section, selected = true, onSelect = {})
                VisualStyle.SOLIDS -> SolidSectionCard(
                    section = section,
                    index = index,
                    selected = true,
                    onSelect = {},
                )
                else -> SectionCard(section, selected = true, onSelect = {})
            }
        }
    }
}
