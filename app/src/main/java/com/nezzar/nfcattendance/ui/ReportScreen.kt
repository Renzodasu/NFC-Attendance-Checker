package com.nezzar.nfcattendance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.ResolvedAttendance
import com.nezzar.nfcattendance.data.Sf2Report
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.ui.theme.BrandAmber
import com.nezzar.nfcattendance.ui.theme.LightAmber
import com.nezzar.nfcattendance.ui.theme.isBrandDark

/**
 * The Report tab, read top to bottom the way the app asks the question: whose
 * session this was, the four numbers in one line, who is absent (the conclusion),
 * then the chart, the names already accounted for, and the two exports.
 *
 * 0.0.10 dropped the card-per-student stacking. Thirty absent learners used to be
 * thirty rounded panels with a button floating in each, which pushed the export
 * and the present list off the screen; the same names now read as one-line rows -
 * name, card, and a Mark where the row is. The chart and the supporting lists are
 * closed until asked for, because a teacher opening this tab is looking for
 * names, not for a donut.
 */
@Composable
fun ReportScreen(state: AppState) {
    val resolved = state.reportResolved()
    // Today's session when one is open, otherwise this class's latest on disk: the
    // report a teacher reads after a relaunch is still about the day she scanned.
    val session = state.reportSession
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (resolved == null || session == null) {
            item(key = "empty") {
                EmptyState(
                    icon = R.drawable.ic_nav_scan,
                    title = "No session yet",
                    body = "Start one on the Scan tab and tap each student ID once.",
                    actionLabel = "Go to Scan",
                    onAction = { state.screen = Screen.SCAN },
                )
            }
            // The school form is a month, not a session: it is offered here too, so
            // a class with history can produce its SF2 after any launch.
            item(key = "sf2") {
                BrandCard { Sf2Section(state) }
            }
            return@LazyColumn
        }

        // Class, day, size and the one action this screen owns - together, as the
        // header. The export used to sit under every list on the screen.
        item(key = "header") { ReportHeader(state, session.sectionName, session.startedAtMillis, session.roster.size) }

        // The four numbers as one line of text, and the absent one drawn twice the
        // size of the others: that count is the conclusion this tab is opened for.
        item(key = "numbers") { NumberRow(resolved, session.roster.size) }

        if (resolved.absent.isEmpty()) {
            item(key = "absent-none") { Note("Everyone on the roster was present.") }
        } else {
            // "No card yet" under all thirty names is one sentence thirty times. It
            // only earns its line when some absent learners do have a card.
            val anyCard = resolved.absent.any { it.uid.isNotBlank() }
            item(key = "absent-list") {
                Column {
                    resolved.absent.forEachIndexed { index, student ->
                        if (index > 0) RowDivider()
                        AbsentRow(student, showNoCard = anyCard, onMark = { state.markPresent(student) })
                    }
                }
            }
        }

        // The tiles state the conclusion; the chart explains the same numbers one
        // line down. It starts closed - the summary line is the useful part at a door.
        item(key = "chart") { ReportChartCard(state = state, resolved = resolved, session = session) }

        if (resolved.late.isNotEmpty()) {
            item(key = "late") {
                Disclosure("Late: " + resolved.late.size) {
                    Column {
                        resolved.late.forEachIndexed { index, item ->
                            if (index > 0) RowDivider()
                            PersonRow(
                                name = item.student.name,
                                uid = item.student.uid,
                                time = ReportBuilder.timeText(item.firstTapMillis),
                            )
                        }
                    }
                }
            }
        }

        if (resolved.present.isNotEmpty()) {
            item(key = "present") {
                Disclosure("Present: " + resolved.present.size) {
                    Column {
                        resolved.present.forEachIndexed { index, item ->
                            if (index > 0) RowDivider()
                            PersonRow(
                                name = item.student.name,
                                uid = item.student.uid,
                                time = ReportBuilder.timeText(item.firstTapMillis),
                            )
                        }
                    }
                }
            }
        }

        if (resolved.unmatched.isNotEmpty()) {
            item(key = "unmatched") {
                Column {
                    SectionLabel("Unmatched taps: " + resolved.unmatched.size)
                    Spacer(Modifier.height(2.dp))
                    Note("A card that is not on this roster - flagged, never dropped.")
                    Spacer(Modifier.height(4.dp))
                    resolved.unmatched.forEachIndexed { index, tap ->
                        if (index > 0) RowDivider()
                        PersonRow(name = tap.uid, uid = "", time = ReportBuilder.timeText(tap.atMillis))
                    }
                }
            }
        }

        // The machine details, at the bottom where they belong: read once when the
        // sheet is handed in, never while the list is being checked.
        item(key = "footer") {
            Note("Session " + session.sessionId + "  \u00B7  started " + ReportBuilder.stampText(session.startedAtMillis))
        }
    }
}

