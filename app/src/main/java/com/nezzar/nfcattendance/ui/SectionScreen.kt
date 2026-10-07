package com.nezzar.nfcattendance.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.PlayingCards
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.VisualStyle

/**
 * Everything that MANAGES one class, on a page of its own: the card as the shelf
 * draws it, the face it is drawn with, the name, the subject that travels with the
 * roster file, and the way to delete it. Reached from the Manage control on the
 * section card itself, so the shelf stays a shelf and the working tabs stay
 * uncluttered.
 *
 * The whole page is ONE draft and ONE Save, and Save sits in the header at the
 * top right: three fields that each saved themselves made it hard to tell what
 * had been written and what had not.
 */
@Composable
fun SectionScreen(state: AppState, modifier: Modifier = Modifier) {
    val section = state.selectedSection
    val listState = rememberLazyListState()

    var name by remember { mutableStateOf(section?.name ?: "") }
    var subject by remember { mutableStateOf(section?.subject ?: "") }
    var suit by remember { mutableStateOf(section?.card?.let { PlayingCards.suit(it) } ?: "") }
    var rank by remember { mutableStateOf(section?.card?.let { PlayingCards.rank(it) } ?: "") }
    var justSaved by remember { mutableStateOf(false) }

    val save = {
        state.saveSectionEdits(name, subject, suit, rank)
        justSaved = state.sectionsError.isEmpty()
    }
    // The header holds the button, this page holds the draft, so the button is
    // registered once and reads the latest draft through this holder.
    val latestSave = rememberUpdatedState(save)
    val hasSection = section != null
    DisposableEffect(hasSection) {
        if (hasSection) state.setHeaderAction("SAVE") { latestSave.value() }
        onDispose { state.setHeaderAction(null, null) }
    }

    // Name and Subject are the two cards worth typing on: keep them in front of
    // the keyboard when it opens.
    RevealTypingField(listState = listState, index = if (hasSection) 2 else null, active = hasSection)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            CollapsingTitle(
                title = section?.name ?: "Section",
                subtitle = "Its name, its subject and its face - then Save.",
                listState = listState,
            )
        }

        if (section == null) {
            item(key = "empty") {
                EmptyState(
                    icon = R.drawable.ic_add,
                    title = "No section selected",
                    body = "Swipe the shelf to a class first, then press Manage on its card.",
                    actionLabel = "Back to the sections",
                    onAction = { state.closeOverlay() },
                )
            }
        } else {
            item(key = "face") { SectionFaceCard(state) }

            // The student list used to hang under the shelf. It is part of managing a
            // class, so it lives on the class's own page now.
            item(key = "roster") {
                val rosterInteraction = remember { MutableInteractionSource() }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale(rosterInteraction)
                        .cardClick(rosterInteraction, onClick = { state.openRoster() }),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Registered students",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron),
                            contentDescription = "Open all registered students",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "name") {
                NameCard(name) {
                    name = it
                    justSaved = false
                }
            }
            item(key = "subject") {
                SubjectCard(subject) {
                    subject = it
                    justSaved = false
                }
            }

            // The name and the subject come first: they are what a class is, while
            // the face is decoration - and it only shows on the shelf in the Cards
            // style, so it can only be picked there either.
            if (state.visualStyle == VisualStyle.CARDS) {
                item(key = "cardface") {
                    FaceCard(
                        suit = suit,
                        rank = rank,
                        onSuit = {
                            suit = it
                            justSaved = false
                        },
                        onRank = {
                            rank = it
                            justSaved = false
                        },
                    )
                }
            }

            val error = state.sectionsError
            if (error.isNotEmpty()) {
                item(key = "refused", contentType = "saveNote") {
                    Text(
                        text = "Not saved. " + error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.animateItem(),
                    )
                }
            } else if (justSaved) {
                item(key = "saved", contentType = "saveNote") {
                    Text(
                        text = "Saved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "delete") { DeleteCard(state, section) }
        }
    }
}

@Composable
private fun FaceCard(
    suit: String,
    rank: String,
    onSuit: (String) -> Unit,
    onRank: (String) -> Unit,
) {
    BrandCard {
        SectionLabel("Card face")
        Spacer(Modifier.height(10.dp))
        FacePicker(
            suit = suit,
            rank = rank,
            onSuit = onSuit,
            onRank = onRank,
        )
        Spacer(Modifier.height(8.dp))
        Note("No two classes hold the same face, so a face already on the shelf is refused.")
    }
}

@Composable
private fun NameCard(name: String, onName: (String) -> Unit) {
    BrandCard {
        SectionLabel("Name")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Section name (e.g. BSCE-4B)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SubjectCard(subject: String, onSubject: (String) -> Unit) {
    BrandCard {
        SectionLabel("Subject")
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = subject,
            onValueChange = onSubject,
            label = { Text("Subject (e.g. Surveying)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Note("It travels with the roster file and shows on the section card.")
    }
}

@Composable
private fun DeleteCard(state: AppState, section: Section) {
    var confirm by remember(section.name) { mutableStateOf(false) }
    BrandCard {
        SectionLabel("Delete")
        Spacer(Modifier.height(10.dp))
        // Asking twice must not feel like the card was swapped out from under the
        // thumb: the question cross-fades in where the button was.
        AnimatedContent(
            targetState = confirm,
            transitionSpec = {
                (fadeIn(tween(MotionTouchMs, easing = EmphasizedDecelerate)) +
                    slideInVertically(
                        animationSpec = tween(MotionScreenMs, easing = EmphasizedDecelerate),
                    ) { height -> height / 4 })
                    .togetherWith(fadeOut(tween(140)))
            },
            label = "deleteConfirm",
        ) { confirming ->
            Column {
                if (!confirming) {
                    Note("Registered students cannot be recovered.")
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { confirm = true }) { Text("Delete this section") }
                } else {
                    Note(
                        "Delete " + section.name + " and its " + section.students.size +
                            " registered student(s)?"
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(
                            onClick = {
                                state.deleteSection(section.name)
                                state.closeOverlay()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text("Yes, delete")
                        }
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { confirm = false }) { Text("Keep it") }
                    }
                }
            }
        }
    }
}
