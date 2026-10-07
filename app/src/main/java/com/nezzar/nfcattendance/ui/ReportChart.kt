package com.nezzar.nfcattendance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.data.ArrivalCurve
import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.ChartData
import com.nezzar.nfcattendance.data.ChartKind
import com.nezzar.nfcattendance.data.ChartPart
import com.nezzar.nfcattendance.data.ChartTone
import com.nezzar.nfcattendance.data.Composition
import com.nezzar.nfcattendance.data.MethodChart
import com.nezzar.nfcattendance.data.ResolvedAttendance
import com.nezzar.nfcattendance.ui.theme.BrandAmber
import com.nezzar.nfcattendance.ui.theme.BrandOutline
import com.nezzar.nfcattendance.ui.theme.BrandPrimary
import com.nezzar.nfcattendance.ui.theme.BrandTextSecondary
import com.nezzar.nfcattendance.ui.theme.LightAmber
import com.nezzar.nfcattendance.ui.theme.LightOutline
import com.nezzar.nfcattendance.ui.theme.LightPrimary
import com.nezzar.nfcattendance.ui.theme.LightTextSecondary
import com.nezzar.nfcattendance.ui.theme.isBrandDark

/**
 * The chart card on the Session report: the same numbers the tiles state, drawn so
 * the shape of the class is visible at a glance.
 *
 * One lens at a time, chosen in Settings, and each answers a different question:
 *   Pie  - what did this class's attendance look like?
 *   Line - how did the room fill, and where did the late mark bite?
 *   Bar  - how was the presence actually recorded: NFC, QR or by hand?
 *
 * The chart is drawn with the same Canvas the app already uses for its card art - no
 * chart library, no extra megabyte. Every number it draws is also written out in the
 * legend beneath it, so nothing is carried by colour alone and a screen reader gets
 * the figures as text.
 */
@Composable
fun ReportChartCard(
    state: AppState,
    resolved: ResolvedAttendance,
    session: AttendanceSession,
    modifier: Modifier = Modifier,
) {
    val composition = remember(resolved) { ChartData.composition(resolved) }
    val curve = remember(resolved, session) { ChartData.arrivals(session, resolved) }
    val methods = remember(resolved) { ChartData.methods(resolved) }

    // A kind with nothing honest to say steps aside rather than drawing an empty box.
    val wanted = state.chartKind
    val drawn = when {
        wanted == ChartKind.LINE && !curve.enough -> ChartKind.PIE
        wanted == ChartKind.BAR && (methods.empty || composition.rosterSize == 1) -> ChartKind.PIE
        else -> wanted
    }
    var open by remember { mutableStateOf(true) }
    val interaction = remember { MutableInteractionSource() }

    BrandCard(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interaction, pressed = 0.99f)
                .cardClick(interaction) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("Chart")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summaryLine(composition),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = if (open) "Hide" else "Show",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (!open) return@BrandCard

        Spacer(Modifier.height(14.dp))

        when (drawn) {
            ChartKind.PIE -> DonutChart(composition)
            ChartKind.LINE -> ArrivalLine(curve)
            ChartKind.BAR -> MethodBars(methods)
        }

        if (drawn != wanted) {
            Spacer(Modifier.height(10.dp))
            Note(substitutionNote(wanted, composition, methods))
        }

        Spacer(Modifier.height(14.dp))
        when (drawn) {
            ChartKind.PIE -> CompositionLegend(composition)
            ChartKind.LINE -> LineLegend(curve)
            ChartKind.BAR -> MethodLegend(methods)
        }
    }
}

/** "32 on time · 4 late · 6 absent · 2 unmatched" - the card's own headline. */
private fun summaryLine(composition: Composition): String {
    val parts = composition.parts.joinToString("  \u00B7  ") { it.count.toString() + " " + it.label.lowercase() }
    return if (composition.unmatched > 0) {
        parts + "  \u00B7  " + composition.unmatched + " unmatched"
    } else {
        parts
    }
}

private fun substitutionNote(wanted: ChartKind, composition: Composition, methods: MethodChart): String = when {
    wanted == ChartKind.LINE ->
        "A line needs at least two readings - showing the attendance instead."
    wanted == ChartKind.BAR && methods.empty ->
        "Nothing has been read yet - showing the attendance instead."
    wanted == ChartKind.BAR && composition.rosterSize == 1 ->
        "One student is not enough for a comparison - showing the attendance instead."
    else -> ""
}

// ------------------------------------------------------------------ the three charts

/**
 * On time, late, absent as a ring. Absent is drawn as the remainder rather than as a
 * slice of its own, so the three always fill the circle exactly.
 */
