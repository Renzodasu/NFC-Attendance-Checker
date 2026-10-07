package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.AttendanceMethod
import com.nezzar.nfcattendance.data.AttendanceProcessor
import com.nezzar.nfcattendance.data.AttendanceResolver
import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.AttendanceStatus
import com.nezzar.nfcattendance.data.Outcome
import com.nezzar.nfcattendance.data.QrCode
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.Tap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The attendance rules, tested once - because they exist once. A card tap, a QR code
 * that carries a card UID, and a QR code the teacher had to resolve by hand all end
 * up in the same processor and answer to the same rules; where it matters the method
 * is asserted separately.
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

    /** The QR fallback: the teacher confirmed the name, the UID was verified on screen. */
    private fun processStudent(
        student: Student,
        method: AttendanceMethod = AttendanceMethod.QR,
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

    /** The architecture in one assertion: the reader changes, the decision does not. */
    @Test
    fun everyReaderProducesTheSameDecisionForTheSameStudent() {
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
    fun aReadWithNoSessionRunningIsRefused() {
        assertEquals(Outcome.NO_SESSION, process("04A1B2C3", session = null).outcome)
        assertEquals(Outcome.NO_SESSION, process("04A1B2C3", running = false).outcome)
        assertFalse(process("04A1B2C3", running = false).records)
        // the QR fallback obeys it as well
        assertEquals(Outcome.NO_SESSION, processStudent(roster.first(), running = false).outcome)
    }

    @Test
    fun aPausedSessionRefusesAReadWithoutEndingIt() {
        assertEquals(Outcome.PAUSED, process("04A1B2C3", paused = true).outcome)
        assertEquals(Outcome.PAUSED, processStudent(roster.first(), paused = true).outcome)
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

    // ------------------------------------------------------- what a scanned code means

    @Test
    fun aCodeThisAppPrintedResolvesWithoutAskingAnybody() {
        assertEquals("presencia:04A1B2C3", QrCode.encode("04a1b2c3"))
        val reading = QrCode.read(QrCode.encode("04a1b2c3"), roster)
        assertTrue(reading is QrCode.Reading.ByUid)
        assertEquals("04A1B2C3", (reading as QrCode.Reading.ByUid).uid)
    }

    /**
     * The decision that matters for a school ID: its code carries a student number,
     * which this app does not read, keep or show. Only the NAME is used.
     */
    @Test
    fun aCodeCarryingAStudentNumberMatchesNobodyAndAsksTheTeacher() {
        for (payload in listOf("2021-00123", "STUDENT-2021-00123", "12345678", "SN:00123")) {
            assertEquals("payload: " + payload, QrCode.Reading.NeedsChoice, QrCode.read(payload, roster))
        }
    }

    @Test
    fun aBareCardUidIsNotTrustedBecauseItLooksLikeAStudentNumber() {
        // 04A1B2C3 is a perfectly valid card UID, but so is an eight-digit student
        // number. Without the scheme the app asks rather than guesses.
        assertEquals(QrCode.Reading.NeedsChoice, QrCode.read("04A1B2C3", roster))
    }

    @Test
    fun aCodeThatCarriesANamePointsAtThatStudent() {
        val reading = QrCode.read("Ana Reyes", roster)
        assertTrue(reading is QrCode.Reading.Named)
        val candidates = (reading as QrCode.Reading.Named).candidates
        assertEquals(1, candidates.size)
        assertEquals("04A1B2C3", candidates.first().uid)

        // case, spacing and punctuation are all tolerated
        for (variant in listOf("ANA REYES", "  ana   reyes  ", "Ana Reyes.", "ana reyes\n")) {
            val v = QrCode.read(variant, roster)
            assertTrue("variant: " + variant, v is QrCode.Reading.Named)
        }
    }

    @Test
    fun aNameBuriedInALongerPayloadIsStillFound() {
        val reading = QrCode.read("https://school.example/id?name=Ana%20Reyes&sn=2021-00123", roster)
        assertTrue(reading is QrCode.Reading.Named)
        assertEquals("04A1B2C3", (reading as QrCode.Reading.Named).candidates.first().uid)
    }

    @Test
    fun twoStudentsWithTheSameNameBothComeBackSoTheUidCanDecide() {
        val twins = roster + Student("Ana Reyes", "04ZZZZZZ")
        val reading = QrCode.read("Ana Reyes", twins)
        assertTrue(reading is QrCode.Reading.Named)
        assertEquals(2, (reading as QrCode.Reading.Named).candidates.size)
    }

    @Test
    fun aShortNameIsNotMatchedInsideALongerWord() {
        val one = listOf(Student("Li", "04A1B2C3"))
        assertEquals(QrCode.Reading.NeedsChoice, QrCode.read("William Cruz", one))
        val exact = QrCode.read("Li", one)
        assertTrue(exact is QrCode.Reading.Named)
    }

    /**
     * The generator and the reader are one contract: the code the app draws on a
     * student's page must resolve back to that student when a camera reads it. This
     * runs the real encoder and the real decoder on the JVM - no camera needed.
     */
    @Test
    fun theCodeTheAppPrintsReadsBackAsTheSameStudent() {
        val printed = QrCode.encode("04A1B2C3")
        val matrix = QRCodeWriter().encode(printed, BarcodeFormat.QR_CODE, 256, 256)
        val width = matrix.width
        val luminance = ByteArray(width * width)
        for (y in 0 until width) {
            for (x in 0 until width) {
                luminance[y * width + x] = if (matrix.get(x, y)) 0 else 255.toByte()
            }
        }
        val source = PlanarYUVLuminanceSource(luminance, width, width, 0, 0, width, width, false)
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(printed, decoded.text)

        val reading = QrCode.read(decoded.text, roster)
        assertTrue(reading is QrCode.Reading.ByUid)
        assertEquals("04A1B2C3", (reading as QrCode.Reading.ByUid).uid)
    }

    @Test
    fun anEmptyScanIsEmpty() {
        assertEquals(QrCode.Reading.Empty, QrCode.read("", roster))
        assertEquals(QrCode.Reading.Empty, QrCode.read("   ", roster))
    }
}
