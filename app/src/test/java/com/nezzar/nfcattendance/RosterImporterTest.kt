package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.RosterImporter
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The import rules: what a shared .xlsx says, what gets refused, and exactly how
 * the merge lands. Nothing here deletes a student or duplicates a UID.
 */
class RosterImporterTest {

    private val dateText = "2026-10-03 17:00"

    private fun millis(text: String): Long {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        format.isLenient = false
        return format.parse(text)!!.time
    }

    private fun file(
        name: String = "BSCE-4B",
        date: String = dateText,
        students: List<Pair<String, String>>,
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        rows += listOf("REGISTERED ROSTER - " + name)
        rows += listOf("Section name", name)
        rows += listOf("Registered students", students.size.toString())
        rows += listOf("Date updated", date)
        rows += listOf("")
        rows += listOf("Name", "UID")
        for (student in students) rows += listOf(student.first, student.second)
        return rows
    }

    @Test
    fun readsTheHeaderBlockAndEveryStudentRow() {
        val parsed = RosterImporter.parse(
            file(students = listOf("Ana Reyes" to "04A1B2C3", "Ben Cruz" to "04d4e5f6"))
        )

        assertEquals("", parsed.error)
        assertEquals("BSCE-4B", parsed.sectionName)
        assertEquals(millis(dateText), parsed.updatedAt)
        assertEquals(listOf("Ana Reyes", "Ben Cruz"), parsed.rows.map { it.name })
        assertEquals(listOf("04A1B2C3", "04D4E5F6"), parsed.rows.map { it.uid })
        assertEquals(listOf(7, 8), parsed.rows.map { it.rowNumber })
        assertTrue(parsed.skipped.isEmpty())
    }

    @Test
    fun everyRejectedRowIsReportedWithItsRowNumber() {
        val rows = mutableListOf<List<String>>()
        rows += listOf("REGISTERED ROSTER - BSCE-4B")
        rows += listOf("Section name", "BSCE-4B")
        rows += listOf("Date updated", "Not recorded")
        rows += listOf("Name", "UID")
        rows += listOf("Ana Reyes", "04A1B2C3")
        rows += listOf("", "04D4E5F6")
        rows += listOf("Cara Lim", "")
        rows += listOf("Dan Tan", "04AABBCC")
        rows += listOf("Eva Santos", "not-hex")
        rows += listOf("Ben Cruz", "04A1B2C3")
        rows += listOf("", "")
        rows += listOf("", "")

        val parsed = RosterImporter.parse(rows)

        assertEquals("", parsed.error)
        assertEquals(0L, parsed.updatedAt)
        assertEquals(listOf("Ana Reyes", "Dan Tan"), parsed.rows.map { it.name })
        assertEquals(4, parsed.skipped.size)
        assertEquals(listOf(6, 7, 9, 10), parsed.skipped.map { it.rowNumber })
        assertTrue(parsed.skipped[0].reason.contains("name is blank"))
        assertTrue(parsed.skipped[1].reason.contains("UID is blank"))
        assertTrue(parsed.skipped[2].reason.contains("not 8+ hex"))
        assertTrue(parsed.skipped[3].reason.contains("duplicate UID 04A1B2C3"))
        assertTrue("the first row that used the UID is named",
            parsed.skipped[3].reason.contains("row 5"))
    }

    @Test
    fun aFileWithoutANameColumnIsRefusedAndAnEmptySheetToo() {
        val noHeader = RosterImporter.parse(listOf(listOf("Section name", "BSCE-4B"), listOf("Ana", "04A1B2C3")))
        assertTrue(noHeader.error.isNotEmpty())
        assertTrue(noHeader.error.contains("no Name column"))

        val empty = RosterImporter.parse(emptyList())
        assertTrue(empty.error.isNotEmpty())
    }

    @Test
    fun mergingAddsMissingUidsAndKeepsEveryLocalStudent() {
        val local = listOf(
            Section("BSCE-4B", listOf(Student("Ana Reyes", "04A1B2C3"), Student("Eva Santos", "04DDEEFF")), 1000L),
        )
        val parsed = RosterImporter.parse(
            file(students = listOf("Ana Reyes" to "04A1B2C3", "Ben Cruz" to "04D4E5F6"))
        )

        val plan = RosterImporter.plan(parsed, local, "BSCE-4B")
        assertEquals("", plan.error)
        assertTrue(plan.mergeIntoExisting)
        assertEquals(listOf("Ben Cruz"), plan.added.map { it.name })
        assertTrue(plan.renamed.isEmpty())
        assertEquals(listOf("Ana Reyes"), plan.unchanged.map { it.name })
        assertEquals(listOf("Eva Santos"), plan.keptLocally.map { it.name })
        assertEquals(1, plan.sections.size)
        assertEquals(
            listOf("Ana Reyes", "Eva Santos", "Ben Cruz"),
            plan.sections[0].students.map { it.name },
        )
        assertEquals(3, plan.sections[0].students.map { it.uid }.toSet().size)
    }

