package com.nezzar.nfcattendance.data

/**
 * What a scanned QR code means for a roster, and the only place that is decided.
 *
 * The codes on a school ID are not ours: they carry a student number, which this
 * app deliberately never reads, keeps, shows or exports. So a scan is asked one
 * question only - does the text contain the NAME of somebody already on this
 * roster? - and when the answer is not obvious the teacher chooses. A student
 * number matches nobody, which is exactly the intended outcome.
 *
 * The codes this app can print itself carry the card UID (the same identifier an
 * NFC tap produces), so those resolve without asking anybody. The scheme prefix is
 * required: a bare eight-digit string is ambiguous with a student number, and
 * guessing would be worse than asking.
 */
object QrCode {

    const val SCHEME = "presencia:"

    /** What a scanned code means for a given roster. */
    sealed interface Reading {
        /** A code this app printed: it carries a card UID, so nobody has to be asked. */
        data class ByUid(val uid: String) : Reading

        /** The code contained the name of someone on the roster, whose UID can now be verified. */
        data class Named(val candidates: List<Student>) : Reading

        /** Nothing in the code ties to a name on this roster: the teacher picks one. */
        object NeedsChoice : Reading

        /** Nothing was read at all. */
        object Empty : Reading
    }

    /** The exact string to draw into a student's QR code. */
    fun encode(uid: String): String = SCHEME + Uid.normalize(uid)

    /**
     * Reads a scanned payload against the roster. The payload itself is never kept,
     * never shown and never written to a file - it is used to answer one question
     * and then dropped.
     */
    fun read(payload: String, roster: List<Student>): Reading {
        val text = payload.trim()
        if (text.isEmpty()) return Reading.Empty

        if (text.startsWith(SCHEME, ignoreCase = true)) {
            val uid = Uid.normalize(text.substring(SCHEME.length))
            return if (Uid.isValid(uid)) Reading.ByUid(uid) else Reading.NeedsChoice
        }

        val haystack = haystackOf(text)
        val named = roster.filter { candidate ->
            val name = normalise(candidate.name)
            name.isNotEmpty() && (" " + haystack + " ").contains(" " + name + " ")
        }
        return if (named.isEmpty()) Reading.NeedsChoice else Reading.Named(named)
    }

    /**
     * The text as searchable words, both as it was read and as it reads once any
     * percent-escaping is resolved - a code holding a URL usually encodes its spaces,
     * and "Ana%20Reyes" has to find Ana Reyes just as "Ana Reyes" does.
     */
    private fun haystackOf(text: String): String {
        val direct = normalise(text)
        val decoded = try {
            normalise(java.net.URLDecoder.decode(text, "UTF-8"))
        } catch (t: Throwable) {
            direct
        }
        return if (decoded == direct) direct else direct + " " + decoded
    }

    /**
     * Letters, digits and single spaces, lower case, so "Ana  REYES." and "ana reyes"
     * agree. Word boundaries survive, so a student called "Li" is not matched inside
     * "William".
     */
    fun normalise(raw: String): String =
        raw.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ")
}
