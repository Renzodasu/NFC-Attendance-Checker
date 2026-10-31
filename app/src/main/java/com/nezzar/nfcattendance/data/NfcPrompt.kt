package com.nezzar.nfcattendance.data

import com.nezzar.nfcattendance.nfc.NfcState

/**
 * What the reader needs the teacher to do, as opposed to what the hardware is.
 *
 * Pause wins over everything: a paused reader is off on purpose, so the app must
 * not nag about a radio it is not using. Kept pure so the affordances of a whole
 * screen can be decided without a phone in hand.
 */
enum class NfcAlarm { NONE, RADIO_OFF, NO_HARDWARE }

fun nfcAlarm(nfc: NfcState, paused: Boolean): NfcAlarm = when {
    paused -> NfcAlarm.NONE
    nfc == NfcState.DISABLED -> NfcAlarm.RADIO_OFF
    nfc == NfcState.UNSUPPORTED -> NfcAlarm.NO_HARDWARE
    else -> NfcAlarm.NONE
}

/**
 * The one sentence at the top of the live-result bar. When it is not null it
 * replaces "Ready - waiting for the first card": nothing is more misleading at a
 * door than a screen that says ready while the antenna is deaf.
 *
 * Kept to a few words on purpose. The bar has ONE line beside the Undo (or the
 * "Turn on NFC") button, and a headline that gets ellipsized at "no card..." has
 * already lost the word that matters. The long form lives in [readerLine].
 */
fun alarmHeadline(alarm: NfcAlarm): String? = when (alarm) {
    NfcAlarm.RADIO_OFF -> "NFC is off"
    NfcAlarm.NO_HARDWARE -> "No NFC on this phone"
    NfcAlarm.NONE -> null
}

/**
 * The reader card's body line. One function for Scan and Register so the two
 * pages cannot drift into saying different things about the same radio.
 */
fun readerLine(nfc: NfcState, paused: Boolean, registering: Boolean = false): String = when {
    nfc == NfcState.UNSUPPORTED ->
        "No NFC adapter on this device, so a tap has nowhere to land."
    nfc == NfcState.DISABLED ->
        "NFC is switched off, so a tap cannot reach this app. Turn it on with the " +
            "button below, then hold an ID to the phone."
    paused ->
        "Paused - the reader is off and taps are ignored. Press Resume when the " +
            "class (or a late arrival) is ready."
    registering -> "Reader mode active - hold the ID card to the phone's NFC antenna."
    else -> "Reader mode active - hold each student ID to the phone's NFC antenna."
}

/**
 * The same state, cut to the length of the Scan screen's single reader line.
 *
 * [readerLine] is written for a card body that has room for a full sentence with
 * a cause and a remedy. The Scan tab has one line beside a dot, so it gets the
 * verb and nothing else: what the teacher has to do, not why the radio did it.
 * Kept here, next to the long form, so the two cannot contradict each other.
 */
fun shortReaderLine(nfc: NfcState, paused: Boolean): String = when {
    nfc == NfcState.UNSUPPORTED -> "No NFC on this phone - mark presence by hand"
    nfc == NfcState.DISABLED -> "NFC is off - a tap cannot reach the app"
    paused -> "Paused - taps are ignored until you resume"
    else -> "Ready - hold an ID to the phone"
}

/**
 * The sentence left in the status bar the moment a session starts. Starting a
 * session on a phone whose radio is off used to say "Tap student IDs one by
 * one" - the app claiming a capability the phone does not have.
 */
fun startStatusText(sessionId: String, sectionName: String, nfc: NfcState): String = when (nfc) {
    NfcState.ENABLED ->
        "Session " + sessionId + " started for " + sectionName + ". Tap student IDs one by one."
    NfcState.DISABLED ->
        "Session " + sessionId + " started for " + sectionName + ", but NFC is off, " +
            "so no card can be read yet. Turn it on in Settings, then tap the IDs."
    NfcState.UNSUPPORTED ->
        "Session " + sessionId + " started for " + sectionName + ", but this phone has " +
            "no NFC reader, so cards cannot be tapped. Mark presence by hand on the Report page."
}
