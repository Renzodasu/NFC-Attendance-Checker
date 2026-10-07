package com.nezzar.nfcattendance.data

/**
 * How a presence reached the app. One value per attendance row, so a report can
 * always say whether a student was scanned, and by which reader.
 */
enum class AttendanceMethod(val label: String) {
    NFC("NFC"),
    QR("QR"),
    MANUAL("Manual"),
}

/** Present or late, judged by the session's own window - never by today's setting. */
enum class AttendanceStatus { ON_TIME, LATE }

/** What happened when an identifier arrived. */
enum class Outcome {
    /** On the roster and recorded, on time. */
    RECORDED,

    /** Already recorded in this session. Nothing is appended. */
    DUPLICATE,

    /** A valid identifier that is not on this roster: recorded, flagged, never dropped. */
    UNKNOWN,

    /** Nothing is running (a manual correction still may be). */
    NO_SESSION,

    /** The session is paused, so the readers are off. */
    PAUSED,

    /** Empty, or not an identifier at all. */
    INVALID,
}

/** The processor's answer, and everything the UI needs to say about it. */
data class ProcessedAttendance(
    val outcome: Outcome,
    val identifier: String,
    val method: AttendanceMethod,
    val atMillis: Long,
    val student: Student? = null,
    val status: AttendanceStatus? = null,
    val message: String = "",
) {
    /** True when the caller must append this read to the session's log. */
    val records: Boolean
        get() = outcome == Outcome.RECORDED || outcome == Outcome.UNKNOWN

    /** What the caller appends. Only meaningful when [records] is true. */
    fun tap(): Tap = Tap(identifier, atMillis, method)
}

/**
 * THE attendance decision, and the only one. Every reader ends up here:
 *
 *   NFC UID   -> [process]            -> the same rules
 *   QR by UID -> [process]            -> the same rules
 *   QR by hand-> [processForStudent]  -> the same rules
 *
 * Registered, unknown, duplicate, session running, paused, on time or late and the
 * recording method are decided here once, for every path. Pure: no Android, no
 * clock, so each branch is unit-testable on the JVM. The late rule itself lives in
 * [AttendanceResolver.statusOf] so this and the report's resolver cannot disagree.
 */
object AttendanceProcessor {

    /** A card UID, or a QR code that already carried one. */
    fun process(
        rawIdentifier: String,
        method: AttendanceMethod,
        session: AttendanceSession?,
        running: Boolean,
        paused: Boolean,
        atMillis: Long,
        windowMillis: Long = AttendanceResolver.LATE_AFTER_MILLIS,
    ): ProcessedAttendance {
        val id = Uid.normalize(rawIdentifier)
        if (!Uid.isValid(id)) {
            return ProcessedAttendance(
                outcome = Outcome.INVALID,
                identifier = id,
                method = method,
                atMillis = atMillis,
                message = if (id.isEmpty()) {
                    "Nothing was read - no identifier in that."
                } else {
                    "That is not a student ID: " + id + ". A card UID is 8 or more hexadecimal characters."
                },
            )
        }

        val stopped = refusalFor(session, running, paused, id, method, atMillis)
        if (stopped != null) return stopped

        val student = session!!.roster.firstOrNull { Uid.normalize(it.uid) == id }
        return decide(id, student, method, session, atMillis, windowMillis)
    }

    /**
     * A presence the teacher resolved by hand, because the code on the ID could not
     * identify the student by itself (it carries a student number, which this app
     * does not read). The name was confirmed on screen and the card UID verified;
     * from here the rules are the ones a card tap obeys, and the method is recorded
     * as QR so the report can tell the difference.
     */
    fun processForStudent(
        student: Student,
        method: AttendanceMethod,
        session: AttendanceSession?,
        running: Boolean,
        paused: Boolean,
        atMillis: Long,
        windowMillis: Long = AttendanceResolver.LATE_AFTER_MILLIS,
    ): ProcessedAttendance {
        val id = Uid.normalize(student.uid)
        val stopped = refusalFor(session, running, paused, id, method, atMillis)
        if (stopped != null) return stopped
        return decide(id, student, method, session!!, atMillis, windowMillis)
    }

    /** Session, pause and running rules - the same for a card, a code or a correction. */
    private fun refusalFor(
        session: AttendanceSession?,
        running: Boolean,
        paused: Boolean,
        id: String,
        method: AttendanceMethod,
        atMillis: Long,
    ): ProcessedAttendance? {
        if (session == null) {
            return ProcessedAttendance(
                outcome = Outcome.NO_SESSION,
                identifier = id,
                method = method,
                atMillis = atMillis,
                message = "No session running - " + id + " was not recorded.",
            )
        }
        // A correction is not a read: a teacher may still fix an absent student on a
        // session that has already ended, but a reader must not write to one.
        if (method != AttendanceMethod.MANUAL) {
            if (!running) {
                return ProcessedAttendance(
                    outcome = Outcome.NO_SESSION,
                    identifier = id,
                    method = method,
                    atMillis = atMillis,
                    message = "No session running - " + id + " was not recorded.",
                )
            }
            if (paused) {
                return ProcessedAttendance(
                    outcome = Outcome.PAUSED,
                    identifier = id,
                    method = method,
                    atMillis = atMillis,
                    message = "Paused - " + id + " was not recorded. Press Resume when the class is ready.",
                )
            }
        }
        return null
    }

    /** Duplicate, unknown or recorded - identical however the presence arrived. */
    private fun decide(
        id: String,
        student: Student?,
        method: AttendanceMethod,
        session: AttendanceSession,
        atMillis: Long,
        windowMillis: Long,
    ): ProcessedAttendance {
        val already = session.taps
            .filter { Uid.normalize(it.uid) == id }
            .minByOrNull { it.atMillis }

        if (already != null) {
            return ProcessedAttendance(
                outcome = Outcome.DUPLICATE,
                identifier = id,
                method = method,
                atMillis = atMillis,
                student = student,
                status = AttendanceResolver.statusOf(session.startedAtMillis, already.atMillis, windowMillis),
                message = "Already recorded: " + (student?.name ?: id) + " at " +
                    ReportBuilder.timeText(already.atMillis) + " (" + already.method.label + "). Not counted twice.",
            )
        }

        val status = AttendanceResolver.statusOf(session.startedAtMillis, atMillis, windowMillis)
        if (student == null) {
            return ProcessedAttendance(
                outcome = Outcome.UNKNOWN,
                identifier = id,
                method = method,
                atMillis = atMillis,
                status = status,
                message = "NOT ON ROSTER at " + ReportBuilder.timeText(atMillis) + ": " + id +
                    " (" + method.label + ") - flagged, not dropped.",
            )
        }
        return ProcessedAttendance(
            outcome = Outcome.RECORDED,
            identifier = id,
            method = method,
            atMillis = atMillis,
            student = student,
            status = status,
            message = if (status == AttendanceStatus.LATE) {
                "Late: " + student.name + " (" + id + ") at " + ReportBuilder.timeText(atMillis) +
                    " - " + method.label + "."
            } else {
                "Present: " + student.name + " (" + id + ") at " + ReportBuilder.timeText(atMillis) +
                    " - " + method.label + "."
            },
        )
    }
}
