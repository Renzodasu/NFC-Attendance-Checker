package com.nezzar.nfcattendance

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nezzar.nfcattendance.data.AttendanceResolver
import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.RosterImporter
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.Tap
import com.nezzar.nfcattendance.data.XlsxReader
import com.nezzar.nfcattendance.data.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs on a real device/emulator: writes genuine .xlsx files and reads them back
 * with the app's own reader.
 */
@RunWith(AndroidJUnit4::class)
class ReportGenerationTest {

    private val roster = listOf(
        Student("Ana Reyes", "04A1B2C3"),
        Student("Ben Cruz", "04A1B2C4"),
        Student("Cara Lim", "04A1B2C5"),
        Student("Dan Tan", "04A1B2C6"),
        Student("Eva Santos", "04A1B2C7"),
    )

    private fun demoSession(): AttendanceSession = AttendanceSession(
        sessionId = "S-DEMO-0001",
        sectionName = "BSCE-4B",
        startedAtMillis = 1700000000000L,
        roster = roster,
        taps = listOf(Tap("04A1B2C3", 1700000010000L), Tap("04A1B2C5", 1700000020000L)),
    )

    private fun reportsDir(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "reports")
        assertTrue(dir.mkdirs() || dir.exists())
        return dir
    }

    @Test
    fun writesARealXlsxOnTheDevice() {
        val file = File(reportsDir(), "attendance-S-DEMO-0001.xlsx")
        file.outputStream().use { out -> XlsxWriter.write(out, ReportBuilder.sheets(demoSession())) }
        assertTrue("xlsx was not written", file.length() > 0L)

        val book = file.inputStream().use { XlsxReader.read(it) }
        assertEquals(listOf("Absent", "Late", "Present", "Unmatched"), book.sheetNames)
        assertEquals(listOf("Name", "UID", "Date", "Time"), book.sheet("Late")!![1])
        val absent = book.sheet("Absent")!!
        assertEquals("ABSENT - the conclusion (roster minus present)", absent[0][0])
        val headerAt = absent.indexOfFirst { it == listOf("Name") }
        assertEquals(listOf("Ben Cruz", "Dan Tan", "Eva Santos"), absent.drop(headerAt + 1).map { it[0] })
        val present = book.sheet("Present")!!
        // Every scanned card carries the date and the time it was read.
        assertEquals(listOf("Name", "UID", "Date", "Time"), present[1])
        assertEquals("Ana Reyes", present[2][0])
        assertEquals(ReportBuilder.dateText(1700000010000L), present[2][2])
        assertEquals(ReportBuilder.timeText(1700000010000L), present[2][3])
        val unmatchedSheet = book.sheet("Unmatched")!!
        assertEquals(listOf("UID", "Date", "Time"), unmatchedSheet[1])
    }

    @Test
    fun writesTheSectionRosterXlsxOnTheDevice() {
        val section = Section("BSCE-4B", roster, 1_767_225_600_000L)
        val file = File(reportsDir(), "roster-BSCE-4B.xlsx")
        file.outputStream().use { out -> XlsxWriter.write(out, ReportBuilder.rosterSheets(section)) }
        assertTrue("roster xlsx was not written", file.length() > 0L)

        val book = file.inputStream().use { XlsxReader.read(it) }
        assertEquals(listOf("Roster"), book.sheetNames)
        val rows = book.sheet("Roster")!!
        assertEquals(ReportBuilder.LABEL_SECTION_NAME, rows[1][0])
        assertEquals("BSCE-4B", rows[1][1])
        assertEquals(ReportBuilder.LABEL_SUBJECT, rows[2][0])
        assertEquals(ReportBuilder.LABEL_DATE_UPDATED, rows[4][0])
        assertEquals(ReportBuilder.dateUpdatedText(1_767_225_600_000L), rows[4][1])

        // The exported file is importable: that is the whole point of the stamp.
        val parsed = RosterImporter.parse(rows)
        assertEquals("", parsed.error)
        assertEquals("BSCE-4B", parsed.sectionName)
        assertEquals(1_767_225_600_000L, parsed.updatedAt)
        assertEquals(roster.map { it.uid }, parsed.rows.map { it.uid })
    }

    @Test
    fun resolverInsideTheAppAgreesWithTheUnitTest() {
        val resolved = AttendanceResolver.resolve(demoSession().roster, demoSession().taps)
        assertEquals(3, resolved.absent.size)
        assertEquals(listOf("Ben Cruz", "Dan Tan", "Eva Santos"), resolved.absent.map { it.name })
    }
}
