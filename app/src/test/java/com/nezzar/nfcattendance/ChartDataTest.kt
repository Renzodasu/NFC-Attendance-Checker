package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.AttendanceMethod
import com.nezzar.nfcattendance.data.AttendanceResolver
import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.ChartData
import com.nezzar.nfcattendance.data.ChartTone
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.Tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the three charts, tested without a screen: the awkward cases
 * are the ones worth having here, because they are the ones a chart gets wrong.
 */
class ChartDataTest {

    private val start = 1_700_000_000_000L

    private val roster = listOf(
        Student("Ana Reyes", "04A1B2C3"),
        Student("Ben Cruz", "04D4E5F6"),
        Student("Cara Lim", "04778899"),
        Student("Dan Tan", "04AABBCC"),
    )

    private fun session(
        taps: List<Tap>,
        students: List<Student> = roster,
        window: Int = 15,
    ) = AttendanceSession(
        sessionId = "S-CHART-1",
        sectionName = "BSCE-4B",
        startedAtMillis = start,
        taps = taps,
        roster = students,
        lateAfterMinutes = window,
    )

    private fun resolved(taps: List<Tap>, students: List<Student> = roster, window: Int = 15) =
        AttendanceResolver.resolve(
            roster = students,
            taps = taps,
            startedAtMillis = start,
            lateAfterMillis = window * 60_000L,
        )

    @Test
    fun percentagesAlwaysAddUpToOneHundred() {
        // Every roster size, and every split of it, must land on exactly 100 - the
        // classic chart bug is three roundings that total 99 or 101.
        for (size in 1..50) {
            for (onTime in 0..size) {
                for (late in 0..(size - onTime)) {
                    val absent = size - onTime - late
                    val parts = ChartData.percentages(listOf(onTime, late, absent), size)
                    assertEquals("size " + size + " split " + onTime + "/" + late + "/" + absent, 100, parts.sum())
                    assertTrue("no negative share", parts.all { it >= 0 })
                }
            }
        }
    }

    @Test
    fun anEmptyRosterIsNotDividedByZero() {
        val empty = ChartData.composition(resolved(emptyList(), students = emptyList()))
        assertEquals(0, empty.rosterSize)
        assertEquals(3, empty.parts.size)
        assertTrue("no NaN or negative shares", empty.parts.all { it.percent == 0 && it.count == 0 })
        assertTrue(empty.nothingRead)
    }

    @Test
    fun nobodyReadYetIsNotTheSameAsAbsent() {
        val composition = ChartData.composition(resolved(emptyList()))
        assertEquals(4, composition.rosterSize)
        assertEquals(4, composition.parts.first { it.tone == ChartTone.ABSENT }.count)
        assertTrue("nothing has been read, so this is unmarked, not absent", composition.nothingRead)
        assertEquals(0, composition.recorded)
    }

    /** The conclusion list is roster-based, so the pie must be too. */
    @Test
    fun unmatchedTapsAreNeverASlice() {
        val taps = listOf(
            Tap("04A1B2C3", start + 60_000L),
            Tap("DEADBEEF", start + 90_000L),
            Tap("04FFFFFF", start + 120_000L),
        )
        val composition = ChartData.composition(resolved(taps))
        assertEquals(3, composition.parts.size)
        assertEquals("slices must sum to the roster", 4, composition.parts.sumOf { it.count })
        assertEquals(2, composition.unmatched)
        assertEquals(1, composition.recorded)
    }

    @Test
    fun aTapExactlyOnTheWindowIsOnTimeAndOneMillisLaterIsLate() {
        val window = 15 * 60_000L
        val inside = resolved(listOf(Tap("04A1B2C3", start + window)))
        assertEquals(1, inside.present.size)
        assertEquals(0, inside.late.size)

        val outside = resolved(listOf(Tap("04D4E5F6", start + window + 1L)))
        assertEquals(0, outside.present.size)
        assertEquals(1, outside.late.size)
    }

