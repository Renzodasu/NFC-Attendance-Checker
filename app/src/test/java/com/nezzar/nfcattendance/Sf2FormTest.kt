package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.Sf2Form
import com.nezzar.nfcattendance.data.Sf2Header
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading a DepEd SF2 school form: the header block, then the learners in its two sex blocks. */
class Sf2FormTest {

    private fun line(vararg cells: Pair<Int, String>): List<String> {
        val row = MutableList(cells.maxOf { it.first } + 1) { "" }
        for ((column, value) in cells) row[column] = value
        return row
    }

    private fun learners(): List<List<String>> = listOf(
        line(0 to "1", 2 to "Doe, John A."),
        line(0 to "2", 2 to "Roe, Richard B."),
        line(2 to "<=== MALE | TOTAL Per Day ===>"),
        line(0 to "1", 2 to "Sample, Ana P."),
        line(0 to "2", 2 to "Sample, Ana P."),
        line(0 to "3", 2 to "   "),
    )

    private fun form(learnerRows: List<List<String>> = learners()): List<List<String>> = listOf(
        listOf("School Form 2 (SF2) Daily Attendance Report of Learners"),
        listOf("(This replaces Form 1, Form 2 & STS Form 4 - Absenteeism and Dropout Profile)"),
        line(
            0 to "School ID", 5 to "123456",
            9 to "School Year", 12 to "2026 - 2027",
            18 to "Report for the Month of", 26 to "September, 2026",
        ),
        line(
            0 to "Name of School", 5 to "Sample ES",
            18 to "Grade Level", 26 to "Grade 5",
            34 to "Section", 38 to "NARRA",
        ),
    ) + learnerRows

    @Test
    fun readsTheHeaderAndTheLearnersInTheFormsOwnBlocks() {
        val parsed = Sf2Form.parse(form())

        assertEquals("", parsed.error)
        assertEquals("123456", parsed.schoolId)
        assertEquals("Sample ES", parsed.schoolName)
        assertEquals("2026 - 2027", parsed.schoolYear)
        assertEquals("Grade 5", parsed.gradeLevel)
        assertEquals("NARRA", parsed.sectionLabel)
        assertEquals("September, 2026", parsed.month)
        assertEquals(9, parsed.monthNumber)
        assertEquals(2026, parsed.year)
        assertEquals("Grade 5 - NARRA", parsed.className)
        assertEquals(
            Sf2Header("123456", "Sample ES", "2026 - 2027", "Grade 5", "NARRA"),
            parsed.header(),
        )

        assertEquals(listOf("Doe, John A.", "Roe, Richard B.", "Sample, Ana P."), parsed.students.map { it.name })
        assertEquals(listOf("M", "M", "F"), parsed.students.map { it.sex })
        assertTrue("a form binds no cards", parsed.students.all { it.uid.isEmpty() })

        assertEquals(2, parsed.skipped.size)
        assertTrue(parsed.skipped[0].endsWith("Sample, Ana P. appears twice"))
        assertTrue(parsed.skipped[1].endsWith("the name is blank"))
    }

    @Test
    fun detectsTheTemplateByItsSheetNameAndByItsShorthand() {
        assertEquals("school_form_2_ver2014.2.1.1", Sf2Form.detect(listOf("Sheet1", "school_form_2_ver2014.2.1.1")))
        assertEquals("SF2", Sf2Form.detect(listOf("SF2")))
        assertEquals("sf2-september-2026", Sf2Form.detect(listOf("sf2-september-2026")))
        assertNull(Sf2Form.detect(listOf("Roster", "Sheet1")))
    }

    @Test
    fun anEmptySheetSaysSo() {
        assertEquals("That school form is empty.", Sf2Form.parse(emptyList()).error)
    }

    @Test
    fun aFormWithoutLearnerNamesIsRefusedButKeepsItsHeader() {
        val parsed = Sf2Form.parse(form(learnerRows = emptyList()))

        assertEquals("That school form has no learner names in it.", parsed.error)
        assertEquals("123456", parsed.schoolId)
        assertTrue(parsed.students.isEmpty())
    }
}