@Composable
private fun DonutChart(composition: Composition) {
    val dark = isBrandDark()
    val t = chartProgress(composition.rosterSize)
    val description = composition.parts.joinToString(", ") { part ->
        part.label + " " + part.count + " of " + composition.rosterSize + ", " + part.percent + " percent"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(170.dp)
                .semantics {
                    contentDescription = "Attendance chart: " + description
                },
        ) {
            val stroke = 24.dp.toPx()
            val outer = (minOf(size.width, size.height) - stroke) / 2f
            val centre = Offset(size.width / 2f, size.height / 2f)
            // A ring with nothing in it still has to read as a ring.
            drawCircle(
                color = BrandOutline.copy(alpha = 0.35f),
                radius = outer - stroke / 2f,
                center = centre,
                style = Stroke(stroke),
            )
            if (composition.rosterSize <= 0) return@Canvas

            var angle = -90f
            composition.parts.forEach { part ->
                val sweep = 360f * part.count / composition.rosterSize.toFloat() * t
                if (sweep > 0.01f) {
                    val colour = toneColour(part.tone, dark)
                    if (part.tone == ChartTone.LATE) {
                        // A tone plus a texture: green and amber are the pair a colour-blind
                        // reader is most likely to confuse, so late is never only a colour.
                        drawArc(
                            color = colour.copy(alpha = 0.22f),
                            startAngle = angle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(stroke),
                        )
                        hatchBand(colour, angle, sweep, centre, outer, stroke)
                    } else {
                        drawArc(
                            color = colour,
                            startAngle = angle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(stroke),
                        )
                    }
                    angle += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = composition.rosterSize.toString(),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = if (composition.rosterSize == 1) "student" else "students",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Cumulative recorded students against elapsed minutes, with the late mark. */
@Composable
private fun ArrivalLine(curve: ArrivalCurve) {
    val dark = isBrandDark()
    val t = chartProgress(curve.readings)
    val ink = toneColour(ChartTone.ON_TIME, dark)
    val marker = toneColour(ChartTone.LATE, dark)
    val maxY = curve.rosterSize.coerceAtLeast(1)

    // The axes are written in text above and below the canvas rather than painted into
    // it: real text scales with the system font size, and a canvas does not.
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = maxY.toString() + " students",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "late after " + curve.windowMinutes + " min",
            style = MaterialTheme.typography.bodySmall,
            color = marker,
        )
    }
    Spacer(Modifier.height(6.dp))
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(122.dp)
            .semantics {
                contentDescription = "Arrival chart: " + curve.readings + " readings over " +
                    curve.totalMinutes + " minutes, of " + curve.rosterSize + " students"
            },
    ) {
        val padLeft = 6.dp.toPx()
        val padRight = 6.dp.toPx()
        val width = size.width - padLeft - padRight
        val height = size.height
        fun x(minute: Int) = padLeft + width * (minute.toFloat() / curve.totalMinutes.toFloat())
        fun y(value: Int) = height - height * (value.toFloat() / maxY.toFloat())

        // three gridlines: none, half, everyone
        listOf(0, maxY / 2, maxY).distinct().forEach { value ->
            drawLine(
                color = BrandOutline.copy(alpha = 0.25f),
                start = Offset(padLeft, y(value)),
                end = Offset(size.width - padRight, y(value)),
                strokeWidth = 1.dp.toPx(),
            )
        }
        // the late boundary
        if (curve.windowMinutes in 0..curve.totalMinutes) {
            val at = x(curve.windowMinutes)
            drawLine(
                color = marker.copy(alpha = 0.8f),
                start = Offset(at, 0f),
                end = Offset(at, height),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }
        // the curve, revealed left to right
        val reveal = padLeft + width * t
        val points = curve.points
        for (i in 0 until points.size - 1) {
            val a = Offset(x(points[i].minute), y(points[i].cumulative))
            val b = Offset(x(points[i + 1].minute), y(points[i + 1].cumulative))
            if (a.x > reveal) break
            drawLine(
                color = ink,
                start = a,
                end = Offset(minOf(b.x, reveal), b.y),
                strokeWidth = 1.75.dp.toPx(),
            )
        }
        points.forEach { point ->
            val centre = Offset(x(point.minute), y(point.cumulative))
            if (centre.x <= reveal) {
                drawCircle(color = ink, radius = 3.dp.toPx(), center = centre)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "0 min",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = curve.totalMinutes.toString() + " min",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** NFC, QR and Manual, with the unmatched taps set apart: those are not students. */
@Composable
private fun MethodBars(chart: MethodChart) {
    val dark = isBrandDark()
    val t = chartProgress(chart.total)
    val most = chart.bars.maxOf { it.count }.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chart.bars.forEach { bar ->
            BarRow(
                label = bar.label,
                count = bar.count,
                fraction = bar.count.toFloat() / most.toFloat(),
                progress = t,
                colour = toneColour(bar.tone, dark),
            )
        }
        if (chart.unmatched > 0) {
            Spacer(Modifier.height(2.dp))
            BarRow(
                label = "Unmatched",
                count = chart.unmatched,
                fraction = chart.unmatched.toFloat() / most.toFloat(),
                progress = t,
                colour = BrandOutline,
                caption = "not on the roster",
            )
        }
    }
}

@Composable
private fun BarRow(
    label: String,
    count: Int,
    fraction: Float,
    progress: Float,
    colour: Color,
    caption: String? = null,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.width(86.dp),
            )
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(18.dp),
            ) {
                drawRoundRect(
                    color = BrandOutline.copy(alpha = 0.22f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()),
                )
                val filled = size.width * fraction * progress
                if (filled > 0f) {
                    drawRoundRect(
                        color = colour,
                        size = androidx.compose.ui.geometry.Size(filled, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
                modifier = Modifier.width(34.dp),
            )
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 86.dp, top = 2.dp),
            )
        }
    }
}

// ------------------------------------------------------------------------- legends

/**
 * The chart told in words and figures. This is the part that has to survive a
 * colour-blind reader, a screen reader, and a phone with animations switched off.
 */
@Composable
private fun CompositionLegend(composition: Composition) {
    val dark = isBrandDark()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        composition.parts.forEach { part ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(part.tone, dark)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = part.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = part.count.toString() + "  \u00B7  " + part.percent + "%",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (composition.unmatched > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(ChartTone.UNMATCHED, dark)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Unmatched taps  \u00B7  not on this roster",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = composition.unmatched.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (composition.nothingRead) {
            Note(
                "Nobody has been read yet, so these students are unmarked - not absent. " +
                    "Start a session and tap the cards."
            )
        }
    }
}

@Composable
private fun LineLegend(curve: ArrivalCurve) {
    val dark = isBrandDark()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(ChartTone.ON_TIME, dark)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Cumulative students read",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = curve.readings.toString() + " of " + curve.rosterSize,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Swatch(ChartTone.LATE, dark)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Late mark, " + curve.windowMinutes + " minutes after the start",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MethodLegend(chart: MethodChart) {
    Note(
        "How this session's " + chart.total + " recorded " +
            (if (chart.total == 1) "presence was" else "presences were") +
            " established. The method also travels into the exported sheet."
    )
}

/** One legend chip, sharing the tone and the texture the chart uses. */
@Composable
private fun Swatch(tone: ChartTone, dark: Boolean) {
    val colour = toneColour(tone, dark)
    Canvas(modifier = Modifier.size(12.dp)) {
        drawRoundRect(
            color = colour,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        )
        if (tone == ChartTone.LATE) {
            clipPath(
                Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                        ),
                    )
                },
            ) {
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(
                        color = if (dark) Color.Black else Color.White,
                        start = Offset(x, size.height),
                        end = Offset(x + size.height, 0f),
                        strokeWidth = 1.4.dp.toPx(),
                    )
                    x += 4.dp.toPx()
                }
            }
        }
    }
}

