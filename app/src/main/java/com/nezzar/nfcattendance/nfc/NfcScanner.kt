package com.nezzar.nfcattendance.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Bundle

/**
 * What the phone's NFC hardware is doing right now - the three lights the UI
 * draws: a phone that can read ([NfcState.ENABLED]), a phone with the hardware
 * but the switch off ([NfcState.DISABLED]), and a phone with no NFC at all
 * ([NfcState.UNSUPPORTED]).
 */
enum class NfcState { UNSUPPORTED, DISABLED, ENABLED }

/**
 * Absent hardware, present-but-off, or reading. Kept pure so the mapping can be
 * tested without a phone in hand.
 */
fun nfcStateOf(supported: Boolean, enabled: Boolean): NfcState = when {
    !supported -> NfcState.UNSUPPORTED
    enabled -> NfcState.ENABLED
    else -> NfcState.DISABLED
}

/**
 * Thin wrapper around reader mode. We only ever read the serial UID from
 * sector 0, which is world-readable on a MIFARE Classic 1K - no issuer keys,
 * no MIFARE Classic authentication at all.
 */
class NfcScanner(private val activity: Activity) {

    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun isSupported(): Boolean = adapter != null

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    /** The reader's state as one value, for the light the UI draws. */
    fun state(): NfcState = nfcStateOf(isSupported(), isEnabled())

    fun start(onUid: (ByteArray) -> Unit) {
        val nfc = adapter ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            // NfcA confirms this is an NFC-A (ISO 14443-3A) tag; the UID itself
            // comes from Tag.getId() - sector 0 is world-readable, no keys involved.
            val id: ByteArray? = if (NfcA.get(tag) != null) tag.id else null
            if (id != null && id.isNotEmpty()) onUid(id)
        }
        nfc.enableReaderMode(activity, callback, flags, Bundle())
    }

    fun stop() {
        adapter?.disableReaderMode(activity)
    }
}
