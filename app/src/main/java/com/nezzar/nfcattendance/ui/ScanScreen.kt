package com.nezzar.nfcattendance.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.AttendanceMethod
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

    Column(modifier = Modifier.fillMaxSize()) {
        // What was just read, above everything and outside the scroll: the one fact
        // this screen exists to deliver, and it no longer hides under the fold.
        LastReadBar(state)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                    SectionLabel(if (state.scanMode == ReaderMode.NFC) "NFC reader" else "QR reader")
                }
                Spacer(Modifier.height(10.dp))
                // Which reader this session uses. Never both: choosing QR turns reader
                // mode off entirely, so a card cannot be half-read while the camera works.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderMode.entries.forEach { mode ->
                        val chosen = state.scanMode == mode
                        val pick = { state.useReaderMode(mode) }
                        if (chosen) {
                            Button(
                                onClick = pick,
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text(mode.label) }
                        } else {
                            OutlinedButton(
                                onClick = pick,
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Text(mode.label) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (state.scanMode == ReaderMode.QR) {
                    if (state.paused) {
                        Text(
                            text = "Paused - the reader is off and codes are ignored. Press " +
                                "Resume when the class (or a late arrival) is ready.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        QrReader(state)
                    }
                } else {
                    val readerLine = when {
                        nfc == NfcState.UNSUPPORTED ->
                            "No NFC adapter on this device (an emulator never has one)."
                        nfc == NfcState.DISABLED ->
                            "NFC adapter is present but switched off in system settings."
                        state.paused ->
                            "Paused - the reader is off and taps are ignored. Press Resume when the " +
                                "class (or a late arrival) is ready."
                        else ->
                            "Reader mode active - hold each student ID to the phone's NFC antenna."
                    }
                    // The reader's sentence turns with the hardware and with Pause, not
                    // with a tap: it cross-fades so the eye follows what changed.
                    Crossfade(
                        targetState = readerLine,
                        animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
                        label = "readerLine",
                    ) { line ->
                        Text(text = line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // One line for the class: the header pill and the shelf already name it.
                Text(
                    text = (section?.name ?: "No section selected") + "  ·  " +
                        state.roster.size.toString() + " registered",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = state.running,
                    transitionSpec = {
                        (fadeIn(tween(MotionTouchMs, easing = EmphasizedDecelerate)) +
                            slideInVertically(
                                animationSpec = tween(MotionScreenMs, easing = EmphasizedDecelerate),
                            ) { height -> height / 5 })
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "sessionActions",
                ) { running ->
                    Column {
                        if (!running) {
                            Button(
                                onClick = {
                                    state.startSession()
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                enabled = state.roster.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().height(54.dp),
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
                                        if (section == null) {
                                            state.screen = Screen.SECTIONS
                                        } else {
                                            state.openRegister()
                                        }
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
                                    modifier = Modifier.weight(1f).height(54.dp),
                                ) {
                                    SwapLabel(if (state.paused) "Resume" else "Pause")
                                }
                                OutlinedButton(
                                    onClick = {
                                        state.stopSession()
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier = Modifier.weight(1f).height(54.dp),
                                ) {
                                    Text("End session")
                                }
                            }
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
                Spacer(Modifier.height(12.dp))
                AnimatedStatusLine(text = state.statusText, emphasise = state.running)
            }
        }

    }
    }
}

/**
 * The line that never scrolls away: the card that was in the teacher's hand a
 * second ago, whether it belongs to this roster, and how many taps the session
 * has. Undo lives here too, because a wrong tap is noticed right here.
 */
@Composable
private fun LastReadBar(state: AppState) {
    val last = state.lastRead
    val taps = state.session?.taps?.size ?: 0
    val show = state.running || last != null

    AnimatedVisibility(
        visible = show,
        enter = fadeIn(tween(MotionTouchMs)) + slideInVertically(
            animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
        ) { height -> -height },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(MotionTouchMs)) { height -> -height },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NfcLight(state, dot = 8.dp)
                Column(modifier = Modifier.weight(1f)) {
                    val headline = when {
                        last == null -> "Ready - waiting for the first card"
                        last.duplicate -> "Already recorded: " + (last.name ?: last.uid)
                        last.method == AttendanceMethod.MANUAL -> "Marked by hand: " + (last.name ?: last.uid)
                        last.name != null -> "Recorded: " + last.name
                        else -> "NOT ON ROSTER"
                    }
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            if (last != null) {
                                if (last.name == null) {
                                    append(last.uid)
                                    append("  ·  ")
                                }
                                append(last.method.label)
                                append("  ·  ")
                            }
                            append(taps)
                            append(if (taps == 1) " tap recorded" else " taps recorded")
                            if (!state.running) append("  ·  session not running")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { state.undoLastTap() }, enabled = taps > 0) {
                    Text("Undo")
                }
            }
        }
    }
}