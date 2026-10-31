package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.AttendanceMethod
import com.nezzar.nfcattendance.data.AttendanceProcessor
import com.nezzar.nfcattendance.data.AttendanceResolver
import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.AttendanceStatus
import com.nezzar.nfcattendance.data.Outcome
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.Tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The attendance rules, tested once - because they exist once. A card tap and a
 * presence marked by hand end up in the same processor and answer to the same rules;
 * where it matters the method is asserted separately.
 */
class AttendanceProcessorTest {

    private val start = 1_700_000_000_000L

    private val roster = listOf(
        Student("Ana Reyes", "04A1B2C3"),
        Student("Ben Cruz", "04D4E5F6"),
    )

    private fun session(
        startedAtMillis: Long = start,
        taps: List<Tap> = emptyList(),
        window: Int = 15,
    ) = AttendanceSession(
        sessionId = "S-PROC-1",
        sectionName = "BSCE-4B",
        startedAtMillis = startedAtMillis,
        taps = taps,
        roster = roster,
        lateAfterMinutes = window,
    )

    private fun process(
        identifier: String,
        method: AttendanceMethod = AttendanceMethod.NFC,
        session: AttendanceSession? = session(),
        running: Boolean = true,
        paused: Boolean = false,
        atMillis: Long = start + 60_000L,
    ) = AttendanceProcessor.process(
        rawIdentifier = identifier,
        method = method,
        session = session,
        running = running,
        paused = paused,
        atMillis = atMillis,
        windowMillis = session?.let { AttendanceResolver.windowFor(it, 10L * 60_000L) }
            ?: AttendanceResolver.LATE_AFTER_MILLIS,
    )

    /** A presence marked by hand, for a learner whose card is not involved. */
    private fun processStudent(
        student: Student,
        method: AttendanceMethod = AttendanceMethod.MANUAL,
        session: AttendanceSession? = session(),
        running: Boolean = true,
        paused: Boolean = false,
        atMillis: Long = start + 60_000L,
    ) = AttendanceProcessor.processForStudent(
        student = student,
        method = method,
        session = session,
        running = running,
        paused = paused,
        atMillis = atMillis,
        windowMillis = session?.let { AttendanceResolver.windowFor(it, 10L * 60_000L) }
            ?: AttendanceResolver.LATE_AFTER_MILLIS,
    )

    @Test
    fun aRegisteredStudentIsRecordedWithTheReaderThatSawThem() {
        val nfc = process("04A1B2C3", AttendanceMethod.NFC)
        assertEquals(Outcome.RECORDED, nfc.outcome)
        assertTrue(nfc.records)
        assertEquals("Ana Reyes", nfc.student?.name)
        assertEquals(AttendanceStatus.ON_TIME, nfc.status)
        assertEquals(AttendanceMethod.NFC, nfc.tap().method)

        val qr = process("04A1B2C3", AttendanceMethod.QR)
        assertEquals(Outcome.RECORDED, qr.outcome)
        assertEquals(AttendanceMethod.QR, qr.tap().method)
    }

    /** The architecture in one assertion: the entry point changes, the decision does not. */
    @Test
    fun everyPathProducesTheSameDecisionForTheSameStudent() {
        val ana = roster.first()
        for (method in listOf(AttendanceMethod.NFC, AttendanceMethod.QR, AttendanceMethod.MANUAL)) {
            val byId = process("04A1B2C3", method)
            val byStudent = processStudent(ana, method)
            assertEquals("outcome, " + method, Outcome.RECORDED, byId.outcome)
            assertEquals("outcome, " + method, byId.outcome, byStudent.outcome)
            assertEquals("status, " + method, byId.status, byStudent.status)
            assertEquals("student, " + method, byId.tap().uid, byStudent.tap().uid)
            assertEquals("method, " + method, method, byStudent.tap().method)
        }
    }

    @Test
    fun anIdentifierThatIsNotOnTheRosterIsRecordedAndFlagged() {
        val result = process("04FFFFFF")
        assertEquals(Outcome.UNKNOWN, result.outcome)
        assertTrue("an unknown card is flagged, never dropped", result.records)
        assertNull(result.student)
    }

    @Test
    fun aSecondReadOfTheSameStudentIsADuplicateAndIsNotRecorded() {
        val already = session(taps = listOf(Tap("04A1B2C3", start + 30_000L)))
        assertEquals(Outcome.DUPLICATE, process("04A1B2C3", session = already).outcome)
        assertEquals(Outcome.DUPLICATE, process("04A1B2C3", AttendanceMethod.QR, already).outcome)
        // and a student resolved by hand after already being recorded is a duplicate too
        assertEquals(Outcome.DUPLICATE, processStudent(roster.first(), session = already).outcome)
    }

    @Test
    fun aReadWithNoSessionRunningIsRefusedButAHandCorrectionIsNot() {
        assertEquals(Outcome.NO_SESSION, process("04A1B2C3", session = null).outcome)
        assertEquals(Outcome.NO_SESSION, process("04A1B2C3", running = false).outcome)
        assertFalse(process("04A1B2C3", running = false).records)
        // A correction is not a read: the teacher may still fix a session that ended.
        assertEquals(Outcome.RECORDED, processStudent(roster.first(), running = false).outcome)
        assertEquals(Outcome.NO_SESSION, processStudent(roster.first(), session = null).outcome)
    }

    @Test
    fun aPausedSessionRefusesAReadWithoutEndingIt() {
        assertEquals(Outcome.PAUSED, process("04A1B2C3", paused = true).outcome)
        // Pause switches the readers off; marking presence by hand is still allowed.
        assertEquals(Outcome.RECORDED, processStudent(roster.first(), paused = true).outcome)
    }

    @Test
    fun aMalformedIdentifierIsRefusedBeforeAnyRuleIsApplied() {
        for (junk in listOf("", "   ", "hello", "12", "04A1B2CZ")) {
            val result = process(junk)
            assertEquals("junk: " + junk, Outcome.INVALID, result.outcome)
            assertFalse(result.records)
        }
    }

    @Test
    fun aLateArrivalIsRecordedAsLateByTheSessionsOwnWindow() {
        assertEquals(AttendanceStatus.ON_TIME, process("04A1B2C3", atMillis = start + 15L * 60_000L).status)
        val late = process("04D4E5F6", atMillis = start + 15L * 60_000L + 1L)
        assertEquals(AttendanceStatus.LATE, late.status)
        assertEquals(Outcome.RECORDED, late.outcome)
        assertEquals(AttendanceStatus.LATE, processStudent(roster.first(), atMillis = start + 16L * 60_000L).status)
    }

    @Test
    fun aCorrectionByHandStillWorksAfterTheSessionHasEnded() {
        val corrected = processStudent(
            roster.first(),
            method = AttendanceMethod.MANUAL,
            session = session(),
            running = false,
        )
        assertEquals(Outcome.RECORDED, corrected.outcome)
        assertEquals(AttendanceMethod.MANUAL, corrected.tap().method)
    }
}
