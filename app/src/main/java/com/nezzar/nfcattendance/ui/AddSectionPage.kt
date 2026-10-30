package com.nezzar.nfcattendance.ui

import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.DocumentsExport
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.RosterImporter
import com.nezzar.nfcattendance.data.Sf2Form

/**
 * Everything that ADDS a section lives on this page, off the shelf: the class name,
 * the subject it is for, and an import of a roster another phone exported.
 */
@Composable
fun NewSectionScreen(state: AppState, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val pickWorkbook = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) state.stageImport(uri)
    }

    // The class name and subject live in the second card: keep them above the
    // keyboard when it opens.
    RevealTypingField(listState = listState, index = 1, active = true)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The shell header already says "New class"; this item keeps only the
        // sentence, so the screen does not say the same thing twice.
        item(key = "title") {
            Note("One class, one subject, one roster.")
        }

        item(key = "create") {
            BrandCard {
                SectionLabel("Class")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Class name (e.g. Grade 3 - Rizal)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g. Surveying)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Note(
                    "The subject says what the class is about. It travels with the roster file " +
                        "and shows under the class name."
                )
                Spacer(Modifier.height(12.dp))
                IfNameIsFree(state, name) {
                    Button(
                        onClick = {
                            state.createSection(name, subject)
                            if (state.sectionsError.isEmpty()) state.closeOverlay()
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        Text("Create class")
                    }
                }
                if (state.sectionsError.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Refused: " + state.sectionsError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item(key = "import") {
            BrandCard {
                SectionLabel("Import")
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        pickWorkbook.launch(
                            arrayOf(
                                DocumentsExport.MIME_XLSX,
                                DocumentsExport.MIME_XLS,
                                "application/octet-stream",
                                "*/*",
                            )
                        )
                    },
                    enabled = !state.importBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_import),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.importBusy) "Reading the file..."
                        else "Import a class from .xlsx or .xls"
                    )
                }
                Spacer(Modifier.height(8.dp))
                Note(
                    "Two kinds of file work: a roster another phone exported, and a DepEd SF2 " +
                        "school form. A form brings its learners' names with no cards bound - tap " +
                        "each card on Register to bind it."
                )
                if (state.importError.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.importError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        val plan = state.importPlan
        val parsed = state.importedFile
        if (plan != null && parsed != null) {
            item(key = "preview") { ImportPreview(state, plan, parsed) }
        }
        val form = state.importedSf2
        if (form != null) {
            item(key = "form") { Sf2Preview(state, form) }
        }

    }
}
/**
 * A duplicate class name is the one refusal that can be known before the button
 * is pressed, so the reason sits under the field instead of arriving as an error
 * after Save. The Button stays enabled-and-refusing for a blank name only.
 */
