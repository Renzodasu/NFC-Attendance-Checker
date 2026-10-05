package com.nezzar.nfcattendance.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.Student

/**
 * Every registered card of the selected section, on one page. The Sections list
 * keeps a single button instead of the whole roster, so that screen stays short;
 * a tap here opens the student page for editing.
 *
 * When a session is running (or just finished) each row also says where that
 * student stood in it: on time, late with the clock time, or absent.
 */
@Composable
fun RosterScreen(state: AppState, modifier: Modifier = Modifier) {
    val section = state.selectedSection
    val students = section?.students ?: emptyList()
    val resolved = state.resolved()
    var filter by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val shown = if (filter.isBlank()) {
        students
    } else {
        students.filter {
            it.name.contains(filter, ignoreCase = true) || it.uid.contains(filter, ignoreCase = true)
        }
    }

    // A long roster grows a search box; keep it clear of the keyboard.
    RevealTypingField(
        listState = listState,
        index = if (students.size > 8) 2 else null,
        active = students.size > 8,
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            CollapsingTitle(
                title = section?.name ?: "Registered students",
                subtitle = students.size.toString() +
                    (if (students.size == 1) " student" else " students") +
                    " registered. Tap one to edit or remove them.",
                listState = listState,
            )
        }

        if (students.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = R.drawable.ic_nav_register,
                    title = "No students registered yet",
                    body = "Open Register, tap a student ID, and type the name it belongs to.",
                    actionLabel = "Go to Register",
                    onAction = { state.closeOverlay(); state.screen = Screen.REGISTER },
                )
            }
        } else {
            item(key = "export") {
                BrandCard {
                    SectionLabel("Export")
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { state.exportRoster() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_export),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Export this roster .xlsx")
                    }
                    ExportResult(
                        path = state.rosterPath,
                        note = state.rosterError,
                        onShare = state.rosterUri?.let { uri -> { state.shareFile(uri) } },
                    )
                }
            }

            if (students.size > 8) {
                item(key = "filter") {
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text("Find by name or UID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item(key = "count") {
                SectionLabel("Showing: " + shown.size + " of " + students.size)
            }

            items(shown, key = { it.uid }) { student ->
                RosterRow(
                    student = student,
                    status = statusOf(student, resolved),
                    onOpen = { state.openStudent(student.uid) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** "on time 07:42:10", "late 08:05:44" or "absent" - only while a session knows. */
private fun statusOf(
    student: Student,
    resolved: com.nezzar.nfcattendance.data.ResolvedAttendance?,
): String? {
    val current = resolved ?: return null
    current.present.firstOrNull { it.student.uid == student.uid }?.let {
        return "on time " + ReportBuilder.timeText(it.firstTapMillis)
    }
    current.late.firstOrNull { it.student.uid == student.uid }?.let {
        return "late " + ReportBuilder.timeText(it.firstTapMillis)
    }
    return "absent"
}

@Composable
private fun RosterRow(
    student: Student,
    status: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .cardClick(interaction, onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = student.uid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status != null) {
                        Spacer(Modifier.width(8.dp))
                        Pill(text = status)
                    }
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron),
                contentDescription = "Open " + student.name,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
