package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Sections
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.XlsxReader
import com.nezzar.nfcattendance.data.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The acceptance rule for the roster date: export the same unchanged section
 * twice and both files carry the identical date string, because exporting never
 * moves the stamp.
 */
class ExportStampTest {

    private val stamp = 1_767_225_600_000L

    /** Exactly what the Export button does, then read the file back. */
    private fun exported(section: Section): List<List<String>> {
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(buffer, ReportBuilder.rosterSheets(section))
        val book = XlsxReader.read(ByteArrayInputStream(buffer.toByteArray()))
        return book.sheet(ReportBuilder.SHEET_ROSTER)!!
    }

    private fun value(rows: List<List<String>>, label: String): String =
        rows.first { it.isNotEmpty() && it[0] == label }[1]

    private fun expected(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date(millis))

    @Test
    fun theSameUnchangedSectionExportedTwiceCarriesTheSameDateString() {
        val section = Sections.create(emptyList(), "BSCE-4B", stamp).sections[0]
        val registered = Sections.register(section.let { listOf(it) }, "BSCE-4B", "04A1B2C3", "Ana Reyes", stamp)
        val live = registered.sections[0]

        val first = exported(live)
        val second = exported(live)

        assertEquals(expected(stamp), value(first, ReportBuilder.LABEL_DATE_UPDATED))
        assertEquals(
            "exporting must not move the date",
            value(first, ReportBuilder.LABEL_DATE_UPDATED),
            value(second, ReportBuilder.LABEL_DATE_UPDATED),
        )
        assertEquals("the stamp itself is untouched", stamp, live.updatedAt)
        assertEquals("BSCE-4B", value(first, ReportBuilder.LABEL_SECTION_NAME))
        assertEquals("1", value(first, ReportBuilder.LABEL_REGISTERED_STUDENTS))
        val header = first.first { it.isNotEmpty() && it[0] == "Name" }
        assertEquals(listOf("Name", "UID"), header)
    }

    @Test
    fun registeringAStudentMovesTheDate() {
        val created = Sections.create(emptyList(), "BSCE-4B", stamp).sections[0]
        val before = exported(created)

        val later = stamp + 90_000L
        val registered = Sections.register(listOf(created), "BSCE-4B", "04A1B2C3", "Ana Reyes", later)
        val after = exported(registered.sections[0])

        assertEquals(expected(stamp), value(before, ReportBuilder.LABEL_DATE_UPDATED))
        assertEquals(expected(later), value(after, ReportBuilder.LABEL_DATE_UPDATED))
        assertTrue(
            "a real roster change must be visible in the file",
            value(before, ReportBuilder.LABEL_DATE_UPDATED) != value(after, ReportBuilder.LABEL_DATE_UPDATED),
        )
    }

    @Test
    fun aSectionThatNeverRecordedADateSaysSoInTheFile() {
        val legacy = Section("BSCE-4B", listOf(Student("Ana Reyes", "04A1B2C3")), 0L)
        val rows = exported(legacy)
        assertEquals(ReportBuilder.NOT_RECORDED, value(rows, ReportBuilder.LABEL_DATE_UPDATED))
    }

    @Test
    fun theRosterExportCarriesNameAndUidOnly() {
        val section = Section(
            "BSCE-4B",
            listOf(Student("Ana Reyes", "04A1B2C3"), Student("Ben Cruz", "04D4E5F6")),
            stamp,
        )
        val rows = exported(section)
        val flat = rows.joinToString("|") { it.joinToString(",") }

        assertTrue(flat.contains("Name,UID"))
        assertTrue(flat.contains("Ana Reyes,04A1B2C3"))
        assertTrue(flat.contains("Ben Cruz,04D4E5F6"))
        assertTrue("no student number column", !flat.contains("Student No."))
        assertTrue("no student_no field", !flat.contains("student_no"))
        assertTrue("no studentNo field", !flat.contains("studentNo"))
    }
}
