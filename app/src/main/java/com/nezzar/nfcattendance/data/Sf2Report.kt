package com.nezzar.nfcattendance.data

import java.util.Calendar
import java.util.Locale

/**
 * Builds a filled DepEd School Form 2 out of a month of recorded sessions.
 *
 * The layout is this app's own, not a pixel copy of the DepEd template: the same
 * header facts, one column per school day, the MALE / FEMALE blocks the form has,
 * a per-day total under each block, and the summary a teacher has to fill in by
 * hand on the printed form. Being readable and honest matters more here than
 * matching the template's merged-cell quirks - a teacher can paste these numbers
 * onto the official page.
 *
 * Cell language, printed in the legend:
 *   blank = present, L = late, x = absent, - = no session that day, N = no card yet.
 *
 * A learner with no card is never counted absent: the app cannot know, and a
 * month silently marking them absent would be a lie in the school's records.
 */
object Sf2Report {

    /** One calendar month, as the export's month stepper shows it. */
    data class Month(val year: Int, val month: Int) {
        val label: String get() = monthLabel(year, month)
        val filePart: String get() = String.format(Locale.US, "%04d-%02d", year, month)
    }

    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    fun monthLabel(year: Int, month: Int): String {
        val name = if (month in 1..12) MONTHS[month - 1] else "Month"
        return name + " " + year
    }

    /** The months this class has sessions in, newest first, for the stepper. */
    fun months(sessions: List<AttendanceSession>, sectionName: String): List<Month> {
        val out = linkedMapOf<Int, Month>()
        for (session in sessions) {
            if (!session.sectionName.equals(sectionName, ignoreCase = true)) continue
            if (session.startedAtMillis <= 0L) continue
            val calendar = Calendar.getInstance(Locale.US)
            calendar.timeInMillis = session.startedAtMillis
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            out.putIfAbsent(year * 100 + month, Month(year, month))
        }
        return out.values.sortedWith(compareByDescending<Month> { it.year * 100 + it.month })
    }

