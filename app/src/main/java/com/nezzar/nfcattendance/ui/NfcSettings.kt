package com.nezzar.nfcattendance.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The platform offers no way to switch NFC on from inside an app: NfcAdapter.enable()
 * is @hide and reserved for system apps. The only door is a settings surface, so
 * every "Turn on NFC" control opens one. API 29 added the quick-settings tile panel,
 * which is the shortest path; older phones get the NFC settings page.
 */
fun openNfcSettings(context: Context) {
    val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Intent(Settings.Panel.ACTION_NFC)
    } else {
        null
    }
    val page = Intent(Settings.ACTION_NFC_SETTINGS)
    // A ROM may not wire either action; ask the package manager before choosing,
    // then start the one that resolves (the settings page is the safer fallback).
    val target = listOfNotNull(panel, page).firstOrNull {
        context.packageManager.resolveActivity(it, 0) != null
    } ?: page
    // Settings lives in another task. Harmless when the context is already an
    // Activity, required when it is not.
    target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // A phone without the activity must not crash a page the teacher is on.
    runCatching { context.startActivity(target) }
}

/** The one control that fixes "NFC is off": 48 dp, outlined, verb first. */
@Composable
fun NfcOffAction(context: Context, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = { openNfcSettings(context) },
        modifier = modifier.height(48.dp),
    ) { Text("Turn on NFC") }
}
