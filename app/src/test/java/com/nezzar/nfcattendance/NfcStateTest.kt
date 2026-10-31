package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.readerLine
import com.nezzar.nfcattendance.data.shortReaderLine
import com.nezzar.nfcattendance.nfc.NfcState
import com.nezzar.nfcattendance.nfc.nfcStateOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three lights on the reader: a phone that can read, a phone whose NFC is
 * switched off, and a phone with no NFC at all.
 */
class NfcStateTest {

    @Test
    fun noHardwareReadsAsUnsupported() {
        assertEquals(NfcState.UNSUPPORTED, nfcStateOf(supported = false, enabled = false))
    }

    @Test
    fun hardwareWithTheSwitchOffReadsAsDisabled() {
        assertEquals(NfcState.DISABLED, nfcStateOf(supported = true, enabled = false))
    }

    @Test
    fun hardwareThatIsReadingReadsAsEnabled() {
        assertEquals(NfcState.ENABLED, nfcStateOf(supported = true, enabled = true))
    }

    @Test
    fun absentHardwareCanNeverReadAsEnabled() {
        // A phone with no adapter cannot be on, whatever the flag claims.
        assertEquals(NfcState.UNSUPPORTED, nfcStateOf(supported = false, enabled = true))
    }

    /**
     * The Scan tab's reader line has room for the verb and no more, so it must say
     * what to do in each of the four states - and never the long card sentence.
     */
    @Test
    fun theShortLineSaysWhatToDoInAllFourStates() {
        assertEquals(
            "Ready - hold an ID to the phone",
            shortReaderLine(NfcState.ENABLED, paused = false),
        )
        assertEquals(
            "NFC is off - a tap cannot reach the app",
            shortReaderLine(NfcState.DISABLED, paused = false),
        )
        assertEquals(
            "No NFC on this phone - mark presence by hand",
            shortReaderLine(NfcState.UNSUPPORTED, paused = false),
        )
        assertEquals(
            "Paused - taps are ignored until you resume",
            shortReaderLine(NfcState.ENABLED, paused = true),
        )
    }

    @Test
    fun theShortLineIsShorterThanTheCardSentence() {
        for (nfc in NfcState.entries) {
            assertTrue(
                "the Scan line must not be the card sentence for " + nfc,
                shortReaderLine(nfc, paused = false).length < readerLine(nfc, paused = false).length,
            )
        }
    }
}
