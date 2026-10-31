package com.nezzar.nfcattendance.data

/**
 * A stable name for a learner whose card has not been bound yet.
 *
 * A class read out of the school form arrives as names only, so every learner
 * carries an empty UID. An empty string cannot be the identity of thirty people:
 * two rows would share one list key, one tap would mark every namesake, and a
 * correction would land on whichever row was first in memory. The key below is
 * derived from the roster position and is written into the tap at the moment a
 * presence is recorded by hand, so it survives a relaunch exactly as a card
 * number does - and it stops being used the moment a real card is bound.
 *
 * It is deliberately internal: a teacher sees "No card yet", never this string.
 */
object ManualKey {

    private const val PREFIX = "name:"

    /**
     * The key for [index] in [roster]: the folded name plus an ordinal, so two
     * learners with the same name stay two learners. The ordinal counts earlier
     * namesakes only, so it does not move when a later learner is added.
     */
    fun of(index: Int, roster: List<Student>): String {
        val student = roster.getOrNull(index) ?: return PREFIX + "row#" + index
        val name = student.name.trim().lowercase()
        var ordinal = 0
        for (i in 0 until index) {
            if (roster[i].name.trim().lowercase() == name) ordinal++
        }
        return PREFIX + name + "#" + ordinal
    }

    /** True when [uid] is a name-derived key rather than a card number. */
    fun isManual(uid: String): Boolean = uid.trim().startsWith(PREFIX)
}
