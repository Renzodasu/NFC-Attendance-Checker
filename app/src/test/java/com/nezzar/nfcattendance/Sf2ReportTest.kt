package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Sf2Report
import com.nezzar.nfcattendance.data.Sheet
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.Tap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

/** The month sheet: one column per school day, blank present / L late / x absent / - no session / N no card. */
class Sf2ReportTest {

    private val section = Section(
        name = "Grade 5 - Narra",
        students = listOf(
            Student("Ana Cruz", "AAAAAAAA", "F"),
            Student("Ben Reyes", "BBBBBBBB", "M"),
            Student("Cara Lim", "", "F"),
        ),
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(Locale.US)
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, 0)
        return calendar.timeInMillis
    }

    private fun session(
        id: String,
        day: Int,
        taps: List<Tap>,
        name: String = section.name,
        month: Int = 9,
    ): AttendanceSession = AttendanceSession(
        sessionId = id,
        sectionName = name,
        startedAtMillis = at(2026, month, day, 8, 0),
        taps = taps,
        roster = section.students,
        lateAfterMinutes = 15,
    )

    private fun septemberSessions(): List<AttendanceSession> = listOf(
        session(
            "S-1", day = 1,
            taps = listOf(Tap("AAAAAAAA", at(2026, 9, 1, 8, 5)), Tap("BBBBBBBB", at(2026, 9, 1, 8, 40))),
        ),
        session("S-2", day = 2, taps = listOf(Tap("AAAAAAAA", at(2026, 9, 2, 8, 10)))),
    )

    private fun rowOf(sheet: Sheet, name: String): List<String> =
        sheet.rows.first { it.size > 1 && it[1] == name }

    private fun rowStartingWith(sheet: Sheet, label: String): List<String> =
        sheet.rows.first { it.isNotEmpty() && it[0] == label }

    /** The summary block keeps its labels in column B, so its value is in C. */
    private fun summaryValue(sheet: Sheet, label: String): String =
        sheet.rows.first { it.size > 2 && (it[0] == label || it[1] == label) }[2]

    @Test
    fun oneCellPerSchoolDaySaysWhoWasThere() {
        val sheet = Sf2Report.build(section, septemberSessions(), 2026, 9)

        assertEquals("SF2", sheet.name)
        assertEquals("School Form 2 (SF2) Daily Attendance Report of Learners", sheet.rows[0][0])
        assertEquals("1", sheet.rows[6][2])
        assertEquals("T", sheet.rows[7][2])

        val ana = rowOf(sheet, "Ana Cruz")
        assertEquals("", ana[2])
        assertEquals("", ana[3])
        assertEquals("-", ana[4])
        assertEquals("0", ana[24])
        assertEquals("2", ana[25])

        val ben = rowOf(sheet, "Ben Reyes")
        assertEquals("L", ben[2])
        assertEquals("x", ben[3])
        assertEquals("1", ben[24])
        assertEquals("1", ben[25])
        assertEquals("1 late", ben[26])

        val cara = rowOf(sheet, "Cara Lim")
        assertEquals("N", cara[2])
        assertEquals("0", cara[24])
        assertEquals("0", cara[25])
        assertEquals("No card yet", cara[26])
    }

    @Test
    fun dailyTotalsAndTheSummaryCountALateLearnerAsPresent() {
        val sheet = Sf2Report.build(section, septemberSessions(), 2026, 9)

        assertEquals("2", rowStartingWith(sheet, "<=== COMBINED | TOTAL Per Day ===>")[2])
        assertEquals("1", rowStartingWith(sheet, "<=== COMBINED | TOTAL Per Day ===>")[3])
        assertEquals("MALE 1   FEMALE 2   TOTAL 3", summaryValue(sheet, "Enrolment"))
        assertEquals("2", summaryValue(sheet, "Days of Classes"))
        assertEquals("1.50", summaryValue(sheet, "Average Daily Attendance"))
        assertEquals("50.00%", summaryValue(sheet, "Percentage of Attendance"))
    }

    @Test
    fun theSheetCarriesItsOwnLayoutForExcel() {
        val sheet = Sf2Report.build(section, septemberSessions(), 2026, 9)

        assertEquals(27, sheet.rows[6].size)
        assertEquals(27, sheet.widths.size)
        assertEquals(34.0, sheet.widths[1], 0.001)
        assertTrue(sheet.merges.contains("A1:AA1"))
        // The form's labels live in the wide column, never in the five-character
        // "No." column, so nothing is clipped when the sheet is printed.
        assertEquals("School ID", sheet.rows[2][1])
        assertEquals("School Year", sheet.rows[2][4])
        assertEquals("Name of School", sheet.rows[3][1])
        assertEquals("Report for the Month of", sheet.rows[4][1])
        assertEquals("NAME (Last, First, Middle)", sheet.rows[6][1])
        assertTrue(sheet.merges.contains("E3:H3"))
        assertTrue(sheet.merges.contains("S4:V4"))
        assertTrue(sheet.styles.isNotEmpty())
        assertTrue(sheet.rowHeights.containsKey(0))
    }

    @Test
    fun aMonthIsOfferedPerClassAndNewestFirst() {
        val sessions = septemberSessions() + session("S-3", day = 31, taps = emptyList(), month = 8) +
            session("S-4", day = 5, taps = emptyList(), name = "Grade 5 - Sampaguita", month = 10)

        assertEquals(
            listOf(Sf2Report.Month(2026, 9), Sf2Report.Month(2026, 8)),
            Sf2Report.months(sessions, "grade 5 - narra"),
        )
        assertEquals("September 2026", Sf2Report.monthLabel(2026, 9))
        assertEquals("2026-09", Sf2Report.Month(2026, 9).filePart)
    }

    @Test
    fun onlyMondayToFridayGetAColumn() {
        val days = Sf2Report.schoolDays(2026, 9)
        assertEquals(22, days.size)
        assertTrue(days.contains(1))
        assertTrue(days.contains(30))
        assertFalse(days.contains(5))
        assertFalse(days.contains(6))
    }
}
