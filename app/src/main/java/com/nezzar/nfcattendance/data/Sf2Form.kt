package com.nezzar.nfcattendance.data

import java.util.Calendar
import java.util.Locale

/**
 * Reads a DepEd School Form 2 (SF2) - the daily attendance report a class adviser
 * keeps - just far enough to take the class out of it: the school details in the
 * header and the learners' names, split into the form's own MALE and FEMALE
 * blocks.
 *
 * It deliberately does NOT try to read the day columns. The form's print layout
 * puts a school day on one or two columns depending on the month, and a teacher's
 * reason for importing is the roster, not last month's marks: those marks are
 * what this app produces, not what it consumes.
 *
 * The cards are not in the form - it keys on names, not UIDs - so every learner
 * arrives with a blank [Student.uid], which means "no card yet". Binding a card
 * later is a separate, explicit act on the Register screen.
 */
object Sf2Form {

    /** How many rows from the top can hold the labelled header block. */
    private const val HEADER_ROWS = 14

    /** How many columns right of a label its value may sit. */
    private const val VALUE_REACH = 8

    /**
     * The sheet name of a school form, or null when this workbook is something
     * else. Matched on the two shapes real files use: the template's own
     * "school_form_2_ver2014.2.1.1" and a sheet someone renamed to "SF2".
     */
    fun detect(sheetNames: List<String>): String? = sheetNames.firstOrNull { raw ->
        val lower = raw.trim().lowercase(Locale.US)
        lower.startsWith("school_form_2") || lower == "sf2" || lower.startsWith("sf2 ") ||
            lower.startsWith("sf2-") || lower.startsWith("sf2_")
    }

    data class Parsed(
        val schoolId: String = "",
        val schoolName: String = "",
        val schoolYear: String = "",
        /** The form's own words, e.g. "September, 2026". */
        val month: String = "",
        /** 1-12, or 0 when the month could not be read. */
        val monthNumber: Int = 0,
        /** The four-digit year on the form, or 0. */
        val year: Int = 0,
        val gradeLevel: String = "",
        val sectionLabel: String = "",
        val students: List<Student> = emptyList(),
        /** Human-readable reasons rows were left out, for the preview card. */
        val skipped: List<String> = emptyList(),
        val error: String = "",
    ) {
        /** "Grade 5 - NARRA", or whatever the form actually said. */
        val className: String
            get() = when {
                gradeLevel.isNotBlank() && sectionLabel.isNotBlank() -> gradeLevel + " - " + sectionLabel
                gradeLevel.isNotBlank() -> gradeLevel
                sectionLabel.isNotBlank() -> sectionLabel
                else -> "School form class"
            }

        /** The part of the form a class keeps, so the export can print it again. */
        fun header(): Sf2Header = Sf2Header(
            schoolId = schoolId,
            schoolName = schoolName,
            schoolYear = schoolYear,
            gradeLevel = gradeLevel,
            sectionLabel = sectionLabel,
        )
    }

