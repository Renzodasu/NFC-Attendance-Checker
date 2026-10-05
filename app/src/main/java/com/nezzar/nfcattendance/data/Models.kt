package com.nezzar.nfcattendance.data

/**
 * The roster keys on Name + UID only. There is no student number anywhere in
 * the model, the storage format or the reports.
 */
data class Student(
    val name: String,
    val uid: String,
)

/**
 * One named Class Section with its own roster.
 *
 * [updatedAt] is the epoch-millis stamp of the last time this section's ROSTER
 * changed (a student registered, renamed or removed). Renaming the section and
 * exporting it do not move this stamp, so an unchanged roster keeps its date.
 * 0 means "never recorded" (a section restored from a legacy file).
 */
data class Section(
    val name: String = "",
    val students: List<Student> = emptyList(),
    val updatedAt: Long = 0L,
    /**
     * What the class is about - "Surveying", "Hydraulics". Optional, and unique to
     * this section: it travels with the roster file and shows on the section card.
     */
    val subject: String = "",
    /** This section's face from the 52-card deck, e.g. "7♦". Random, then fixed. */
    val card: String = "",
)

data class Tap(
    val uid: String,
    val atMillis: Long,
)

data class AttendanceSession(
    val sessionId: String,
    val sectionName: String,
    val startedAtMillis: Long,
    val taps: List<Tap>,
    val roster: List<Student>,
)

data class PresentStudent(val student: Student, val firstTapMillis: Long)

/**
 * [late] is a subset of "tapped": the card was read more than the session's late
 * window after the start, so the student is counted, but counted as late.
 */
data class ResolvedAttendance(
    val present: List<PresentStudent>,
    val late: List<PresentStudent> = emptyList(),
    val absent: List<Student>,
    val unmatched: List<Tap>,
)
