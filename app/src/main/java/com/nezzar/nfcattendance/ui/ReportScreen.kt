package com.nezzar.nfcattendance.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.ReportBuilder

@Composable
fun ReportScreen(state: AppState) {
    val resolved = state.resolved()
    val session = state.session
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
        } else {
            item(key = "stats") {
                BrandCard {
                    SectionLabel("Result")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Green marks the number this screen exists to produce, and it
                        // stays off while that number is still zero.
                        StatTile(
                            value = resolved.absent.size,
                            label = "absent",
                            modifier = Modifier.weight(1f),
                            accent = resolved.absent.isNotEmpty(),
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

            item(key = "absent-label") {
                SectionLabel("Absent: " + resolved.absent.size)
            }
            if (resolved.absent.isEmpty()) {
                item(key = "absent-none") {
                    Note("Everyone on the roster was present.")
                }
            }
            items(resolved.absent, key = { it.uid }) { student ->
                BrandCard(modifier = Modifier.animateItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(student.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = student.uid,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // A broken, lost or never-made card must not be able to turn a
                        // present student into an absent one. The sheet records which.
                        TextButton(onClick = { state.markPresent(student.uid) }) {
                            Text("Mark present")
                        }
                    }
                }
            }

            // A late arrival is counted, and counted as late: its own list, with the
            // minute it was read.
            if (resolved.late.isNotEmpty()) {
                item(key = "late-label") {
                    SectionLabel("Late: " + resolved.late.size)
                }
                items(resolved.late, key = { "late-" + it.student.uid }) { item ->
                    BrandCard(modifier = Modifier.animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.student.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = item.student.uid,
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
                items(resolved.present, key = { it.student.uid }) { item ->
                    BrandCard(modifier = Modifier.animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.student.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = item.student.uid,
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