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
 */
object AttendanceResolver {

    /** Fifteen minutes after the start, by default. */
    const val DEFAULT_LATE_AFTER_MINUTES = 15
    const val LATE_AFTER_MILLIS = DEFAULT_LATE_AFTER_MINUTES * 60L * 1000L

    fun resolve(
        roster: List<Student>,
        taps: List<Tap>,
        startedAtMillis: Long = 0L,
        lateAfterMillis: Long = LATE_AFTER_MILLIS,
    ): ResolvedAttendance {
        val firstTapByUid = LinkedHashMap<String, Long>()
        for (tap in taps.sortedBy { it.atMillis }) {
            val uid = Uid.normalize(tap.uid)
            if (uid.isEmpty()) continue
            if (!firstTapByUid.containsKey(uid)) firstTapByUid[uid] = tap.atMillis
        }

        val rosterUids = roster.map { Uid.normalize(it.uid) }.toSet()

        val present = mutableListOf<PresentStudent>()
        val late = mutableListOf<PresentStudent>()
        val absent = mutableListOf<Student>()
        for (student in roster) {
            val at = firstTapByUid[Uid.normalize(student.uid)]
            when {
                at == null -> absent += student
                startedAtMillis > 0L && at - startedAtMillis > lateAfterMillis ->
                    late += PresentStudent(student, at)
                else -> present += PresentStudent(student, at)
            }
        }

        val unmatched = firstTapByUid
            .filterKeys { !rosterUids.contains(it) }
            .map { entry -> Tap(entry.key, entry.value) }

        return ResolvedAttendance(present = present, late = late, absent = absent, unmatched = unmatched)
    }
}
