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
import com.nezzar.nfcattendance.data.NfcAlarm
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.alarmHeadline
import com.nezzar.nfcattendance.data.nfcAlarm
import com.nezzar.nfcattendance.data.readerLine

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
        LastReadBar(state, activity)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            CollapsingTitle(
                title = "Scan attendance",
                subtitle = "Tap each student ID once. A card counts once per session - a second tap is flagged, never counted twice.",
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
                    // The reader's sentence turns with the hardware and with Pause, not
                    // with a tap: it cross-fades so the eye follows what changed.
                    Crossfade(
                        targetState = readerLine(nfc, state.paused),
                        animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
                        label = "readerLine",
                    ) { line ->
                        Text(text = line, style = MaterialTheme.typography.bodyMedium)
                    }
                    // A sentence that names the fix is a promise; the button is the way
                    // to keep it. Only for a radio that is off - a phone without the
                    // hardware has nothing to switch on.
                    if (state.scanMode == ReaderMode.NFC && nfcAlarm(nfc, state.paused) == NfcAlarm.RADIO_OFF) {
                        Spacer(Modifier.height(10.dp))
                        NfcOffAction(activity)
                    }
                }
                Spacer(Modifier.height(8.dp))
                // The class this session belongs to, in the one band that stays on screen
                // while the reader is held over a card.
                ClassIdentity(
                    name = section?.name ?: "",
                    subject = null,
                    selected = section != null,
                    badgeSize = 30.dp,
                    nameStyle = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (section == null) "No class selected yet" else
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

        // Skip and flag: a tap the app could not place is never dropped and never
        // silently swallowed. It waits here, by name of the card, until the line has
        // gone in and the teacher has a free hand to give it a name.
        item(key = "attention") {
            val flagged = state.unmatchedTaps()
            if (flagged.size > state.attentionSeen) {
                BrandCard {
                    SectionLabel("Needs attention")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "These taps belong to nobody on " + (section?.name ?: "this roster") +
                            ". They are saved, not dropped - give each card a name, or mark the " +
                            "student by hand from the roster.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    flagged.take(8).forEach { tap ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = ReportBuilder.timeText(tap.atMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(64.dp),
                            )
                            Text(
                                text = tap.uid,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = tap.method.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (flagged.size > 8) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "and " + (flagged.size - 8) + " more - they are in the report too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { state.dismissAttention() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Mark as seen")
                    }
                }
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
private fun LastReadBar(state: AppState, activity: Activity) {
    val last = state.lastRead
    val taps = state.session?.taps?.size ?: 0
    val show = state.running || last != null
    // The radio outranks the last card: "Ready - waiting for the first card" while
    // the antenna is deaf is the lie this bar exists to stop. In QR mode the radio
    // is not the reader at all, so there is nothing to warn about.
    val alarm = if (state.scanMode == ReaderMode.NFC) {
        nfcAlarm(state.nfcState, state.paused)
    } else {
        NfcAlarm.NONE
    }

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
                    val headline = alarmHeadline(alarm) ?: when {
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
                if (alarm == NfcAlarm.RADIO_OFF) {
                    // The slot Undo occupies is dead at zero taps anyway; while the
                    // radio is off it belongs to the one verb that changes anything.
                    TextButton(onClick = { openNfcSettings(activity) }) {
                        Text("Turn on NFC")
                    }
                } else {
                    // No switch to offer on a phone without the hardware; Undo still
                    // matters there, because manual marks can still be undone.
                    TextButton(onClick = { state.undoLastTap() }, enabled = taps > 0) {
                        Text("Undo")
                    }
                }
            }
        }
    }
}