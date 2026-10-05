package com.nezzar.nfcattendance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The polyhedra a section is drawn as, handed out by its place in the list. */
object Solids {
    val NAMES = listOf(
        "Tetrahedron",
        "Cube",
        "Octahedron",
        "Dodecahedron",
        "Icosahedron",
        "Prism",
    )

    fun of(index: Int): String = NAMES[((index % NAMES.size) + NAMES.size) % NAMES.size]
}

/**
 * A wireframe solid, drawn with lines only - no assets, no dependency. Each shape
 * is the classic textbook wireframe so it stays recognisable at badge size.
 */
@Composable
fun SolidGlyph(
    name: String,
    size: Dp,
    colour: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 1.4.dp,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = stroke.toPx()
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f * 0.92f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(colour, Offset(x1, y1), Offset(x2, y2), s)

        fun poly(points: List<Offset>) {
            for (i in points.indices) line(points[i].x, points[i].y, points[(i + 1) % points.size].x, points[(i + 1) % points.size].y)
        }

        fun ngon(cx0: Float, cy0: Float, r0: Float, sides: Int, turn: Float): List<Offset> =
            (0 until sides).map { i ->
                val a = turn + i * (2.0 * Math.PI / sides).toFloat()
                Offset(cx0 + r0 * kotlin.math.cos(a), cy0 + r0 * kotlin.math.sin(a))
            }

        when (name) {
            "Tetrahedron" -> {
                val a = ngon(cx, cy + r * 0.18f, r, 3, (-Math.PI / 2).toFloat())
                poly(a)
                line(cx, cy - r * 0.75f, a[0].x, a[0].y)
                line(cx, cy - r * 0.75f, a[1].x, a[1].y)
                line(cx, cy - r * 0.75f, a[2].x, a[2].y)
            }
            "Cube" -> {
                val d = r * 0.42f
                val front = ngon(cx - d * 0.5f, cy + d * 0.5f, r * 0.62f, 4, (Math.PI / 4).toFloat())
                val back = ngon(cx + d * 0.5f, cy - d * 0.5f, r * 0.62f, 4, (Math.PI / 4).toFloat())
                poly(front)
                poly(back)
                for (i in front.indices) line(front[i].x, front[i].y, back[i].x, back[i].y)
            }
            "Octahedron" -> {
                poly(listOf(Offset(cx, cy - r), Offset(cx + r * 0.8f, cy), Offset(cx, cy + r), Offset(cx - r * 0.8f, cy)))
                drawOval(
                    colour,
                    topLeft = Offset(cx - r * 0.8f, cy - r * 0.34f),
                    size = Size(r * 1.6f, r * 0.68f),
                    style = Stroke(s),
                )
            }
            "Dodecahedron" -> {
                val outer = ngon(cx, cy, r, 5, (-Math.PI / 2).toFloat())
                val inner = ngon(cx, cy, r * 0.62f, 5, (Math.PI / 2).toFloat())
                poly(outer)
                poly(inner)
                for (i in outer.indices) line(outer[i].x, outer[i].y, inner[(i + 4) % 5].x, inner[(i + 4) % 5].y)
            }
            "Icosahedron" -> {
                val ring = ngon(cx, cy, r, 6, 0f)
                poly(ring)
                poly(listOf(ring[0], ring[2], ring[4]))
                poly(listOf(ring[1], ring[3], ring[5]))
            }
            else -> {
                val top = ngon(cx, cy - r * 0.5f, r * 0.72f, 6, 0f)
                val bottom = top.map { Offset(it.x, it.y + r) }
                poly(top)
                poly(bottom)
                for (i in top.indices) line(top[i].x, top[i].y, bottom[i].x, bottom[i].y)
            }
        }
    }
}
