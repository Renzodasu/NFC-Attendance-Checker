package com.nezzar.nfcattendance.data

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The import rules for a shared roster .xlsx, as plain Kotlin so every branch is
 * unit-testable. Importing MERGES: it matches on UID, never deletes a student
 * and never turns one UID into two rows.
 */
object RosterImporter {

    const val SHEET_ROSTER = "Roster"
    const val HEADER_NAME = "Name"
    const val LABEL_SECTION_NAME = "Section name"
    const val LABEL_SUBJECT = "Subject"
    const val LABEL_DATE_UPDATED = "Date updated"
    const val NOT_RECORDED = "Not recorded"

    private val DATE_FORMATS = listOf(
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )

    /** One usable student row, with the spreadsheet row it came from. */
    data class Row(val rowNumber: Int, val name: String, val uid: String)

    /** A row that was refused, always with its 1-based row number and the reason. */
    data class Skipped(val rowNumber: Int, val reason: String)

    /** What the file itself says, before anything is compared with this phone. */
    data class Parsed(
        val sectionName: String = "",
        val updatedAt: Long = 0L,
        val rows: List<Row> = emptyList(),
        val skipped: List<Skipped> = emptyList(),
        val error: String = "",
        /** The class subject the file carried, if it had one. */
        val subject: String = "",
    )

    /** How the merge will land, plus the section list it produces. */
    data class Plan(
        val targetName: String = "",
        val mergeIntoExisting: Boolean = false,
        val added: List<Row> = emptyList(),
        val renamed: List<Pair<Row, String>> = emptyList(),
        val unchanged: List<Row> = emptyList(),
        val keptLocally: List<Student> = emptyList(),
        val skipped: List<Skipped> = emptyList(),
        val fileUpdatedAt: Long = 0L,
        val resultingUpdatedAt: Long = 0L,
        val sections: List<Section> = emptyList(),
        val error: String = "",
    )

    /** Reads the header block and the student table out of the Roster sheet. */
    fun parse(rows: List<List<String>>): Parsed {
        if (rows.isEmpty()) return Parsed(error = "The Roster sheet is empty.")

        var headerIndex = -1
        var index = 0
        while (index < rows.size) {
            if (cell(rows[index], 0).equals(HEADER_NAME, ignoreCase = true)) {
                headerIndex = index
                break
            }
            index++
        }
        if (headerIndex < 0) {
            return Parsed(error = "The Roster sheet has no Name column, so there is nothing to import.")
        }

        var sectionName = ""
        var updatedAt = 0L
        var subject = ""
        index = 0
        while (index < headerIndex) {
            val label = cell(rows[index], 0)
            val value = cell(rows[index], 1)
            if (label.equals(LABEL_SECTION_NAME, ignoreCase = true) && value.isNotEmpty()) sectionName = value
            if (label.equals(LABEL_SUBJECT, ignoreCase = true) && value.isNotEmpty()) subject = value
            if (label.equals(LABEL_DATE_UPDATED, ignoreCase = true)) updatedAt = parseDate(value)
            index++
        }

        val out = mutableListOf<Row>()
        val skipped = mutableListOf<Skipped>()
        val firstRowOfUid = LinkedHashMap<String, Int>()
        index = headerIndex + 1
        while (index < rows.size) {
            val row = rows[index]
            val rowNumber = index + 1
            val name = cell(row, 0)
            val rawUid = cell(row, 1)
            if (name.isEmpty() && rawUid.isEmpty()) {
                index++
                continue
            }
            if (name.isEmpty()) {
                skipped += Skipped(rowNumber, "the name is blank")
            } else if (rawUid.isEmpty()) {
                skipped += Skipped(rowNumber, "the UID is blank")
            } else if (!Uid.isValid(rawUid)) {
                skipped += Skipped(rowNumber, "the UID is not 8+ hex characters: " + rawUid)
            } else {
                val uid = Uid.normalize(rawUid)
                val seen = firstRowOfUid[uid]
                if (seen != null) {
                    skipped += Skipped(rowNumber, "duplicate UID " + uid + " (first seen on row " + seen + ")")
                } else {
                    firstRowOfUid[uid] = rowNumber
                    out += Row(rowNumber, name, uid)
                }
            }
            index++
        }
        return Parsed(sectionName, updatedAt, out, skipped, subject = subject)
    }

