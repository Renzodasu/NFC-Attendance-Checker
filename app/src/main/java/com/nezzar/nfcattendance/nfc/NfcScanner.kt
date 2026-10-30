package com.nezzar.nfcattendance.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.os.Bundle
import android.os.Handler
import android.os.Looper

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

    /**
     * Reader callbacks arrive on the NFC binder thread, but everything they touch -
     * Compose state, haptics, the tone - lives on the main thread. A tap that
     * arrives while the UI is recomposing is normal at the door, so the read is
     * handed over instead of being executed where it landed.
     */
    private val main = Handler(Looper.getMainLooper())

    fun isSupported(): Boolean = adapter != null

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    /** The reader's state as one value, for the light the UI draws. */
    fun state(): NfcState = nfcStateOf(isSupported(), isEnabled())

    /**
     * Arms reader mode. Returns false when the adapter is gone or the platform
     * refuses to arm (the radio was switched off between the state read and this
     * call), so the caller never has to guard an exception on a tap.
     */
    fun start(onUid: (ByteArray) -> Unit): Boolean {
        val nfc = adapter ?: return false
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        val callback = NfcAdapter.ReaderCallback { tag: Tag ->
            // NfcA confirms this is an NFC-A (ISO 14443-3A) tag; the UID itself
            // comes from Tag.getId() - sector 0 is world-readable, no keys involved.
            val id: ByteArray? = if (NfcA.get(tag) != null) tag.id else null
            if (id != null && id.isNotEmpty()) main.post { onUid(id) }
        }
        return try {
            nfc.enableReaderMode(activity, callback, flags, Bundle())
            true
        } catch (t: Exception) {
            // IllegalArgumentException/UnsupportedOperationException when the radio
            // died in between: a page that cannot read is bad, a crash is worse.
            false
        }
    }

    fun stop() {
        adapter?.disableReaderMode(activity)
    }
}
