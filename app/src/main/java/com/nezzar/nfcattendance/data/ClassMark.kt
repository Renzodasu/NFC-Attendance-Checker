package com.nezzar.nfcattendance.data

/**
 * The two-letter mark a class wears instead of a playing card face.
 *
 * It is DERIVED from the name the teacher already typed, so it can never drift
 * from the class it stands for and never has to be chosen or kept unique:
 *
 *   "Grade 3 - Rizal"        -> "3R"
 *   "Grade 3 - Sampaguita"   -> "3S"
 *   "Grade 5 - NARRA" -> "5P"
 *   "7 - Narra"              -> "7N"
 *   "Narra"                  -> "NA"
 *
 * Rule: the last dash-separated part is the proper noun, so its first letter is
 * the letter; the first number anywhere in the name (two digits at most) is the
 * digit. A name with no number keeps two letters of the noun. Anything that
 * cannot produce two characters falls back to "?" rather than drawing nothing.
 */
object ClassMark {

    fun monogram(rawName: String): String {
        val name = rawName.trim()
        if (name.isEmpty()) return "?"
        val noun = name.split('-', '\u2013', '\u2014')
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            ?: name
        val digits = Regex("[0-9]+").find(name)?.value?.takeLast(2).orEmpty()
        // A noun may itself start with a digit ("BSCE-3A" -> noun "3A"), so the letter
        // is preferred: taking the first alphanumeric would repeat the grade digit and
        // paint "33" instead of "3A". A noun with no letters at all still yields a mark.
        val initial = (noun.firstOrNull { it.isLetter() } ?: noun.firstOrNull { it.isLetterOrDigit() })
            ?.uppercaseChar()?.toString().orEmpty()
        return when {
            digits.isNotEmpty() && initial.isNotEmpty() -> digits + initial
            digits.isNotEmpty() -> digits
            noun.length >= 2 -> noun.take(2).uppercase()
            initial.isNotEmpty() -> initial
            else -> "?"
        }
    }

    /**
     * The part of the name that is the class itself, for the line under a mark:
     * "Grade 3 - Rizal" reads better as "Rizal" there, while the full name stays
     * in the heading.
     */
    fun noun(rawName: String): String {
        val name = rawName.trim()
        if (name.isEmpty()) return ""
        return name.split('-', '\u2013', '\u2014')
            .map { it.trim() }
            .lastOrNull { it.isNotEmpty() }
            .orEmpty()
    }
}
