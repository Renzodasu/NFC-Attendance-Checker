package com.nezzar.nfcattendance.ui

import android.app.Activity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.AttendanceMethod
import com.nezzar.nfcattendance.data.NfcAlarm
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.ResolvedAttendance
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.alarmHeadline
import com.nezzar.nfcattendance.data.nfcAlarm
import com.nezzar.nfcattendance.data.shortReaderLine
import com.nezzar.nfcattendance.nfc.NfcState
import com.nezzar.nfcattendance.ui.theme.BrandAmber
import com.nezzar.nfcattendance.ui.theme.LightAmber
import com.nezzar.nfcattendance.ui.theme.isBrandDark

/**
 * The Scan tab, in one column: what was just read (pinned), whose class this is,
 * the four numbers the session is producing, the state of the reader, and one
 * control pinned above the tabs.
 *
 * Two things moved in 0.0.10. The screen stopped being a stack of cards - a
 * teacher reads three facts at a door, not four panels - and every number that
 * does not need a box to be read lost it. What is left is the reader's sentence
 * (the only line allowed to turn amber), the present count (the only number
 * allowed to be green), and one full-width control that changes its mind between
 * Start, Pause and End instead of three buttons sitting there at once.
 */
@Composable
fun ScanScreen(state: AppState, activity: Activity) {
    val resolved = state.resolved()
    val section = state.selectedSection

    Column(modifier = Modifier.fillMaxSize()) {
        // What was just read, above everything and outside the scroll: the one fact
        // this screen exists to deliver, and it no longer hides under the fold.
        LastReadBar(state, activity)

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "class") { ClassHeadline(section, state.roster.size) }
                item(key = "numbers") { SessionNumbers(resolved) }
                item(key = "reader") { ReaderLine(state, activity) }
                item(key = "status") { ScanStatus(state) }
                item(key = "attention") { AttentionBlock(state, section) }
            }

            SessionControls(
                state = state,
                section = section,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Class name, then one grey line: how many learners it has, and what it teaches. */
@Composable
private fun ClassHeadline(section: Section?, registered: Int) {
    Column {
        Text(
            text = section?.name ?: "No class yet",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                if (section == null) {
                    append("Create a class on the Sections tab")
                } else {
                    append(registered)
                    append(if (registered == 1) " student registered" else " students registered")
                    val subject = section.subject
                    if (subject.isNotBlank()) {
                        append("  \u00B7  ")
                        append(subject)
                    }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The session's four numbers as one row of text, not four tiles: at arm's length
 * a column of digits reads faster than a grid of cards, and it costs a fifth of
 * the height. Present is the only one that earns green, and only once it is not
 * zero - a green zero would celebrate nothing.
 */
@Composable
private fun SessionNumbers(resolved: ResolvedAttendance?) {
    val present = resolved?.present?.size ?: 0
    // No gaps between the cells: the four labels are the widest thing in this row
    // and a 14dp gutter was enough to push UNMATCHED onto a second line.
    Row(modifier = Modifier.fillMaxWidth()) {
        SessionNumber("Present", present, Modifier.weight(1f), accent = present > 0)
        SessionNumber("Late", resolved?.late?.size ?: 0, Modifier.weight(1f))
        SessionNumber("Absent", resolved?.absent?.size ?: 0, Modifier.weight(1f))
        SessionNumber("Unmatched", resolved?.unmatched?.size ?: 0, Modifier.weight(1f))
    }
}

@Composable
private fun SessionNumber(label: String, value: Int, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/**
 * The reader's state in one line. Amber is reserved for the radio being off - the
 * one condition the teacher can fix from here - and a phone without NFC gets the
 * quiet grey sentence instead, because there is no switch to promise it.
 */
@Composable
private fun ReaderLine(state: AppState, activity: Activity) {
    val nfc = state.nfcState
    val alarm = nfcAlarm(nfc, state.paused)
    val dark = isBrandDark()
    val warn = if (dark) BrandAmber else LightAmber

    Row(verticalAlignment = Alignment.CenterVertically) {
        NfcLight(state, dot = 8.dp)
        Spacer(Modifier.width(8.dp))
        Crossfade(
            targetState = shortReaderLine(nfc, state.paused),
            animationSpec = tween(durationMillis = MotionTouchMs, easing = EmphasizedDecelerate),
            label = "readerLine",
            modifier = Modifier.weight(1f),
        ) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = if (alarm == NfcAlarm.RADIO_OFF) warn else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (alarm == NfcAlarm.RADIO_OFF) {
            TextButton(onClick = { openNfcSettings(activity) }) { Text("Turn on NFC") }
        }
    }
}

/** What the app last did - one muted line, and only once something has happened. */
@Composable
private fun ScanStatus(state: AppState) {
    // Only after something happened: the start-of-session sentence repeats what the
    // reader line and the pinned control already say, in four lines of prose.
    if (state.lastRead == null) return
    AnimatedStatusLine(
        text = state.statusText,
        emphasise = false,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Skip and flag: a tap the app could not place is never dropped and never
 * silently swallowed. It waits here, by name of the card, until the line has gone
 * in and the teacher has a free hand to give it a name.
 */
@Composable
private fun AttentionBlock(state: AppState, section: Section?) {
    val flagged = state.unmatchedTaps()
    if (flagged.size <= state.attentionSeen) return

    BrandCard(modifier = Modifier.animateContentSize()) {
        SectionLabel("Needs attention")
        Spacer(Modifier.height(6.dp))
        Text(
            text = flagged.size.toString() + " " +
                (if (flagged.size == 1) "tap belongs" else "taps belong") +
                " to nobody on " + (section?.name ?: "this roster") +
                (if (flagged.size == 1) ". It is saved, not dropped." else ". They are saved, not dropped."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
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
                    modifier = Modifier.width(84.dp),
                    maxLines = 1,
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
            Spacer(Modifier.height(4.dp))
            Note("and " + (flagged.size - 8) + " more - they are in the report too.")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { state.dismissAttention() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Mark as seen")
        }
    }
}

/**
 * The one control this tab owns, pinned above the tab bar so Start, Pause and End
 * are never scrolled away from. When there is nothing to start, the pill is
 * replaced by the button that fixes it - a disabled Start with no reason reads as
 * a broken app.
 */
@Composable
private fun SessionControls(state: AppState, section: Section?, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            when {
                state.running -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            state.togglePause()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        SwapLabel(if (state.paused) "Resume" else "Pause")
                    }
                    OutlinedButton(
                        onClick = {
                            state.stopSession()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        shape = CircleShape,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Text("End")
                    }
                }
                state.roster.isEmpty() -> {
                    Note(
                        if (section == null) "No class yet - create one first."
                        else "No students yet in " + section.name + "."
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (section == null) state.screen = Screen.SECTIONS else state.openRegister()
                        },
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(if (section == null) "Choose a class" else "Register students")
                    }
                }
                else -> Button(
                    onClick = {
                        state.startSession()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text("Start session")
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
    // Nothing read yet means there is no result to report: the reader line under
    // the numbers already says why the antenna is quiet, and saying it twice on one
    // screen made the top of the app read as an error banner.
    val show = last != null
    // The radio outranks the last card: "Ready - waiting for the first card" while
    // the antenna is deaf is the lie this bar exists to stop. A mark made by hand is
    // not a card read, though - the radio has nothing to do with it - so the bar
    // reports the mark and leaves the antenna warning to the reader line below.
    val alarm = nfcAlarm(state.nfcState, state.paused)
    val barAlarm = if (last != null && last.method == AttendanceMethod.MANUAL) NfcAlarm.NONE else alarm
    val dark = isBrandDark()
    val warn = if (dark) BrandAmber else LightAmber

    if (!show) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NfcLight(state, dot = 8.dp)
            Text(
                text = alarmHeadline(barAlarm) ?: when {
                    last == null -> "Ready"
                    last.duplicate -> "Already counted: " + (last.name ?: last.uid)
                    last.method == AttendanceMethod.MANUAL -> "Marked by hand: " + (last.name ?: last.uid)
                    last.name != null -> "Recorded: " + last.name
                    else -> "Not on roster"
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (barAlarm != NfcAlarm.NONE) warn else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = taps.toString() + (if (taps == 1) " tap" else " taps"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (barAlarm == NfcAlarm.RADIO_OFF) {
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
