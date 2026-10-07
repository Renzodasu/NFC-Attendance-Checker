package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.AttendanceResolver
import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.Tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The roster keys on Name + UID only - there is no student number in the model. */
class AttendanceResolverTest {

    private val roster = listOf(
        Student("Ana Reyes", "04A1B2C3"),
        Student("Ben Cruz", "04A1B2C4"),
        Student("Cara Lim", "04A1B2C5"),
        Student("Dan Tan", "04A1B2C6"),
        Student("Eva Santos", "04A1B2C7"),
    )

    /**
     * The bug this guards: a session judged by today's setting instead of the one it
     * was started with, so moving the slider rewrites a report that already happened.
     */
    @Test
    fun aSessionIsJudgedByTheWindowItWasStartedWith() {
        val start = 1_700_000_000_000L
        val taps = listOf(Tap("04A1B2C3", start + 11L * 60_000L))
        val one = listOf(Student("Ana Reyes", "04A1B2C3"))

        // Started when the window was 15 minutes: eleven minutes in is on time.
        val fifteen = AttendanceSession("S-1", "BSCE-4B", start, taps, one, lateAfterMinutes = 15)
        val asStarted = AttendanceResolver.resolve(
            roster = one,
            taps = fifteen.taps,
            startedAtMillis = fifteen.startedAtMillis,
            lateAfterMillis = AttendanceResolver.windowFor(fifteen, 10L * 60_000L),
        )
        assertEquals(1, asStarted.present.size)
        assertEquals(0, asStarted.late.size)

        // A session written before snapshots existed has no window of its own, so it
        // falls back to the setting - and that is the documented behaviour, not a bug.
        val legacy = AttendanceSession("S-2", "BSCE-4B", start, taps, one)
        val fallback = AttendanceResolver.resolve(
            roster = one,
            taps = legacy.taps,
            startedAtMillis = legacy.startedAtMillis,
            lateAfterMillis = AttendanceResolver.windowFor(legacy, 10L * 60_000L),
        )
        assertEquals(1, fallback.late.size)
    }

    @Test
    fun fiveNameRosterWithTwoTapsYieldsExactlyThreeAbsentNames() {
        val taps = listOf(
            Tap("04A1B2C3", 1000L),
            Tap("04A1B2C5", 2000L),
        )
        val result = AttendanceResolver.resolve(roster, taps)

        assertEquals(2, result.present.size)
        assertEquals(3, result.absent.size)
        assertEquals(listOf("Ben Cruz", "Dan Tan", "Eva Santos"), result.absent.map { it.name })
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun reTapLaterInTheSameSessionKeepsTheFirstTapTime() {
        val taps = listOf(Tap("04A1B2C3", 5000L), Tap("04A1B2C3", 9000L))
        val result = AttendanceResolver.resolve(roster, taps)

        assertEquals(1, result.present.size)
        assertEquals(5000L, result.present[0].firstTapMillis)
    }

    @Test
    fun unknownUidIsFlaggedAndNotDropped() {
        val taps = listOf(Tap("DEADBEEF", 1000L), Tap("04A1B2C3", 2000L))
        val result = AttendanceResolver.resolve(roster, taps)

        assertEquals(1, result.unmatched.size)
        assertEquals("DEADBEEF", result.unmatched[0].uid)
        assertEquals(1, result.present.size)
        assertEquals(4, result.absent.size)
    }

    @Test
    fun noTapsMeansEverybodyIsAbsent() {
        val result = AttendanceResolver.resolve(roster, emptyList())
        assertEquals(0, result.present.size)
        assertEquals(5, result.absent.size)
        assertTrue(result.unmatched.isEmpty())
    }

    @Test
    fun duplicateTapsOfTheSameUnknownUidCollapseToOneUnmatchedEntry() {
        val taps = listOf(Tap("DEADBEEF", 1000L), Tap("deadbeef", 2000L))
        val result = AttendanceResolver.resolve(roster, taps)
        assertEquals(1, result.unmatched.size)
        assertEquals(1000L, result.unmatched[0].atMillis)
    }

    @Test
    fun aTapAfterTheLateWindowIsCountedAsLateNotPresent() {
        val start = 1_700_000_000_000L
        val taps = listOf(
            Tap("04A1B2C3", start + 60_000L),              // one minute in - on time
            Tap("04A1B2C4", start + 16L * 60_000L),        // sixteen minutes in - late
        )
        val result = AttendanceResolver.resolve(roster, taps, start)

        assertEquals(listOf("Ana Reyes"), result.present.map { it.student.name })
        assertEquals(listOf("Ben Cruz"), result.late.map { it.student.name })
        assertEquals(3, result.absent.size)
    }

    @Test
    fun exactlyFifteenMinutesInIsStillOnTime() {
        val start = 1_700_000_000_000L
        val result = AttendanceResolver.resolve(
            roster = roster,
            taps = listOf(Tap("04A1B2C3", start + 15L * 60_000L)),
            startedAtMillis = start,
        )
        assertEquals(1, result.present.size)
        assertTrue(result.late.isEmpty())
    }

    @Test
    fun aCustomWindowMovesTheBoundary() {
        val start = 1_700_000_000_000L
        val tap = listOf(Tap("04A1B2C3", start + 11L * 60_000L))
        assertEquals(1, AttendanceResolver.resolve(roster, tap, start).present.size)

        val strict = AttendanceResolver.resolve(roster, tap, start, lateAfterMillis = 10L * 60_000L)
        assertTrue(strict.present.isEmpty())
        assertEquals(1, strict.late.size)
    }

    @Test
    fun aUidReadWithStrayColonsStillMatchesTheRoster() {
        val taps = listOf(Tap("04:a1:b2:c3", 1000L))
        val result = AttendanceResolver.resolve(roster, taps)
        assertEquals(listOf("Ana Reyes"), result.present.map { it.student.name })
    }
}
