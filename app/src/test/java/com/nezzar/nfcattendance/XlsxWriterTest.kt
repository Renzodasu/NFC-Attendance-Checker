package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.Tap
import com.nezzar.nfcattendance.data.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class XlsxWriterTest {

    private fun session(): AttendanceSession = AttendanceSession(
        sessionId = "S-20261103-100000",
        sectionName = "BSCE-4B",
        startedAtMillis = 1700000000000L,
        roster = listOf(
            Student("Ana Reyes", "04A1B2C3"),
            Student("Ben Cruz", "04A1B2C4"),
            Student("Cara Lim", "04A1B2C5"),
        ),
        taps = listOf(Tap("04A1B2C3", 1700000010000L)),
    )

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, String> {
        val entries = LinkedHashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return entries
    }

    private fun sessionWorkbook(): LinkedHashMap<String, String> {
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(buffer, ReportBuilder.sheets(session()))
        return unzip(buffer.toByteArray())
    }

    private fun rosterSheet(section: Section): String {
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(buffer, ReportBuilder.rosterSheets(section))
        return unzip(buffer.toByteArray())["xl/worksheets/sheet1.xml"]!!
    }

    @Test
    fun producesAValidZipWithTheThreeSheetsInTheRequiredOrder() {
        val entries = sessionWorkbook()

        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet3.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet4.xml"))

        val workbook = entries["xl/workbook.xml"]!!
        val absent = workbook.indexOf("name=\"Absent\"")
        val late = workbook.indexOf("name=\"Late\"")
        val present = workbook.indexOf("name=\"Present\"")
        val unmatched = workbook.indexOf("name=\"Unmatched\"")
        assertTrue(absent >= 0)
        assertTrue(late > absent)
        assertTrue(present > late)
        assertTrue(unmatched > present)
    }

    @Test
    fun absentSheetComesFirstAndCarriesTheAbsentNamesAsTheConclusion() {
        val entries = sessionWorkbook()

        val absentSheet = entries["xl/worksheets/sheet1.xml"]!!
        assertTrue(absentSheet.contains("ABSENT"))
        assertTrue(absentSheet.contains("Ben Cruz"))
        assertTrue(absentSheet.contains("Cara Lim"))
        assertTrue(!absentSheet.contains("Ana Reyes"))

        val presentSheet = entries["xl/worksheets/sheet3.xml"]!!
        assertTrue(presentSheet.contains("Ana Reyes"))
        assertTrue(presentSheet.contains("04A1B2C3"))
        assertTrue(!presentSheet.contains("Ben Cruz"))

        val unmatchedSheet = entries["xl/worksheets/sheet4.xml"]!!
        assertTrue(unmatchedSheet.contains("UNMATCHED"))
    }

    @Test
    fun noStudentNumberColumnOrLabelSurvivesAnywhereInTheWorkbook() {
        val entries = sessionWorkbook()
        for ((name, body) in entries) {
            assertTrue("student_no leaked into " + name, !body.contains("student_no"))
            assertTrue("a student number column leaked into " + name, !body.contains("Student No."))
            assertTrue("a studentNo field leaked into " + name, !body.contains("studentNo"))
        }
        val absentSheet = entries["xl/worksheets/sheet1.xml"]!!
        assertTrue(absentSheet.contains(">Name<"))
    }

    @Test
    fun theSectionRosterSheetCarriesTheSectionNameTheDateAndNameUidRows() {
        val section = Section(
            "BSCE-4B",
            listOf(Student("Ana Reyes", "04A1B2C3"), Student("Ben Cruz", "04D4E5F6")),
            1_767_225_600_000L,
        )
        val sheet = rosterSheet(section)

        assertTrue(sheet.contains("REGISTERED ROSTER - BSCE-4B"))
        assertTrue("the section name must be labelled", sheet.contains(">Section name<"))
        assertTrue("the date must be labelled", sheet.contains(">Date updated<"))
        assertTrue(
            "the date value must be written",
            sheet.contains(ReportBuilder.dateUpdatedText(1_767_225_600_000L)),
        )
        assertTrue(sheet.contains("Ana Reyes"))
        assertTrue(sheet.contains("04A1B2C3"))
        assertTrue(sheet.contains("Ben Cruz"))
        assertTrue(sheet.contains("04D4E5F6"))
        assertTrue("roster sheet must state Name + UID only", sheet.contains(">Name<") && sheet.contains(">UID<"))
        assertTrue(!sheet.contains("Student No."))
    }

    @Test
    fun aRosterWithoutARecordedDateSaysNotRecorded() {
        val sheet = rosterSheet(Section("BSCE-4B", listOf(Student("Ana Reyes", "04A1B2C3")), 0L))
        assertTrue(sheet.contains(">Date updated<"))
        assertTrue(sheet.contains(ReportBuilder.NOT_RECORDED))
    }

    @Test
    fun xmlSpecialCharactersAreEscaped() {
        val session = session().copy(
            roster = listOf(Student("Ana & <Reyes>", "04A1B2C3")),
            taps = emptyList(),
        )
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(buffer, ReportBuilder.sheets(session))
        val sheet = unzip(buffer.toByteArray())["xl/worksheets/sheet1.xml"]!!
        assertTrue(sheet.contains("Ana &amp; &lt;Reyes&gt;"))
        assertTrue(!sheet.contains("Ana & <Reyes>"))
    }

    @Test
    fun theSheetNamesAreExactlyAbsentLatePresentUnmatched() {
        val entries = sessionWorkbook()
        val workbook = entries["xl/workbook.xml"]!!
        val names = Regex("name=\"([^\"]+)\"").findAll(workbook).map { it.groupValues[1] }.toList()
        assertEquals(listOf("Absent", "Late", "Present", "Unmatched"), names)
    }
}