    @Test
    fun aKnownUidTakesTheFilesNameAndNeverBecomesASecondRow() {
        val local = listOf(Section("BSCE-4B", listOf(Student("Ana R. Reyes", "04A1B2C3")), 1000L))
        val parsed = RosterImporter.parse(
            file(students = listOf("Ana Reyes" to "04a1b2c3", "Ben Cruz" to "04D4E5F6"))
        )

        val plan = RosterImporter.plan(parsed, local, "BSCE-4B")
        assertEquals(1, plan.renamed.size)
        assertEquals("Ana Reyes", plan.renamed[0].first.name)
        assertEquals("Ana R. Reyes", plan.renamed[0].second)
        assertEquals(1, plan.added.size)
        assertEquals(listOf("Ana Reyes", "Ben Cruz"), plan.sections[0].students.map { it.name })
        assertEquals(2, plan.sections[0].students.size)
    }

    @Test
    fun importingIntoANewSectionCreatesIt() {
        val parsed = RosterImporter.parse(
            file(name = "BSCE-5A", students = listOf("Cara Lim" to "04778899"))
        )
        val plan = RosterImporter.plan(parsed, emptyList(), "BSCE-5A")

        assertEquals("", plan.error)
        assertEquals(false, plan.mergeIntoExisting)
        assertEquals(listOf("BSCE-5A"), plan.sections.map { it.name })
        assertEquals(listOf("Cara Lim"), plan.sections[0].students.map { it.name })
        assertEquals(millis(dateText), plan.sections[0].updatedAt)
    }

    @Test
    fun theLaterOfTheTwoDatesWins() {
        val parsed = RosterImporter.parse(file(students = listOf("Ana Reyes" to "04A1B2C3")))

        val newerLocally = RosterImporter.plan(
            parsed,
            listOf(Section("BSCE-4B", listOf(Student("Ana Reyes", "04A1B2C3")), millis("2026-11-01 09:30"))),
            "BSCE-4B",
        )
        assertEquals(millis("2026-11-01 09:30"), newerLocally.resultingUpdatedAt)

        val newerInTheFile = RosterImporter.plan(
            parsed,
            listOf(Section("BSCE-4B", listOf(Student("Ana Reyes", "04A1B2C3")), millis("2026-01-01 08:00"))),
            "BSCE-4B",
        )
        assertEquals(millis(dateText), newerInTheFile.resultingUpdatedAt)

        val intoANewSection = RosterImporter.plan(parsed, emptyList(), "BSCE-9Z")
        assertEquals("a new section takes the date that travelled with the file",
            millis(dateText), intoANewSection.resultingUpdatedAt)

        val dateless = RosterImporter.parse(
            file(date = ReportBuilder.NOT_RECORDED, students = listOf("Ana Reyes" to "04A1B2C3"))
        )
        assertEquals(0L, dateless.updatedAt)
        assertEquals(0L, RosterImporter.plan(dateless, emptyList(), "BSCE-9Z").resultingUpdatedAt)
    }

    @Test
    fun aBlankTargetNameOrAnUnusableFileIsRefused() {
        val parsed = RosterImporter.parse(file(students = listOf("Ana Reyes" to "04A1B2C3")))
        val blank = RosterImporter.plan(parsed, emptyList(), "   ")
        assertTrue(blank.error.contains("Type a section name"))

        val allBad = RosterImporter.parse(
            listOf(listOf("Name", "UID"), listOf("Ana Reyes", "nope"), listOf("", "04A1B2C3"))
        )
        assertEquals(2, allBad.skipped.size)
        assertTrue(RosterImporter.plan(allBad, emptyList(), "BSCE-4B").error.contains("no usable student rows"))

        val broken = RosterImporter.Parsed(error = "The Roster sheet is empty.")
        assertEquals("The Roster sheet is empty.", RosterImporter.plan(broken, emptyList(), "X").error)
    }

    @Test
    fun theSummaryLineCountsEveryOutcome() {
        val local = listOf(
            Section(
                "BSCE-4B",
                listOf(
                    Student("Ana R. Reyes", "04A1B2C3"),
                    Student("Dan Tan", "04AABBCC"),
                    Student("Eva Santos", "04DDEEFF"),
                ),
                1000L,
            ),
        )
        val rows = mutableListOf<List<String>>()
        rows += listOf("Section name", "BSCE-4B")
        rows += listOf("Date updated", dateText)
        rows += listOf("Name", "UID")
        rows += listOf("Ana Reyes", "04A1B2C3")
        rows += listOf("Ben Cruz", "04D4E5F6")
        rows += listOf("Dan Tan", "04AABBCC")
        rows += listOf("Nobody", "zzz")

        val plan = RosterImporter.plan(RosterImporter.parse(rows), local, "BSCE-4B")
        assertEquals(
            "Imported into BSCE-4B: added 1, names updated 1, unchanged 1, kept (not in file) 1, skipped rows 1.",
            RosterImporter.summary(plan),
        )
    }
}
