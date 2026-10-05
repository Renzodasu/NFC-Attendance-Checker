package com.nezzar.nfcattendance.data

/**
 * Single canonical UID representation shared by the scanner, the registration
 * flow and the absence resolver: uppercase hexadecimal, no separators.
 */
object Uid {

    private val HEX = "0123456789ABCDEF".toCharArray()

    /** Canonical form of the bytes exactly as the NFC reader returned them. */
    fun canonical(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /** Same UID with the byte order reversed - the form NFC Tools usually prints. */
    fun reversed(bytes: ByteArray): String = canonical(bytes.reversedArray())

    /** Normalises a UID: strips separators, uppercases. */
    fun normalize(raw: String): String =
        raw.trim().replace(":", "").replace("-", "").replace(" ", "").uppercase()

    /** A MIFARE Classic 1K UID is 4 bytes = 8 hex characters (longer UIDs are allowed). */
    fun isValid(candidate: String): Boolean {
        val s = normalize(candidate)
        if (s.length < 8 || s.length % 2 != 0) return false
        return s.all { it.isDigit() || it in 'A'..'F' }
    }
}
