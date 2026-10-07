package com.nezzar.nfcattendance.data

/**
 * The three lenses a session can be looked at through. One at a time - the point of
 * the switch is that each one answers a different question, not that there are three
 * ways to see the same number.
 */
enum class ChartKind(val label: String) {
    PIE("Pie"),
    LINE("Line"),
    BAR("Bar"),
}

/** What a part of a chart means, so the UI can colour and texture it honestly. */
enum class ChartTone { ON_TIME, LATE, ABSENT, UNMATCHED }

/** One labelled quantity: a slice, or a bar. */
data class ChartPart(
    val label: String,
    val count: Int,
    val percent: Int,
    val tone: ChartTone,
)

/**
 * The attendance itself: on time, late, absent, over the roster.
 *
 * Unmatched taps are deliberately NOT a slice. They are cards that matched nobody on
 * this roster, so they are not students, and putting them in would make the slices
 * sum past the roster size and contradict the exported sheet. They are counted here
 * separately, for a footnote and for the bar chart.
 */
data class Composition(
    val parts: List<ChartPart>,
    val rosterSize: Int,
    val unmatched: Int,
    val recorded: Int,
) {
    /** Nobody has been read yet: the roster is unmarked, which is not the same as absent. */
    val nothingRead: Boolean get() = recorded == 0
}

/** One point of the arrival curve, in minutes since the session started. */
data class ArrivalPoint(val minute: Int, val cumulative: Int)

/**
 * How the room filled: cumulative recorded students against elapsed time, with the
 * session's own late boundary carried along so the chart can mark it.
 */
data class ArrivalCurve(
    val points: List<ArrivalPoint>,
    val rosterSize: Int,
    val windowMinutes: Int,
    val totalMinutes: Int,
    val readings: Int,
) {
    /** A line needs at least two readings, or it is a dot pretending to be a trend. */
    val enough: Boolean get() = readings >= 2
}

/** How this session's presence was actually recorded. */
data class MethodChart(
    val bars: List<ChartPart>,
    val unmatched: Int,
    val total: Int,
) {
    val empty: Boolean get() = total == 0
}

/**
 * Turns one session into the numbers the three charts draw. Pure - no Android, no
 * clock, no drawing - so every case below is unit-testable, including the awkward
 * ones: nothing read yet, nobody late, only unmatched taps, a single student.
 */
object ChartData {

    /**
     * Percentages that add up to exactly 100, by largest remainder. Rounding each
     * share on its own is how a chart ends up claiming 99% or 101%.
     */
    fun percentages(counts: List<Int>, total: Int): List<Int> {
        if (total <= 0) return counts.map { 0 }
        val exact = counts.map { it * 100.0 / total }
        val out = exact.map { kotlin.math.floor(it).toInt() }.toMutableList()
        var left = 100 - out.sum()
        val byRemainder = exact.indices.sortedByDescending { exact[it] - out[it] }
        var i = 0
        while (left > 0 && byRemainder.isNotEmpty()) {
            out[byRemainder[i % byRemainder.size]] += 1
            left -= 1
            i += 1
        }
        return out
    }

    /** On time / late / absent, over the roster. Never includes unmatched taps. */
    fun composition(resolved: ResolvedAttendance): Composition {
        val onTime = resolved.present.size
        val late = resolved.late.size
        val absent = resolved.absent.size
        val total = onTime + late + absent
        val percent = percentages(listOf(onTime, late, absent), total)
        return Composition(
            parts = listOf(
                ChartPart("On time", onTime, percent[0], ChartTone.ON_TIME),
                ChartPart("Late", late, percent[1], ChartTone.LATE),
                ChartPart("Absent", absent, percent[2], ChartTone.ABSENT),
            ),
            rosterSize = total,
            unmatched = resolved.unmatched.size,
            recorded = onTime + late,
        )
    }

    /**
     * Cumulative recorded students against minutes since the start. The curve begins
     * at (0, 0): before the first card was read, nobody had been recorded.
     */
    fun arrivals(session: AttendanceSession, resolved: ResolvedAttendance): ArrivalCurve {
        val window = if (session.lateAfterMinutes > 0) {
            session.lateAfterMinutes
        } else {
            AttendanceResolver.DEFAULT_LATE_AFTER_MINUTES
        }
        val reads = (resolved.present + resolved.late)
            .map { it.firstTapMillis }
            .sorted()

        if (reads.isEmpty()) {
            return ArrivalCurve(
                points = emptyList(),
                rosterSize = session.roster.size,
                windowMinutes = window,
                totalMinutes = 0,
                readings = 0,
            )
        }

        val points = mutableListOf(ArrivalPoint(minute = 0, cumulative = 0))
        reads.forEachIndexed { index, at ->
            val minute = ((at - session.startedAtMillis) / 60_000L).toInt().coerceAtLeast(0)
            points += ArrivalPoint(minute = minute, cumulative = index + 1)
        }
        return ArrivalCurve(
            points = points,
            rosterSize = session.roster.size,
            windowMinutes = window,
            // The axis has to reach the last reading, and never be zero-width.
            totalMinutes = points.last().minute.coerceAtLeast(1),
            readings = reads.size,
        )
    }

    /** NFC, QR and Manual, plus the unmatched taps set apart - they are not students. */
    fun methods(resolved: ResolvedAttendance): MethodChart {
        val bars = listOf(
            ChartPart("NFC", resolved.countOf(AttendanceMethod.NFC), 0, ChartTone.ON_TIME),
            ChartPart("QR", resolved.countOf(AttendanceMethod.QR), 0, ChartTone.LATE),
            ChartPart("Manual", resolved.countOf(AttendanceMethod.MANUAL), 0, ChartTone.ABSENT),
        )
        val total = bars.sumOf { it.count }
        val percent = percentages(bars.map { it.count }, total)
        return MethodChart(
            bars = bars.mapIndexed { index, bar -> bar.copy(percent = percent[index]) },
            unmatched = resolved.unmatched.size,
            total = total,
        )
    }
}