    /** "yyyy-MM-dd HH:mm" (also with seconds, or date only) as epoch millis. 0 when absent. */
    fun parseDate(raw: String): Long {
        val text = raw.trim()
        if (text.isEmpty() || text.equals(NOT_RECORDED, ignoreCase = true)) return 0L
        for (pattern in DATE_FORMATS) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = false
                return format.parse(text)?.time ?: 0L
            } catch (t: Throwable) {
                // try the next accepted shape
            }
        }
        return 0L
    }

    /** Compares the file with this phone and produces the merge, or an error. */
    fun plan(parsed: Parsed, sections: List<Section>, targetName: String): Plan {
        if (parsed.error.isNotEmpty()) return Plan(targetName = targetName, error = parsed.error)
        val name = targetName.trim()
        if (name.isEmpty()) {
            return Plan(targetName = name, skipped = parsed.skipped, error = "Type a section name before importing.")
        }
        if (parsed.rows.isEmpty()) {
            return Plan(
                targetName = name,
                skipped = parsed.skipped,
                error = "This file has no usable student rows, so nothing can be imported.",
            )
        }
        val existing = Sections.find(sections, name)
        val localByUid = LinkedHashMap<String, Student>()
        if (existing != null) {
            for (student in existing.students) localByUid[Uid.normalize(student.uid)] = student
        }

        val added = mutableListOf<Row>()
        val renamed = mutableListOf<Pair<Row, String>>()
        val unchanged = mutableListOf<Row>()
        for (row in parsed.rows) {
            val local = localByUid[row.uid]
            when {
                local == null -> added += row
                local.name == row.name -> unchanged += row
                else -> renamed += row to local.name
            }
        }
        val fileUids = parsed.rows.map { it.uid }.toSet()
        val keptLocally = existing?.students?.filter { Uid.normalize(it.uid) !in fileUids } ?: emptyList()

        // A known UID takes the file's name; a missing UID is appended; a local
        // student the file does not mention is kept where it is.
        val merged = mutableListOf<Student>()
        if (existing != null) {
            for (student in existing.students) {
                val row = parsed.rows.firstOrNull { it.uid == Uid.normalize(student.uid) }
                merged += if (row == null) student else Student(row.name, student.uid)
            }
        }
        for (row in added) merged += Student(row.name, row.uid)

        val resultingUpdatedAt = maxOf(existing?.updatedAt ?: 0L, parsed.updatedAt)
        // A new section adopts the file's subject; merging into one keeps the local subject.
        val result = if (existing == null) {
            sections + Section(name, merged, resultingUpdatedAt, parsed.subject.trim())
        } else {
            sections.map {
                if (it.name == existing.name) it.copy(students = merged, updatedAt = resultingUpdatedAt) else it
            }
        }

        return Plan(
            targetName = name,
            mergeIntoExisting = existing != null,
            added = added,
            renamed = renamed,
            unchanged = unchanged,
            keptLocally = keptLocally,
            skipped = parsed.skipped,
            fileUpdatedAt = parsed.updatedAt,
            resultingUpdatedAt = resultingUpdatedAt,
            sections = result,
        )
    }

    /** The one summary line shown after an import. */
    fun summary(plan: Plan): String {
        if (plan.error.isNotEmpty()) return plan.error
        return "Imported into " + plan.targetName + ": added " + plan.added.size +
            ", names updated " + plan.renamed.size +
            ", unchanged " + plan.unchanged.size +
            ", kept (not in file) " + plan.keptLocally.size +
            ", skipped rows " + plan.skipped.size + "."
    }

    private fun cell(row: List<String>, index: Int): String =
        if (index < row.size) row[index].trim() else ""
}
