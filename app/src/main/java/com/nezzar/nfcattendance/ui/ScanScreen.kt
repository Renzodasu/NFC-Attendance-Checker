package com.nezzar.nfcattendance.ui

import android.app.Activity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.nfc.NfcState

@Composable
fun ScanScreen(state: AppState, activity: Activity) {
    val haptics = LocalHapticFeedback.current
    // AppRoot owns the reader and reports what the hardware is doing: one source of
    // truth, so the light and the sentence below can never disagree.
    val nfc = state.nfcState

    val resolved = state.resolved()
    val section = state.selectedSection
    val current = state.session
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.selectedSection != null) {
            item(key = "section-face") {
                SectionFaceCard(state)
            }
        }
        item(key = "title") {
            CollapsingTitle(
                title = "Scan attendance",
                subtitle = "Tap each student ID once. A card read twice in 3 seconds counts once.",
                listState = listState,
            )
        }

        item(key = "reader") {
            BrandCard(modifier = Modifier.animateContentSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NfcLight(state)
                    SectionLabel("NFC reader")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        nfc == NfcState.UNSUPPORTED ->
                            "No NFC adapter on this device (an emulator never has one)."
                        nfc == NfcState.DISABLED ->
                            "NFC adapter is present but switched off in system settings."
                        state.paused ->
                            "Paused - the reader is off and taps are ignored. Press Resume when the " +
                                "class (or a late arrival) is ready."
                        else ->
                            "Reader mode active - hold each student ID to the phone's NFC antenna."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                KeyValueRow("Section", section?.name ?: "(none selected)")
                KeyValueRow("Roster", state.roster.size.toString() + " registered")
                Spacer(Modifier.height(12.dp))
                if (!state.running) {
                    Button(
                        onClick = {
                            state.startSession()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        enabled = state.roster.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start session")
                    }
                    // A disabled button with no reason reads as a broken app.
                    if (state.roster.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Note(
                            if (section == null) "Create a section first, then register its students."
                            else "No students registered in " + section.name + " yet."
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                state.screen = if (section == null) Screen.SECTIONS else Screen.REGISTER
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (section == null) "Go to Sections" else "Go to Register")
                        }
                    }
                } else {
                    // Pause keeps the session open for the whole class: the reader
                    // stops listening, late arrivals still get counted afterwards.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                state.togglePause()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (state.paused) "Resume" else "Pause")
                        }
                        OutlinedButton(
                            onClick = {
                                state.stopSession()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("End session")
                        }
                    }
                }
            }
        }

        item(key = "session") {
            BrandCard {
                SectionLabel("This session")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Present is the number this screen produces; it stops shouting
                    // green while it is still zero.
                    StatTile(
                        value = resolved?.present?.size ?: 0,
                        label = "present",
                        modifier = Modifier.weight(1f),
                        accent = (resolved?.present?.size ?: 0) > 0,
                    )
                    StatTile(
                        value = resolved?.late?.size ?: 0,
                        label = "late",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = resolved?.absent?.size ?: 0,
                        label = "absent",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = resolved?.unmatched?.size ?: 0,
                        label = "unmatched",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                KeyValueRow("Session", current?.sessionId ?: "none")
                KeyValueRow(
                    label = "Started",
                    value = current?.let { ReportBuilder.stampText(it.startedAtMillis) } ?: "not started",
                )
                KeyValueRow("Taps recorded", (current?.taps?.size ?: 0).toString())
                KeyValueRow(
                    label = "Late after",
                    value = state.lateAfterMinutes.toString() + " minute(s) from the start",
                )
                KeyValueRow("Reader", if (state.paused) "paused" else "listening")
                Spacer(Modifier.height(12.dp))
                AnimatedStatusLine(text = state.statusText, emphasise = state.running)
            }
        }

    }
}