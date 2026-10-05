package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.Uid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UidTest {

    @Test
    fun canonicalIsUppercaseHexWithoutSeparators() {
        val bytes = byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte())
        assertEquals("04A1B2C3", Uid.canonical(bytes))
        assertFalse(Uid.canonical(bytes).contains(":"))
        assertFalse(Uid.canonical(bytes).contains("-"))
    }

    @Test
    fun canonicalPadsEveryByteToTwoDigits() {
        assertEquals("00010AFF", Uid.canonical(byteArrayOf(0x00, 0x01, 0x0A, 0xFF.toByte())))
    }

    @Test
    fun reversedFlipsTheByteOrder() {
        val bytes = byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte())
        assertEquals("C3B2A104", Uid.reversed(bytes))
    }

    @Test
    fun normalizeStripsSeparatorsAndUppercases() {
        assertEquals("04A1B2C3", Uid.normalize(" 04:a1:b2:c3 "))
        assertEquals("04A1B2C3", Uid.normalize("04-a1-b2-c3"))
        assertEquals("04A1B2C3", Uid.normalize("04 a1 b2 c3"))
    }

    @Test
    fun validationAcceptsFourByteUidsAndRejectsMalformedOnes() {
        assertTrue(Uid.isValid("04A1B2C3"))
        assertTrue(Uid.isValid("04:A1:B2:C3"))
        assertFalse(Uid.isValid("04A1B2"))
        assertFalse(Uid.isValid("04A1B2C"))
        assertFalse(Uid.isValid("04A1B2CZ"))
        assertFalse(Uid.isValid(""))
    }
}