    fun build(
        section: Section,
        sessions: List<AttendanceSession>,
        year: Int,
        month: Int,
    ): Sheet {
        val days = schoolDays(year, month)
        val monthSessions = sessions
            .filter { it.sectionName.equals(section.name, ignoreCase = true) }
            .filter { sameMonth(it.startedAtMillis, year, month) }
        val byDay = monthSessions.groupBy { dayOfMonth(it.startedAtMillis) }

        val rows = mutableListOf<MutableList<String>>()
        val styles = mutableMapOf<String, Int>()
        val merges = mutableListOf<String>()
        val heights = mutableMapOf<Int, Double>()

        val dayStart = 2
        val lastDayCol = if (days.isEmpty()) dayStart - 1 else dayStart + days.size - 1
        val absentCol = lastDayCol + 1
        val presentCol = lastDayCol + 2
        val remarksCol = lastDayCol + 3
        val lastCol = remarksCol

        fun put(row: Int, col: Int, value: String, style: Int? = null) {
            while (rows.size <= row) rows += mutableListOf<String>()
            val line = rows[row]
            while (line.size <= col) line += ""
            line[col] = value
            if (style != null) styles[row.toString() + "," + col] = style
        }

        fun merge(range: String) {
            merges += range
        }

        // ---------------------------------------------------------- the header
        put(0, 0, "School Form 2 (SF2) Daily Attendance Report of Learners", Style.TITLE)
        merge("A1:" + colName(lastCol) + "1")
        put(1, 0, "(This replaces Form 1, Form 2 & STS Form 4 - Absenteeism and Dropout Profile)", Style.FOOTNOTE)
        merge("A2:" + colName(lastCol) + "2")
        heights[0] = 26.0
        heights[1] = 13.0

        val header = section.sf2 ?: Sf2Header()
        // Labels sit in B, the wide name column: a label put in A is clipped at five
        // characters, and a form that teachers print has to be readable on paper.
        put(2, 1, "School ID", Style.BOLD)
        put(2, 2, header.schoolId.ifBlank { " " })
        merge("C3:D3")
        put(2, 4, "School Year", Style.BOLD)
        merge("E3:H3")
        put(2, 8, header.schoolYear.ifBlank { " " })
        merge("I3:K3")

        put(3, 1, "Name of School", Style.BOLD)
        put(3, 2, header.schoolName.ifBlank { " " })
        merge("C4:F4")
        put(3, 6, "Grade Level", Style.BOLD)
        merge("G4:K4")
        put(3, 11, gradeLabel(section, header))
        merge("L4:N4")
        put(3, 14, "Section", Style.BOLD)
        merge("O4:R4")
        put(3, 18, sectionLabel(section, header))
        merge("S4:V4")

        put(4, 1, "Report for the Month of", Style.BOLD)
        put(4, 2, monthLabel(year, month))
        merge("C5:F5")

        // ------------------------------------------------------- day headings
        put(6, 0, "No.", Style.BOXED_BOLD_CENTER)
        put(7, 0, "", Style.BOXED)
        merge("A7:A8")

        put(6, 1, "NAME (Last, First, Middle)", Style.BOXED_BOLD_CENTER)
        put(7, 1, "", Style.BOXED)
        merge("B7:B8")

        days.forEachIndexed { index, day ->
            val col = dayStart + index
            put(6, col, day.toString(), Style.BOXED_BOLD_CENTER)
            put(7, col, weekdayLetter(year, month, day), Style.BOXED_CENTER)
        }

        put(6, absentCol, "Total for Month", Style.BOXED_BOLD_CENTER)
        merge(colName(absentCol) + "7:" + colName(presentCol) + "7")
        put(7, absentCol, "ABSENT", Style.BOXED_BOLD_CENTER)
        put(7, presentCol, "PRESENT", Style.BOXED_BOLD_CENTER)

        put(6, remarksCol, "REMARKS", Style.BOXED_BOLD_CENTER)
        put(7, remarksCol, "", Style.BOXED)
        merge(colName(remarksCol) + "7:" + colName(remarksCol) + "8")
        heights[6] = 22.0

        // ------------------------------------------------------- learner blocks
        val male = section.students.filter { it.sex == "M" }
        val female = section.students.filter { it.sex == "F" }
        val unspecified = section.students.filter { it.sex != "M" && it.sex != "F" }

        var row = 8
        val blocks = mutableListOf<Triple<String, String, List<Student>>>()
        blocks += Triple("MALE", "MALE", male)
        blocks += Triple("FEMALE", "FEMALE", female)
        if (unspecified.isNotEmpty()) {
            blocks += Triple("SEX NOT RECORDED", "SEX NOT RECORDED", unspecified)
        }

        for (block in blocks) {
            put(row, 0, block.first, Style.BOLD)
            merge("A" + (row + 1) + ":" + colName(lastCol) + (row + 1))
            row++

            var number = 0
            for (student in block.third) {
                number++
                put(row, 0, number.toString(), Style.BOXED_CENTER)
                put(row, 1, student.name, Style.BOXED)
                var lateCount = 0
                var absentCount = 0
                var presentCount = 0
                days.forEachIndexed { index, day ->
                    val mark = markFor(student, byDay[day])
                    put(row, dayStart + index, mark, Style.BOXED_CENTER)
                    when (mark) {
                        "L" -> {
                            lateCount++
                            presentCount++
                        }
                        "" -> presentCount++
                        "x" -> absentCount++
                    }
                }
                put(row, absentCol, absentCount.toString(), Style.BOXED_CENTER)
                put(row, presentCol, presentCount.toString(), Style.BOXED_CENTER)
                put(row, remarksCol, remarksFor(student, lateCount), Style.BOXED)
                row++
            }

            // The form's own "<=== MALE | TOTAL Per Day ===>" line.
            val label = "<=== " + block.first + " | TOTAL Per Day ===>"
            put(row, 0, label, Style.BOXED_BOLD)
            put(row, 1, "", Style.BOXED_BOLD)
            merge("A" + (row + 1) + ":B" + (row + 1))
            var presentTotal = 0
            var absentTotal = 0
            days.forEachIndexed { index, _ ->
                val col = dayStart + index
                val value = dayPresentFor(block.third, byDay[days[index]])
                put(row, col, value.toString(), Style.BOXED_BOLD_CENTER)
                presentTotal += value
                absentTotal += block.third.count { markFor(it, byDay[days[index]]) == "x" }
            }
            put(row, absentCol, absentTotal.toString(), Style.BOXED_BOLD_CENTER)
            put(row, presentCol, presentTotal.toString(), Style.BOXED_BOLD_CENTER)
            put(row, remarksCol, "", Style.BOXED_BOLD)
            row++
            row++
        }

        // ------------------------------------------------------------ summary
        put(row, 0, "<=== COMBINED | TOTAL Per Day ===>", Style.BOXED_BOLD)
        put(row, 1, "", Style.BOXED_BOLD)
        merge("A" + (row + 1) + ":B" + (row + 1))
        var combinedPresent = 0
        var combinedAbsent = 0
        days.forEachIndexed { index, day ->
            put(row, dayStart + index, dayPresentFor(section.students, byDay[day]).toString(), Style.BOXED_BOLD_CENTER)
            combinedPresent += dayPresentFor(section.students, byDay[day])
            combinedAbsent += section.students.count { markFor(it, byDay[day]) == "x" }
        }
        put(row, absentCol, combinedAbsent.toString(), Style.BOXED_BOLD_CENTER)
        put(row, presentCol, combinedPresent.toString(), Style.BOXED_BOLD_CENTER)
        put(row, remarksCol, "", Style.BOXED_BOLD)
        row += 2

        val enrolment = section.students.size
        val daysWithSession = days.count { byDay[it]?.isNotEmpty() == true }
        val average = if (daysWithSession == 0) 0.0 else combinedPresent.toDouble() / daysWithSession
        val percentage = if (enrolment == 0) 0.0 else average / enrolment * 100.0

        put(row, 1, "Enrolment", Style.BOLD)
        put(row, 2, "MALE " + male.size + "   FEMALE " + female.size + "   TOTAL " + enrolment)
        merge("C" + (row + 1) + ":J" + (row + 1))
        row++
        put(row, 1, "Days of Classes", Style.BOLD)
        put(row, 2, daysWithSession.toString())
        row++
        put(row, 1, "Average Daily Attendance", Style.BOLD)
        put(row, 2, String.format(Locale.US, "%.2f", average))
        merge("C" + (row + 1) + ":D" + (row + 1))
        row++
        put(row, 1, "Percentage of Attendance", Style.BOLD)
        put(row, 2, String.format(Locale.US, "%.2f%%", percentage))
        merge("C" + (row + 1) + ":D" + (row + 1))
        row += 2

        put(row, 0, "blank = present   L = late   x = absent   - = no session that day   N = no card yet", Style.FOOTNOTE)
        merge("A" + (row + 1) + ":" + colName(lastCol) + (row + 1))

        // ------------------------------------------------------------- widths
        val widths = MutableList(lastCol + 1) { 2.0 }
        widths[0] = 5.0
        widths[1] = 34.0
        for (index in days.indices) widths[dayStart + index] = 4.4
        widths[absentCol] = 8.0
        widths[presentCol] = 9.0
        widths[remarksCol] = 24.0

        return Sheet(
            name = "SF2",
            rows = rows,
            widths = widths,
            styles = styles,
            merges = merges,
            rowHeights = heights,
        )
    }

