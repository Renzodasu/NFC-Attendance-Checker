package com.nezzar.nfcattendance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R

/**
 * One student, full screen. The roster row only opens this page, so nothing
 * destructive or fiddly sits inside a list the finger is trying to scroll.
 */
@Composable
fun StudentScreen(state: AppState, uid: String, modifier: Modifier = Modifier) {
    val section = state.selectedSection
    val student = section?.students?.firstOrNull { it.uid == uid }
    var name by remember(uid, student?.name) { mutableStateOf(student?.name ?: "") }
    var confirmRemove by remember(uid) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // The Name card is the second block on the page: keep it clear of the keyboard.
    RevealTypingField(listState = listState, index = 2, active = student != null)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            CollapsingTitle(
                title = student?.name ?: "Student",
                subtitle = "The card UID is what the phone reads. The name is just a label.",
                listState = listState,
            )
        }

        if (student == null) {
            item(key = "gone") {
                EmptyState(
                    icon = R.drawable.ic_nav_sections,
                    title = "That card is not on this roster",
                    body = "It may have been removed, or another section is selected.",
                    actionLabel = "Back to the list",
                    onAction = { state.closeOverlay() },
                )
            }
        } else {
            item(key = "card") {
                BrandCard {
                    SectionLabel("Card")
                    Spacer(Modifier.height(8.dp))
                    Text(text = student.uid, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Note(
                        "Registered in " + (section?.name ?: "this section") +
                            ". This UID is what the phone reads when the card is tapped."
                    )
                }
            }

            item(key = "name") {
                BrandCard {
                    SectionLabel("Name")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Student name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                state.renameStudent(uid, name)
                                if (state.sectionsError.isEmpty()) state.closeOverlay()
                            },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text("Save name")
                        }
                        OutlinedButton(
                            onClick = { state.closeOverlay() },
                            modifier = Modifier.weight(1f).height(52.dp),
                        ) {
                            Text("Cancel")
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

            item(key = "remove") {
                BrandCard {
                    SectionLabel("Remove from roster")
                    Spacer(Modifier.height(8.dp))
                    if (!confirmRemove) {
                        Note("The card becomes free to register to someone else. This cannot be undone.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { confirmRemove = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Remove " + student.name)
                        }
                    } else {
                        Note(
                            "Remove " + student.name + "? Card " + student.uid +
                                " becomes free to register to another student."
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    state.removeStudent(uid)
                                    state.closeOverlay()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) {
                                Text("Yes, remove")
                            }
                            OutlinedButton(onClick = { confirmRemove = false }) { Text("Keep") }
                        }
                    }
                }
            }
        }

        item(key = "back") {
            Button(onClick = { state.closeOverlay() }, modifier = Modifier.fillMaxWidth()) {
                Text("Back to the list")
            }
        }
    }
}
