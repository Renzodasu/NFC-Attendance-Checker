package com.nezzar.nfcattendance

import com.nezzar.nfcattendance.data.NfcAlarm
import com.nezzar.nfcattendance.data.alarmHeadline
import com.nezzar.nfcattendance.data.nfcAlarm
import com.nezzar.nfcattendance.data.readerLine
import com.nezzar.nfcattendance.data.startStatusText
import com.nezzar.nfcattendance.nfc.NfcState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app says when the radio is off. The rule under test is honesty: no
 * surface may call itself ready while no card can be read, and Pause is not a
 * hardware fault.
 */
class NfcPromptTest {

    @Test
    fun switchedOffRadioIsAnAlarm() {
        assertEquals(NfcAlarm.RADIO_OFF, nfcAlarm(NfcState.DISABLED, paused = false))
        val headline = alarmHeadline(NfcAlarm.RADIO_OFF)
        assertNotNull(headline)
        assertTrue(headline!!.contains("NFC is off"))
        // The band carries ONE line beside the button, so the headline must stay
        // short; the full sentence belongs to the reader card (see readerLine).
    }

    @Test
    fun pausedReaderIsNotNagged() {
        // Pause already turns the antenna off; the radio being off as well is not a
        // second problem, so the alarm stands down and Resume explains itself.
        assertEquals(NfcAlarm.NONE, nfcAlarm(NfcState.DISABLED, paused = true))
        assertNull(alarmHeadline(NfcAlarm.NONE))
    }

    @Test
    fun missingHardwareIsItsOwnAlarm() {
        assertEquals(NfcAlarm.NO_HARDWARE, nfcAlarm(NfcState.UNSUPPORTED, paused = false))
        val headline = alarmHeadline(NfcAlarm.NO_HARDWARE)
        assertNotNull(headline)
        assertTrue(headline!!.contains("No NFC"))
        assertNotEquals(alarmHeadline(NfcAlarm.RADIO_OFF), headline)
    }

    @Test
    fun workingRadioHasNoAlarm() {
        assertEquals(NfcAlarm.NONE, nfcAlarm(NfcState.ENABLED, paused = false))
        assertNull(alarmHeadline(NfcAlarm.NONE))
    }

    @Test
    fun everyAlarmHasAHeadlineExceptNone() {
        for (alarm in NfcAlarm.entries) {
            if (alarm == NfcAlarm.NONE) {
                assertNull(alarmHeadline(alarm))
            } else {
                assertNotNull(alarmHeadline(alarm))
                assertTrue(alarmHeadline(alarm)!!.isNotBlank())
                // Anything longer gets ellipsized in the band, which loses the
                // word that matters ("...no card..." says nothing).
                assertTrue(
                    "$alarm headline is too long for a one-line band",
                    alarmHeadline(alarm)!!.length <= 24,
                )
            }
        }
    }

    @Test
    fun readerLineTurnsWithTheRadio() {
        val enabled = readerLine(NfcState.ENABLED, paused = false)
        val disabled = readerLine(NfcState.DISABLED, paused = false)
        val unsupported = readerLine(NfcState.UNSUPPORTED, paused = false)
        assertNotEquals(enabled, disabled)
        assertNotEquals(enabled, unsupported)
        assertNotEquals(disabled, unsupported)
        assertTrue(enabled.contains("Reader mode active"))
        assertTrue(disabled.contains("switched off"))
        assertTrue(unsupported.contains("No NFC adapter"))
    }

    @Test
    fun pauseOutranksTheReaderSentence() {
        val paused = readerLine(NfcState.ENABLED, paused = true)
        assertTrue(paused.contains("Paused"))
        assertTrue(paused.contains("Resume"))
    }

    @Test
    fun registeringHasItsOwnSentence() {
        val scan = readerLine(NfcState.ENABLED, paused = false)
        val register = readerLine(NfcState.ENABLED, paused = false, registering = true)
        assertNotEquals(scan, register)
        assertTrue(register.contains("ID card"))
        assertTrue(scan.contains("student ID"))
    }

    @Test
    fun startSentenceOnlyPromisesTapsWhenTheRadioIsOn() {
        val id = "S-20261030-090000"
        val section = "Grade 6 - Rizal"
        val enabled = startStatusText(id, section, NfcState.ENABLED)
        assertTrue(enabled.contains("Tap student IDs one by one"))
        for (off in listOf(NfcState.DISABLED, NfcState.UNSUPPORTED)) {
            val line = startStatusText(id, section, off)
            assertTrue(line.contains(id))
            assertTrue(line.contains(section))
            assertTrue("$off must not promise taps: $line", !line.contains("Tap student IDs"))
        }
        assertTrue(startStatusText(id, section, NfcState.DISABLED).contains("NFC is off"))
        assertTrue(startStatusText(id, section, NfcState.UNSUPPORTED).contains("QR"))
    }
}