/**
 * Name, day, class size, Export, and the school form folded under it. One card, so
 * the export is above the fold on every phone instead of under thirty absent rows.
 */
@Composable
private fun ReportHeader(state: AppState, sectionName: String, startedAt: Long, rosterSize: Int) {
    BrandCard {
        Text(
            text = sectionName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = ReportBuilder.dayTimeText(startedAt) + "  \u00B7  " + rosterSize +
                (if (rosterSize == 1) " student" else " students"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { state.exportReport() },
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_export),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Export")
        }
        ExportResult(
            path = state.reportPath,
            note = state.reportError,
            onShare = state.reportUri?.let { uri -> { state.shareFile(uri) } },
        )
        Spacer(Modifier.height(4.dp))
        Sf2Section(state)
    }
}

/**
 * The four numbers, one line - and the absent one at twice the size of the rest,
 * because it is the number this tab exists to say. The numbers are bottom-aligned
 * so the captions sit on one line under a hero digit that is taller than them.
 */
@Composable
private fun NumberRow(resolved: ResolvedAttendance, rosterSize: Int) {
    val dark = isBrandDark()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1.15f)) {
            Text(
                text = resolved.absent.size.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = if (resolved.absent.isNotEmpty()) {
                    if (dark) BrandAmber else LightAmber
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
            )
            Text(
                text = "ABSENT OF " + rosterSize,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NumberCell("Late", resolved.late.size, Modifier.weight(0.95f))
        NumberCell("Present", resolved.present.size, Modifier.weight(0.95f), accent = resolved.present.isNotEmpty())
        NumberCell("Unmatched", resolved.unmatched.size, Modifier.weight(0.95f))
    }
}

@Composable
private fun NumberCell(label: String, value: Int, modifier: Modifier = Modifier, accent: Boolean = false) {
    val colour = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(modifier = modifier) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = colour,
            maxLines = 1,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * One absent learner per line: the name at reading size, the card underneath it in
 * grey, and Mark at the end of the row. A tapped mark moves the row out of this
 * list and into Present, so the list shrinks as the teacher works through it.
 */
@Composable
private fun AbsentRow(student: Student, showNoCard: Boolean, onMark: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = student.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (student.uid.isNotBlank()) {
                Text(
                    text = student.uid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (showNoCard) {
                Text(
                    text = "No card yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        // A broken, lost or never-made card must not be able to turn a present
        // student into an absent one. The sheet records which were marked by hand.
        TextButton(
            onClick = onMark,
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Mark " + student.name + " present" },
        ) {
            Text("Mark")
        }
    }
}

/** A name already accounted for: who, when, and nothing else. */
@Composable
private fun PersonRow(name: String, uid: String, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (uid.isNotBlank()) {
                Text(
                    text = uid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A heading that opens into its list. Closed is the default because the opening
 * screen of this tab is a question - who is missing - and twenty-one present names
 * are not the answer to it. The count stays in the heading either way.
 */
@Composable
private fun Disclosure(title: String, content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(title, Modifier.weight(1f))
            Text(
                text = if (open) "Hide" else "Show",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(visible = open) { Column(modifier = Modifier.animateContentSize()) { content() } }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * The month's SF2, folded to one line until it is wanted. It sits with the session
 * export because both are sheets the same teacher hands in, and it stays reachable
 * after the app is restarted - the sessions are on disk, not only in memory.
 */
@Composable
private fun Sf2Section(state: AppState) {
    val section = state.selectedSection ?: return
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable { open = !open },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SF2 school form",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (open) "Hide" else "Show",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    AnimatedVisibility(visible = open) {
        Column(modifier = Modifier.animateContentSize()) {
            Spacer(Modifier.height(8.dp))
            Sf2Body(state, section.name, state.sf2Months)
        }
    }
}

@Composable
private fun Sf2Body(state: AppState, sectionName: String, months: List<Sf2Report.Month>) {
    val chosen = months.firstOrNull { it.year * 100 + it.month == state.sf2MonthKey } ?: months.firstOrNull()
    Note(
        "One sheet per month, built from this class's sessions: blank is present, " +
            "x absent, L late, N no card yet."
    )
    Spacer(Modifier.height(10.dp))
    if (months.isEmpty()) {
        Note("No session recorded for " + sectionName + " yet.")
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            for (month in months) {
                FilterChip(
                    selected = month == chosen,
                    onClick = { state.chooseSf2Month(month.year * 100 + month.month) },
                    label = { Text(month.label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { state.exportSf2() },
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_export),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Export SF2 - " + (chosen?.label ?: "the month"))
        }
        ExportResult(
            path = state.sf2Path,
            note = state.sf2Error,
            onShare = state.sf2Uri?.let { uri -> { state.shareFile(uri) } },
        )
    }
}