    fun parse(rows: List<List<String>>): Parsed {
        if (rows.isEmpty()) return Parsed(error = "That school form is empty.")

        val schoolId = value(rows, "School ID")
        val schoolName = value(rows, "Name of School")
        val schoolYear = value(rows, "School Year")
        val gradeLevel = value(rows, "Grade Level")
        val sectionLabel = value(rows, "Section")
        val month = value(rows, "Report for the Month of")
        val monthNumber = monthNumber(month)
        val year = yearOf(month)

        val students = mutableListOf<Student>()
        val skipped = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var inFemaleBlock = false
        var index = 0
        while (index < rows.size) {
            val row = rows[index]
            index++
            val joined = row.joinToString(" ").uppercase(Locale.US)
            if (joined.contains("===")) {
                // The form prints one "<=== MALE | TOTAL Per Day ===>" row after the
                // boys and one after the girls. Everything after the first is female.
                inFemaleBlock = true
                continue
            }
            // The No. cell is a merge of A:B on this template, and a file re-saved by
            // another program can keep the number in either half of that merge: the
            // first girl on the real form has her "1" in column B. Read both.
            val number = (row.getOrNull(0) ?: "").trim()
                .ifEmpty { (row.getOrNull(1) ?: "").trim() }
            if (number.isEmpty() || number.any { !it.isDigit() }) continue
            val rawName = nameOf(row) ?: continue
            if (rawName.isEmpty()) {
                skipped += "row " + (index) + ": the name is blank"
                continue
            }
            val key = rawName.uppercase(Locale.US)
            if (!seen.add(key)) {
                skipped += rawName + " appears twice"
                continue
            }
            students += Student(
                name = rawName,
                uid = "",
                sex = if (inFemaleBlock) "F" else "M",
            )
        }

        if (students.isEmpty()) {
            return Parsed(
                schoolId = schoolId,
                schoolName = schoolName,
                schoolYear = schoolYear,
                month = month,
                monthNumber = monthNumber,
                year = year,
                gradeLevel = gradeLevel,
                sectionLabel = sectionLabel,
                error = "That school form has no learner names in it.",
            )
        }

        return Parsed(
            schoolId = schoolId,
            schoolName = schoolName,
            schoolYear = schoolYear,
            month = month,
            monthNumber = monthNumber,
            year = year,
            gradeLevel = gradeLevel,
            sectionLabel = sectionLabel,
            students = students,
            skipped = skipped,
        )
    }

    /** The value printed to the right of [label], or "". */
    private fun value(rows: List<List<String>>, label: String): String {
        val wanted = label.uppercase(Locale.US)
        val top = minOf(rows.size, HEADER_ROWS)
        var r = 0
        while (r < top) {
            val row = rows[r]
            var c = 0
            while (c < row.size) {
                val cell = cell(row[c])
                if (cell.uppercase(Locale.US) == wanted) {
                    var next = c + 1
                    val limit = minOf(row.size, c + 1 + VALUE_REACH)
                    while (next < limit) {
                        val found = cell(row[next])
                        if (found.isNotEmpty()) return found
                        next++
                    }
                }
                c++
            }
            r++
        }
        return ""
    }

    /**
     * The learner's name from the merged NAME cell. The template merges C:E, so
     * the text is in the leftmost of those; if a file was re-saved with the text
     * nudged right, the first non-blank of the three still finds it.
     */
    private fun nameOf(row: List<String>): String? {
        if (row.size <= 2) return null
        val cells = mutableListOf<String>()
        var c = 2
        val limit = minOf(row.size, 6)
        while (c < limit) {
            val part = cell(row[c])
            if (part.isNotEmpty()) cells += part
            c++
        }
        if (cells.isEmpty()) return ""
        val name = cells.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        if (name.none { it.isLetter() }) return ""
        return name
    }

    private fun cell(raw: String): String =
        raw.replace('\u00A0', ' ').trim()

    /** "September, 2026" -> 9. */
    private fun monthNumber(raw: String): Int {
        val lower = raw.lowercase(Locale.US)
        val names = listOf(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
        )
        names.forEachIndexed { i, name ->
            if (lower.contains(name)) return i + 1
        }
        val short = listOf(
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec",
        )
        short.forEachIndexed { i, name ->
            if (lower.contains(name + " ")) return i + 1
        }
        return 0
    }

    /** "September, 2026" -> 2026. */
    private fun yearOf(raw: String): Int {
        val match = Regex("(19|20)\\d{2}").find(raw) ?: return 0
        return match.value.toIntOrNull() ?: 0
    }

    /** True when [year]/[month] name a real calendar month. */
    fun isValidMonth(year: Int, month: Int): Boolean =
        year in 1900..2200 && month in 1..12

    /** First and last day-of-month for [year]/[month]. */
    fun daysInMonth(year: Int, month: Int): Int {
        val calendar = Calendar.getInstance(Locale.US)
        calendar.clear()
        calendar.set(year, month - 1, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}
