package com.nezzar.nfcattendance.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns a finished session into the three worksheets. Order is mandatory:
 * Absent first (it is the conclusion), then Present, then Unmatched. There is
 * no student-number column anywhere.
 *
 * The section-roster export is one Roster sheet carrying the section name and
 * the date the roster was last updated, so a shared file keeps its own date.
 */
object ReportBuilder {

    const val SHEET_ABSENT = "Absent"
    const val SHEET_LATE = "Late"
    const val SHEET_PRESENT = "Present"
    const val SHEET_UNMATCHED = "Unmatched"
    const val SHEET_ROSTER = "Roster"

    const val LABEL_SECTION_NAME = "Section name"
    const val LABEL_SUBJECT = "Subject"
    const val LABEL_DATE_UPDATED = "Date updated"
    const val LABEL_REGISTERED_STUDENTS = "Registered students"
    const val NOT_RECORDED = "Not recorded"

    /**
     * "4 October 2026, 3:16 PM" - the month in words and a civilian clock. These
     * stamps are read by people on a phone and in the sheet, so they are written
     * the way they are said; the session id stays machine-shaped because it names
     * the file.
     */
    private const val DATE_PATTERN = "d MMMM yyyy, h:mm a"

    /** Date and time are separate columns in the sheets, so each reads on its own. */
    private const val DATE_ONLY = "d MMMM yyyy"
    private const val TIME_ONLY = "h:mm:ss a"

    fun dateUpdatedText(millis: Long): String {
        if (millis <= 0L) return NOT_RECORDED
        return SimpleDateFormat(DATE_PATTERN, Locale.US).format(Date(millis))
    }

    /** "4 October 2026" */
    fun dateText(millis: Long): String = SimpleDateFormat(DATE_ONLY, Locale.US).format(Date(millis))

    /** "3:16:45 PM" - when a card was read. */
    fun timeText(millis: Long): String = SimpleDateFormat(TIME_ONLY, Locale.US).format(Date(millis))

    /** "4 October 2026, 3:16:45 PM" - the full stamp, for a session header. */
    fun stampText(millis: Long): String =
        SimpleDateFormat("$DATE_ONLY, $TIME_ONLY", Locale.US).format(Date(millis))

    fun sheets(
        session: AttendanceSession,
        lateAfterMillis: Long = AttendanceResolver.LATE_AFTER_MILLIS,
    ): List<Sheet> {
        val resolved = AttendanceResolver.resolve(
            roster = session.roster,
            taps = session.taps,
            startedAtMillis = session.startedAtMillis,
            lateAfterMillis = lateAfterMillis,
        )
        val windowMinutes = lateAfterMillis / 60_000L

        val absent = mutableListOf<List<String>>()
        absent += listOf("ABSENT - the conclusion (roster minus present)")
        absent += listOf("Section", session.sectionName)
        absent += listOf("Session id", session.sessionId)
        absent += listOf("Session date", dateText(session.startedAtMillis))
        absent += listOf("Session started", stampText(session.startedAtMillis))
        absent += listOf("Roster size", session.roster.size.toString())
        absent += listOf("Present (on time)", resolved.present.size.toString())
        absent += listOf("Late (after " + windowMinutes + " min)", resolved.late.size.toString())
        absent += listOf("Absent", resolved.absent.size.toString())
        absent += listOf("Unmatched taps", resolved.unmatched.size.toString())
        absent += listOf("")
        absent += listOf("Name")
        for (student in resolved.absent) absent += listOf(student.name)

        val late = mutableListOf<List<String>>()
        late += listOf("LATE - card read more than " + windowMinutes + " minute(s) after the start")
        late += listOf("Name", "UID", "Date", "Time")
        for (item in resolved.late) {
            late += listOf(
                item.student.name,
                item.student.uid,
                dateText(item.firstTapMillis),
                timeText(item.firstTapMillis),
            )
        }

        val present = mutableListOf<List<String>>()
        present += listOf("PRESENT (on time) - with the date and time each card was scanned")
        present += listOf("Name", "UID", "Date", "Time")
        for (item in resolved.present) {
            present += listOf(
                item.student.name,
                item.student.uid,
                dateText(item.firstTapMillis),
                timeText(item.firstTapMillis),
            )
        }

        val unmatched = mutableListOf<List<String>>()
        unmatched += listOf("UNMATCHED TAPS - UID not on the roster (flagged, not dropped)")
        unmatched += listOf("UID", "Date", "Time")
        for (tap in resolved.unmatched) {
            unmatched += listOf(tap.uid, dateText(tap.atMillis), timeText(tap.atMillis))
        }

        return listOf(
            Sheet(SHEET_ABSENT, absent),
            Sheet(SHEET_LATE, late),
            Sheet(SHEET_PRESENT, present),
            Sheet(SHEET_UNMATCHED, unmatched),
        )
    }

    /**
     * The selected section's registered roster: Name, UID, plus the header block
     * that lets another phone recognise the file on import.
     */
    fun rosterSheets(section: Section): List<Sheet> {
        val rows = mutableListOf<List<String>>()
        rows += listOf("REGISTERED ROSTER - " + section.name)
        rows += listOf(LABEL_SECTION_NAME, section.name)
        rows += listOf(LABEL_SUBJECT, section.subject)
        rows += listOf(LABEL_REGISTERED_STUDENTS, section.students.size.toString())
        rows += listOf(LABEL_DATE_UPDATED, dateUpdatedText(section.updatedAt))
        rows += listOf("")
        rows += listOf("Name", "UID")
        for (student in section.students) rows += listOf(student.name, student.uid)
        return listOf(Sheet(SHEET_ROSTER, rows))
    }
}
