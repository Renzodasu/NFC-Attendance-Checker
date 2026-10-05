package com.nezzar.nfcattendance.ui

import com.nezzar.nfcattendance.data.VisualStyle
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

/**
 * Everything that ADDS a section lives on this page, off the shelf: the class name,
 * the subject it is for, and an import of a roster another phone exported.
 */
@Composable
fun NewSectionScreen(state: AppState, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var suit by remember { mutableStateOf("") }
    var rank by remember { mutableStateOf("") }
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
        item(key = "title") {
            CollapsingTitle(
                title = "New section",
                subtitle = "One class, one subject, one roster.",
                listState = listState,
            )
        }

        item(key = "create") {
            BrandCard {
                SectionLabel("Class")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Section name (e.g. BSCE-4B)") },
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
                        "and shows on the section card."
                )
                if (state.visualStyle == VisualStyle.CARDS) {
                    Spacer(Modifier.height(14.dp))
                    SectionLabel("Card face")
                    Spacer(Modifier.height(8.dp))
                    // Two short fields; tapping either one reveals a single box
                    // holding every symbol and every number, so a whole face is
                    // set in one visit instead of two separate lists.
                    FacePicker(
                        suit = suit,
                        rank = rank,
                        onSuit = { suit = it },
                        onRank = { rank = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    Note(
                        "Pick a symbol and a number, or leave both on Any and the app deals " +
                            "one no other class is holding."
                    )
                }
                Spacer(Modifier.height(12.dp))
                val chosenFace = rank + suit
                val heldBy = if (chosenFace.length > 1) {
                    state.sections.firstOrNull { it.card == chosenFace }?.name
                } else {
                    null
                }
                Button(
                    onClick = {
                        state.createSection(name, subject, chosenFace)
                        if (state.sectionsError.isEmpty()) state.closeOverlay()
                    },
                    enabled = heldBy == null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text("Create section")
                }
                if (heldBy != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = heldBy + " already holds " + chosenFace + ". Pick another face.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
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
                                "application/octet-stream",
                                "*/*",
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_import),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Import a section from .xlsx")
                }
                Spacer(Modifier.height(8.dp))
                Note("Cards are matched by UID, and the subject in the file comes with them.")
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

        item(key = "close") {
            TextButton(onClick = { state.closeOverlay() }, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
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