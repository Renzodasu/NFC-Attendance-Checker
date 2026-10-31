package com.nezzar.nfcattendance.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.Sf2Report

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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            CollapsingTitle(
                title = "Session report",
                subtitle = "The absent list is the conclusion. Export when you are done.",
                listState = listState,
            )
        }

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
            item(key = "sf2") { Sf2Item(state) }
        } else {
            // The report opens on the class, not on a number: whose session this is has
            // to be readable before the tiles are read.
            item(key = "class") {
                ClassIdentity(
                    name = session.sectionName,
                    subject = state.sections.firstOrNull { it.name == session.sectionName }?.subject,
                    selected = true,
                    badgeSize = 52.dp,
                    nameStyle = MaterialTheme.typography.headlineSmall,
                )
            }

            // The conclusion comes before the numbers: this screen is opened to read
            // names, and the names to read are the students who were not in the room.
            item(key = "absent-label") {
                SectionLabel("Absent: " + resolved.absent.size)
            }
            if (resolved.absent.isEmpty()) {
                item(key = "absent-none") {
                    Note("Everyone on the roster was present.")
                }
            }
            itemsIndexed(resolved.absent, key = { index, student -> "absent-" + index + "-" + student.name }) { _, student ->
                BrandCard(modifier = Modifier.animateItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(student.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (student.uid.isBlank()) "No card yet" else student.uid,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // A broken, lost or never-made card must not be able to turn a
                        // present student into an absent one. The sheet records which.
                        TextButton(onClick = { state.markPresent(student) }) {
                            Text("Mark present")
                        }
                    }
                }
            }

            item(key = "stats") {
                BrandCard {
                    SectionLabel("Result")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Amber marks the number this screen exists to produce, and it
                        // stays off while that number is still zero.
                        StatTile(
                            value = resolved.absent.size,
                            label = "absent",
                            modifier = Modifier.weight(1f),
                            warning = resolved.absent.isNotEmpty(),
                        )
                        StatTile(
                            value = resolved.late.size,
                            label = "late",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            value = resolved.present.size,
                            label = "present",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = resolved.unmatched.size,
                            label = "unmatched",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    KeyValueRow("Section", session.sectionName)
                    KeyValueRow("Session", session.sessionId)
                    KeyValueRow("Started", ReportBuilder.stampText(session.startedAtMillis))
                }
            }

            // The tiles state the conclusion; the chart explains the same numbers one
            // line down, and Export stays the screen's one action below it.
            item(key = "chart") {
                ReportChartCard(state = state, resolved = resolved, session = session)
            }

            // The one action this screen owns sits above the lists, not below them.
            item(key = "export") {
                BrandCard(modifier = Modifier.animateContentSize()) {
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
                        Text("Export .xlsx report")
                    }
                    ExportResult(
                        path = state.reportPath,
                        note = state.reportError,
                        onShare = state.reportUri?.let { uri -> { state.shareFile(uri) } },
                    )
                }
            }

            // The school form lives with the session export: both are sheets for the
            // same teacher, and neither is hidden below the attendance lists.
            item(key = "sf2") { Sf2Item(state) }

            // A late arrival is counted, and counted as late: its own list, with the
            // minute it was read.
            if (resolved.late.isNotEmpty()) {
                item(key = "late-label") {
                    SectionLabel("Late: " + resolved.late.size)
                }
                itemsIndexed(resolved.late, key = { index, item -> "late-" + index + "-" + item.student.name }) { _, item ->
                    BrandCard(modifier = Modifier.animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.student.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (item.student.uid.isBlank()) "No card yet" else item.student.uid,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = ReportBuilder.timeText(item.firstTapMillis),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = ReportBuilder.dateText(item.firstTapMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Supporting data is only drawn when there is some - two headings over
            // nothing used to push the export button off the screen.
            if (resolved.present.isNotEmpty()) {
                item(key = "present-label") {
                    SectionLabel("Present: " + resolved.present.size)
                }
                itemsIndexed(resolved.present, key = { index, item -> "present-" + index + "-" + item.student.name }) { _, item ->
                    BrandCard(modifier = Modifier.animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.student.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = if (item.student.uid.isBlank()) "No card yet" else item.student.uid,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // When that card was scanned: the point of the whole sheet.
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = ReportBuilder.timeText(item.firstTapMillis),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = ReportBuilder.dateText(item.firstTapMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (resolved.unmatched.isNotEmpty()) {
                item(key = "unmatched-label") {
                    SectionLabel("Unmatched taps: " + resolved.unmatched.size)
                    Spacer(Modifier.height(4.dp))
                    Note("A card that is not on this roster - flagged, never dropped.")
                }
                items(resolved.unmatched, key = { it.uid + it.atMillis.toString() }) { tap ->
                    BrandCard(modifier = Modifier.animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tap.uid,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = ReportBuilder.timeText(tap.atMillis),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

        }
    }
}

/**
 * The month's SF2, offered whenever a class is chosen. It sits beside the session
 * export because both are sheets the same teacher hands in, and it stays reachable
 * after the app is restarted - the sessions are on disk, not only in memory.
 */
@Composable
private fun Sf2Item(state: AppState) {
    val section = state.selectedSection
    if (section != null) {
        Sf2Card(state, section.name, state.sf2Months)
    }
}

@Composable
private fun Sf2Card(state: AppState, sectionName: String, months: List<Sf2Report.Month>) {
    val chosen = months.firstOrNull { it.year * 100 + it.month == state.sf2MonthKey } ?: months.firstOrNull()
    BrandCard {
        SectionLabel("SF2 school form")
        Spacer(Modifier.height(10.dp))
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
}