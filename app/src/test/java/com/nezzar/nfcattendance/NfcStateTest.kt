package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.nfc.NfcState
import com.nezzar.nfcattendance.nfc.nfcStateOf
import org.junit.Assert.assertEquals
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
}