    @Test
    fun theArrivalCurveStartsAtZeroAndEndsAtEveryoneRecorded() {
        val taps = listOf(
            Tap("04A1B2C3", start + 60_000L),
            Tap("04D4E5F6", start + 240_000L),
            Tap("04778899", start + 16L * 60_000L),
        )
        val res = resolved(taps)
        val curve = ChartData.arrivals(session(taps), res)

        assertEquals(0, curve.points.first().minute)
        assertEquals(0, curve.points.first().cumulative)
        assertEquals(3, curve.points.last().cumulative)
        assertEquals(res.present.size + res.late.size, curve.points.last().cumulative)
        assertEquals("15 minutes of window", 15, curve.windowMinutes)
        assertEquals(16, curve.totalMinutes)
        assertTrue(curve.enough)
        // monotonic, never going backwards
        curve.points.zipWithNext().forEach { (a, b) ->
            assertTrue("time moves forward", b.minute >= a.minute)
            assertTrue("the count never drops", b.cumulative >= a.cumulative)
        }
    }

    @Test
    fun aCurveNeedsTwoReadingsToBeALine() {
        assertFalse(ChartData.arrivals(session(emptyList()), resolved(emptyList())).enough)
        assertEquals(0, ChartData.arrivals(session(emptyList()), resolved(emptyList())).points.size)
        val one = listOf(Tap("04A1B2C3", start + 60_000L))
        assertFalse("one reading is a dot, not a trend", ChartData.arrivals(session(one), resolved(one)).enough)
        val two = one + Tap("04D4E5F6", start + 120_000L)
        assertTrue(ChartData.arrivals(session(two), resolved(two)).enough)
    }

    @Test
    fun aFourHourSessionKeepsItsOwnAxis() {
        val taps = listOf(
            Tap("04A1B2C3", start + 60_000L),
            Tap("04D4E5F6", start + 240L * 60_000L),
        )
        val curve = ChartData.arrivals(session(taps), resolved(taps))
        assertEquals(240, curve.totalMinutes)
        assertEquals("the window is the session's own, not today's setting", 15, curve.windowMinutes)
    }

    @Test
    fun methodBarsMatchHowThePresenceWasRecorded() {
        val taps = listOf(
            Tap("04A1B2C3", start + 60_000L, AttendanceMethod.NFC),
            Tap("04D4E5F6", start + 120_000L, AttendanceMethod.QR),
            Tap("04778899", start + 180_000L, AttendanceMethod.MANUAL),
            Tap("DEADBEEF", start + 200_000L),
        )
        val res = resolved(taps)
        val chart = ChartData.methods(res)

        assertEquals(1, chart.bars.first { it.label == "NFC" }.count)
        assertEquals(1, chart.bars.first { it.label == "QR" }.count)
        assertEquals(1, chart.bars.first { it.label == "Manual" }.count)
        assertEquals(3, chart.total)
        assertEquals(1, chart.unmatched)
        assertFalse(chart.empty)
        assertEquals(100, chart.bars.sumOf { it.percent })
    }

    @Test
    fun aSessionWithNothingRecordedHasAnEmptyMethodChart() {
        val chart = ChartData.methods(resolved(emptyList()))
        assertTrue(chart.empty)
        assertEquals(0, chart.total)
        assertEquals(3, chart.bars.size)
        assertTrue(chart.bars.all { it.count == 0 && it.percent == 0 })
    }

    @Test
    fun aSingleStudentRosterStillComposes() {
        val one = listOf(Student("Ana Reyes", "04A1B2C3"))
        val taps = listOf(Tap("04A1B2C3", start + 60_000L))
        val composition = ChartData.composition(resolved(taps, students = one))
        assertEquals(1, composition.rosterSize)
        assertEquals(1, composition.parts.first { it.tone == ChartTone.ON_TIME }.count)
        assertEquals(100, composition.parts.first { it.tone == ChartTone.ON_TIME }.percent)
        assertFalse(composition.nothingRead)
    }

    @Test
    fun everyoneOnTimeIsAWholeGreenRing() {
        val taps = roster.mapIndexed { index, student ->
            Tap(student.uid, start + (index + 1) * 60_000L)
        }
        val composition = ChartData.composition(resolved(taps))
        assertEquals(4, composition.parts.first { it.tone == ChartTone.ON_TIME }.count)
        assertEquals(100, composition.parts.first { it.tone == ChartTone.ON_TIME }.percent)
        assertEquals("late and absent are both empty", 2, composition.parts.count { it.count == 0 })
    }
}