    /**
     * One learner on one day: "" present, "L" late, "x" absent, "-" no session,
     * "N" no card yet. A day with two sessions is present if either saw them, and
     * late only when every tap that day was after its own session's window.
     */
    private fun markFor(student: Student, daySessions: List<AttendanceSession>?): String {
        if (daySessions.isNullOrEmpty()) return "-"
        if (student.uid.isBlank()) return "N"
        val uid = Uid.normalize(student.uid)
        var onTime = false
        var lateSeen = false
        for (session in daySessions) {
            val tap = session.taps.firstOrNull { Uid.normalize(it.uid) == uid } ?: continue
            val status = AttendanceResolver.statusOf(
                session.startedAtMillis,
                tap.atMillis,
                AttendanceResolver.windowFor(session),
            )
            if (status == AttendanceStatus.LATE) lateSeen = true else onTime = true
        }
        return when {
            onTime -> ""
            lateSeen -> "L"
            else -> "x"
        }
    }

    /** How many of [students] were present on a day, for the total line. */
    private fun dayPresentFor(students: List<Student>, daySessions: List<AttendanceSession>?): Int =
        students.count { markFor(it, daySessions) == "" || markFor(it, daySessions) == "L" }

    private fun remarksFor(student: Student, lateCount: Int): String {
        val parts = mutableListOf<String>()
        if (student.uid.isBlank()) parts += "No card yet"
        if (lateCount > 0) parts += lateCount.toString() + " late"
        return parts.joinToString("; ")
    }

    /** Grade from the stored form header, or the part of the class name before the dash. */
    private fun gradeLabel(section: Section, header: Sf2Header): String {
        if (header.gradeLevel.isNotBlank()) return header.gradeLevel
        val dash = section.name.indexOf('-')
        return if (dash > 0) section.name.substring(0, dash).trim() else section.name.trim()
    }

    private fun sectionLabel(section: Section, header: Sf2Header): String {
        if (header.sectionLabel.isNotBlank()) return header.sectionLabel
        return ClassMark.noun(section.name).ifBlank { section.name }
    }

    /** The Mon-Fri days of a month, in order. */
    fun schoolDays(year: Int, month: Int): List<Int> {
        val calendar = Calendar.getInstance(Locale.US)
        calendar.clear()
        calendar.set(year, month - 1, 1)
        val last = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val out = mutableListOf<Int>()
        var day = 1
        while (day <= last) {
            calendar.set(year, month - 1, day)
            val weekDay = calendar.get(Calendar.DAY_OF_WEEK)
            if (weekDay != Calendar.SATURDAY && weekDay != Calendar.SUNDAY) out += day
            day++
        }
        return out
    }

    private fun weekdayLetter(year: Int, month: Int, day: Int): String {
        val calendar = Calendar.getInstance(Locale.US)
        calendar.clear()
        calendar.set(year, month - 1, day)
        return when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "M"
            Calendar.TUESDAY -> "T"
            Calendar.WEDNESDAY -> "W"
            Calendar.THURSDAY -> "TH"
            Calendar.FRIDAY -> "F"
            else -> ""
        }
    }

    private fun sameMonth(millis: Long, year: Int, month: Int): Boolean {
        if (millis <= 0L) return false
        val calendar = Calendar.getInstance(Locale.US)
        calendar.timeInMillis = millis
        return calendar.get(Calendar.YEAR) == year && calendar.get(Calendar.MONTH) + 1 == month
    }

    private fun dayOfMonth(millis: Long): Int {
        val calendar = Calendar.getInstance(Locale.US)
        calendar.timeInMillis = millis
        return calendar.get(Calendar.DAY_OF_MONTH)
    }

    private fun colName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }
}
