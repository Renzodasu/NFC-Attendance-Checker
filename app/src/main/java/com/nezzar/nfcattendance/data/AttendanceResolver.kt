package com.nezzar.nfcattendance.data

/**
 * Pure attendance resolver: absent = roster minus everyone who tapped.
 * No Android imports, so it is fully unit-testable on the JVM.
 *
 * Arrival rule: a card read more than [lateAfterMillis] after the session started
 * is LATE, not present. That window is the whole reason the session can be paused
 * and left open for the rest of the class - a student who walks in at minute 20
 * still gets counted, and counted honestly.
 *
 * A session with no start time (startedAtMillis <= 0) treats every tap as on time,
 * which is what the older callers and unit tests rely on.
 *
 * The first tap per UID wins, and the tap's [TapSource] rides along into the
 * result, so a hand-made correction is never reported as a scanned card.
 */
object AttendanceResolver {

    /** Fifteen minutes after the start, by default. */
    const val DEFAULT_LATE_AFTER_MINUTES = 15
    const val LATE_AFTER_MILLIS = DEFAULT_LATE_AFTER_MINUTES * 60L * 1000L

    /**
     * The window a session is judged by: the one it was STARTED with. The fallback
     * only applies to a session saved before snapshots existed (lateAfterMinutes 0),
     * so changing the setting today can never re-classify a session that already ran.
     */
    fun windowFor(session: AttendanceSession, fallbackMillis: Long = LATE_AFTER_MILLIS): Long =
        if (session.lateAfterMinutes > 0) session.lateAfterMinutes.toLong() * 60_000L else fallbackMillis

    /**
     * The one place "late" is decided, so the processor that accepts a read and the
     * resolver that reports it can never disagree about when late begins.
     */
    fun statusOf(startedAtMillis: Long, atMillis: Long, windowMillis: Long = LATE_AFTER_MILLIS): AttendanceStatus =
        if (startedAtMillis > 0L && atMillis - startedAtMillis > windowMillis) {
            AttendanceStatus.LATE
        } else {
            AttendanceStatus.ON_TIME
        }

    fun resolve(
        roster: List<Student>,
        taps: List<Tap>,
        startedAtMillis: Long = 0L,
        lateAfterMillis: Long = LATE_AFTER_MILLIS,
    ): ResolvedAttendance {
        val firstTapByUid = LinkedHashMap<String, Tap>()
        for (tap in taps.sortedBy { it.atMillis }) {
            val uid = Uid.normalize(tap.uid)
            if (uid.isEmpty()) continue
            if (!firstTapByUid.containsKey(uid)) firstTapByUid[uid] = tap.copy(uid = uid)
        }

        val rosterUids = roster.map { Uid.normalize(it.uid) }.toSet()

        val present = mutableListOf<PresentStudent>()
        val late = mutableListOf<PresentStudent>()
        val absent = mutableListOf<Student>()
        for (student in roster) {
            val tap = firstTapByUid[Uid.normalize(student.uid)]
            when {
                tap == null -> absent += student
                statusOf(startedAtMillis, tap.atMillis, lateAfterMillis) == AttendanceStatus.LATE ->
                    late += PresentStudent(student, tap.atMillis, tap.method)
                else -> present += PresentStudent(student, tap.atMillis, tap.method)
            }
        }

        val unmatched = firstTapByUid
            .filterKeys { !rosterUids.contains(it) }
            .values
            .toList()

        return ResolvedAttendance(present = present, late = late, absent = absent, unmatched = unmatched)
    }
}