// ------------------------------------------------------------------------- plumbing

/** The palette's own tones: a green for on time, amber for late, greys for the rest. */
private fun toneColour(tone: ChartTone, dark: Boolean): Color = when (tone) {
    ChartTone.ON_TIME -> if (dark) BrandPrimary else LightPrimary
    ChartTone.LATE -> if (dark) BrandAmber else LightAmber
    ChartTone.ABSENT -> if (dark) BrandTextSecondary else LightTextSecondary
    ChartTone.UNMATCHED -> if (dark) BrandOutline else LightOutline
}

/** A one-shot 0 to 1 for the entrance. Nothing depends on it to be readable. */
@Composable
private fun chartProgress(key: Any?): Float {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(420, easing = EmphasizedDecelerate)) }
    return progress.value
}

/** A diagonal hatch inside one band of the ring, so "late" is a texture as well as a colour. */
private fun DrawScope.hatchBand(
    colour: Color,
    startAngle: Float,
    sweep: Float,
    centre: Offset,
    outer: Float,
    stroke: Float,
) {
    val inner = outer - stroke
    val band = Path().apply {
        arcTo(Rect(centre, outer), startAngle, sweep, false)
        arcTo(Rect(centre, inner), startAngle + sweep, -sweep, false)
        close()
    }
    clipPath(band) {
        val pitch = 6.dp.toPx()
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = colour,
                start = Offset(x, 0f),
                end = Offset(x + size.height, size.height),
                strokeWidth = 1.6.dp.toPx(),
            )
            x += pitch
        }
    }
}