@Composable
private fun IfNameIsFree(state: AppState, name: String, content: @Composable () -> Unit) {
    val clash = if (name.isBlank()) null else state.sections.firstOrNull {
        it.name.equals(name.trim(), ignoreCase = true)
    }
    content()
    if (clash != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "You already have a class called " + clash.name + ".",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Sf2Preview(state: AppState, parsed: Sf2Form.Parsed) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appear,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            expandFrom = Alignment.Top,
        ) + fadeIn(tween(200)),
    ) {
        BrandCard {
            SectionLabel("School form preview")
            Spacer(Modifier.height(10.dp))
            KeyValueRow("File", state.importSourceName)
            KeyValueRow("School", parsed.schoolName.ifBlank { "(not named in the file)" })
            KeyValueRow("School ID", parsed.schoolId.ifBlank { "(none)" })
            KeyValueRow("School year", parsed.schoolYear.ifBlank { "(none)" })
            KeyValueRow("Grade level", parsed.gradeLevel.ifBlank { "(none)" })
            KeyValueRow("Section in the file", parsed.sectionLabel.ifBlank { "(none)" })
            KeyValueRow("Month", parsed.month.ifBlank { "(none)" })
            val male = parsed.students.count { it.sex == "M" }
            val female = parsed.students.count { it.sex == "F" }
            val unknown = parsed.students.size - male - female
            KeyValueRow(
                label = "Learners",
                value = parsed.students.size.toString() + "  ·  M " + male + "  ·  F " + female +
                    (if (unknown > 0) "  ·  sex not recorded " + unknown else "") +
                    (if (parsed.skipped.isNotEmpty()) "  ·  skipped " + parsed.skipped.size else ""),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.importTargetName,
                onValueChange = { state.importSectionNameChanged(it) },
                label = { Text("Class name to import into") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            val target = state.sections.firstOrNull {
                it.name.equals(state.importTargetName.trim(), ignoreCase = true)
            }
            Text(
                text = if (target == null) {
                    "Create " + state.importTargetName.trim() + " from these learners."
                } else {
                    "Merge into " + target.name + " - only names it does not already have are added."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Note("Their cards are not bound yet. Tap a card on Register and choose the learner it belongs to.")
            if (parsed.skipped.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                for (skip in parsed.skipped.take(6)) {
                    Text(
                        text = skip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (parsed.skipped.size > 6) {
                    Text(
                        text = "and " + (parsed.skipped.size - 6) + " more skipped",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = {
                    state.confirmSf2Import()
                    state.closeOverlay()
                }) {
                    Text("Import")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { state.cancelImport() }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ImportPreview(state: AppState, plan: RosterImporter.Plan, parsed: RosterImporter.Parsed) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appear,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            expandFrom = Alignment.Top,
        ) + fadeIn(tween(200)),
    ) {
        BrandCard {
            SectionLabel("Import preview")
            Spacer(Modifier.height(10.dp))
            KeyValueRow("File", state.importSourceName)
            KeyValueRow(
                label = "Section in the file",
                value = if (parsed.sectionName.isBlank()) "(not named in the file)" else parsed.sectionName,
            )
            KeyValueRow(
                label = "Subject in the file",
                value = if (parsed.subject.isBlank()) "(none)" else parsed.subject,
            )
            KeyValueRow("Updated in the file", ReportBuilder.dateUpdatedText(parsed.updatedAt))
            KeyValueRow(
                label = "Student rows",
                value = parsed.rows.size.toString() + "  ·  skipped " + parsed.skipped.size,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.importTargetName,
                onValueChange = { state.importSectionNameChanged(it) },
                label = { Text("Section name to import into") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            if (plan.error.isNotEmpty()) {
                Text(
                    text = "Refused: " + plan.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    text = if (plan.mergeIntoExisting) {
                        "Merge into " + plan.targetName + " - nothing already there is removed."
                    } else {
                        "Create " + plan.targetName + " from these students."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Note(
                    plan.added.size.toString() + " added, " + plan.renamed.size + " renamed, " +
                        plan.unchanged.size + " unchanged, " + plan.keptLocally.size + " kept, " +
                        plan.skipped.size + " skipped"
                )
                Spacer(Modifier.height(10.dp))
                var index = 0
                for (row in plan.added) {
                    StaggeredRow(index++) {
                        Text(
                            text = "add " + row.name + "   " + row.uid,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                for ((row, previous) in plan.renamed) {
                    StaggeredRow(index++) {
                        Text(
                            text = "name update " + row.uid + ": " + previous + " -> " + row.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                for (student in plan.keptLocally) {
                    StaggeredRow(index++) {
                        Text(
                            text = "kept (not in the file) " + student.name + "   " + student.uid,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                for (skip in plan.skipped) {
                    StaggeredRow(index++) {
                        Text(
                            text = "skipped row " + skip.rowNumber + ": " + skip.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow(
                    label = "Updated after import",
                    value = ReportBuilder.dateUpdatedText(plan.resultingUpdatedAt),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = {
                    state.confirmImport()
                    state.closeOverlay()
                }) {
                    Text("Import")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = { state.cancelImport() }) { Text("Cancel") }
            }
        }
    }
}