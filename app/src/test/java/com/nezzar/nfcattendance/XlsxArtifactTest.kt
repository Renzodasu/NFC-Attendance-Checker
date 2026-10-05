package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.AttendanceResolver
import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.RosterImporter
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.Tap
import com.nezzar.nfcattendance.data.XlsxReader
import com.nezzar.nfcattendance.data.XlsxWriter
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end artifact proof that runs on the JVM: build a synthetic session,
 * resolve it, and write the REAL .xlsx files to disk so they can be unzipped,
 * inspected and opened by an external reader (openpyxl).
 */
class XlsxArtifactTest {

    private val roster = listOf(
        Student("Ana Reyes", "04A1B2C3"),
        Student("Ben Cruz", "04D4E5F6"),
        Student("Cara Lim", "04778899"),
        Student("Dan Tan", "04AABBCC"),
        Student("Eva Santos", "04DDEEFF"),
    )

    private val rosterStampedAt = 1_767_225_600_000L

    private val session = AttendanceSession(
        sessionId = "S-DEMO-0001",
        sectionName = "BSCE-4B",
        startedAtMillis = 1_767_225_600_000L,
        taps = listOf(
            Tap("04A1B2C3", 1_767_225_601_000L),
            Tap("04778899", 1_767_225_602_500L),
            Tap("04FFFFFF", 1_767_225_603_000L),
        ),
        roster = roster,
    )

    @Test
    fun writesRealXlsxArtifactsToDisk() {
        val resolved = AttendanceResolver.resolve(roster, session.taps)
        assertEquals(listOf("Ben Cruz", "Dan Tan", "Eva Santos"), resolved.absent.map { it.name })
        assertEquals(listOf("Ana Reyes", "Cara Lim"), resolved.present.map { it.student.name })
        assertEquals(listOf("04FFFFFF"), resolved.unmatched.map { it.uid })

        val outDir = File("build/nfc-artifacts").apply { mkdirs() }

        val attendance = File(outDir, "attendance-S-DEMO-0001.xlsx")
        attendance.outputStream().use { XlsxWriter.write(it, ReportBuilder.sheets(session)) }
        assertTrue("artifact written: " + attendance.absolutePath, attendance.isFile && attendance.length() > 0)
        println("ARTIFACT=" + attendance.absolutePath + " bytes=" + attendance.length())

        val section = Section("BSCE-4B", roster, rosterStampedAt)
        val sectionRoster = File(outDir, "roster-BSCE-4B.xlsx")
        sectionRoster.outputStream().use { XlsxWriter.write(it, ReportBuilder.rosterSheets(section)) }
        assertTrue("artifact written: " + sectionRoster.absolutePath,
            sectionRoster.isFile && sectionRoster.length() > 0)
        println("ARTIFACT=" + sectionRoster.absolutePath + " bytes=" + sectionRoster.length())

        ZipFile(attendance).use { zip ->
            val names = zip.entries().toList().map { it.name }
            assertTrue(names.contains("[Content_Types].xml"))
            assertTrue(names.contains("xl/workbook.xml"))
            assertEquals(
                listOf(
                    "xl/worksheets/sheet1.xml",
                    "xl/worksheets/sheet2.xml",
                    "xl/worksheets/sheet3.xml",
                    "xl/worksheets/sheet4.xml",
                ),
                names.filter { it.startsWith("xl/worksheets/") }.sorted(),
            )
            val wb = zip.getInputStream(zip.getEntry("xl/workbook.xml")).readBytes().decodeToString()
            val order = listOf("Absent", "Late", "Present", "Unmatched").map { wb.indexOf("name=\"$it\"") }
            assertTrue(
                "sheet order in workbook.xml: " + order,
                order[0] < order[1] && order[1] < order[2] && order[2] < order[3],
            )

            val s1 = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes().decodeToString()
            assertTrue(s1.contains("Ben Cruz") && s1.contains("Eva Santos"))
            assertTrue("absent sheet must not carry the present student", !s1.contains("Ana Reyes"))
            assertTrue("absent sheet must not carry a student number column", !s1.contains("Student No."))
            val s3 = zip.getInputStream(zip.getEntry("xl/worksheets/sheet4.xml")).readBytes().decodeToString()
            assertTrue(s3.contains("04FFFFFF"))
        }

        ZipFile(sectionRoster).use { zip ->
            val wb = zip.getInputStream(zip.getEntry("xl/workbook.xml")).readBytes().decodeToString()
            assertTrue(wb.contains("name=\"Roster\""))
            val sheet = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes().decodeToString()
            assertTrue(sheet.contains("Ana Reyes") && sheet.contains("04A1B2C3"))
            assertTrue(sheet.contains("Eva Santos") && sheet.contains("04DDEEFF"))
            assertTrue(sheet.contains(">Section name<"))
            assertTrue(sheet.contains(">Date updated<"))
        }

        // The same files read back through the app's own reader: the roster
        // export must import cleanly into another phone.
        val book = sectionRoster.inputStream().use { XlsxReader.read(it) }
        assertEquals(listOf("Roster"), book.sheetNames)
        val parsed = RosterImporter.parse(book.sheet("Roster")!!)
        assertEquals("", parsed.error)
        assertEquals("BSCE-4B", parsed.sectionName)
        assertEquals(rosterStampedAt, parsed.updatedAt)
        assertEquals(roster.map { it.uid }, parsed.rows.map { it.uid })
    }
}
