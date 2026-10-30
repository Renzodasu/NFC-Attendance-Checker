package com.nezzar.nfcattendance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.Section

/**
 * The class list, as a grid of plates rather than one full-screen card per swipe.
 *
 * 0.0.6 replaced the playing-card shelf: a face had to be memorised, it competed
 * with the learner's own card for the same word, and it was invisible on the two
 * screens where identity actually matters. A plate carries the class NAME at
 * reading size, the subject as one grey line, a two-letter mark derived from the
 * name, and the roster count - and all of them are legible at a glance, because
 * this screen is used while standing in front of forty students.
 *
 * Selection is still the app's one live state: the chosen plate is the only one
 * with a green edge and a green mark.
 */
@Composable
fun SectionsScreen(state: AppState) {
    val listState = rememberLazyListState()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val needle = query.trim().lowercase()
    val matches = if (needle.isEmpty()) {
        emptyList()
    } else {
        state.sections.filter { section ->
            section.name.lowercase().contains(needle) || section.subject.lowercase().contains(needle)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            CollapsingTitle(
                title = "My classes",
                subtitle = "One roster per class. Register students, then scan attendance.",
                listState = listState,
            )
        }

        // A file that exists but cannot be read is NOT an empty app. Saying so - and
        // refusing to write - is the whole difference between a warning and data loss.
        state.sectionsBroken?.let { broken ->
            item(key = "broken") {
                BrandCard {
                    SectionLabel("Your class list could not be read")
                    Spacer(Modifier.height(8.dp))
                    Text(broken, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Note("Nothing has been saved over it. The file is still on the phone, exactly as it was.")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { state.retryLoadSections() },
                            modifier = Modifier.weight(1f).height(54.dp),
                        ) { Text("Try again") }
                        OutlinedButton(
                            onClick = { state.startFreshAfterUnreadable() },
                            modifier = Modifier.weight(1f).height(54.dp),
                        ) { Text("Start fresh") }
                    }
                }
            }
        }

        if (state.sections.isEmpty() && state.sectionsBroken == null) {
            item(key = "first-run") {
                EmptyState(
                    icon = R.drawable.ic_add,
                    title = "No classes yet",
                    body = "A class is one roster. Create the first one and register its students.",
                    actionLabel = "Create your first class",
                    onAction = { state.openNewSection() },
                )
            }
        }

        if (state.sections.isNotEmpty()) {
            item(key = "header") {
                if (searching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search classes") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { if (query.isEmpty()) searching = false else query = "" },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = "Clear the search",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                    )
                    LaunchedEffect(searching) {
                        searchFocus.requestFocus()
                        keyboard?.show()
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(state.sections.size.toString() +
                            if (state.sections.size == 1) " class" else " classes")
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { searching = true },
                            modifier = Modifier.size(46.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = "Search classes",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { state.openNewSection() },
                            modifier = Modifier.height(46.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("New class")
                        }
                    }
                }
            }

            if (searching && needle.isNotEmpty()) {
                item(key = "search-results") {
                    BrandCard {
                        if (matches.isEmpty()) {
                            Note("No class matches \"" + query.trim() + "\".")
                        } else {
                            SectionLabel(matches.size.toString() + " match" + if (matches.size == 1) "" else "es")
                            Spacer(Modifier.height(4.dp))
                            matches.forEach { section ->
                                SearchRow(
                                    section = section,
                                    onClick = {
                                        query = ""
                                        searching = false
                                        state.selectSection(section.name)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "grid") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.sections.chunked(2).forEach { pair ->
                        // IntrinsicSize.Min ties the two plates to the taller one, so a
                        // two-line class name grows BOTH plates instead of clipping the
                        // roster count inside one of them.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.height(IntrinsicSize.Min),
                        ) {
                            pair.forEach { section ->
                                ClassPlate(
                                    section = section,
                                    selected = section.name == state.selectedName,
                                    onSelect = { state.selectSection(section.name) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (state.sections.isNotEmpty()) {
            item(key = "actions") {
                val section = state.selectedSection
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            section?.let { state.selectSection(it.name) }
                            state.openRegister()
                        },
                        enabled = section != null,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_register),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Register")
                    }
                    OutlinedButton(
                        onClick = {
                            section?.let { state.selectSection(it.name) }
                            state.openSection()
                        },
                        enabled = section != null,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_edit),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Edit class")
                    }
                }
            }
        }
    }
}

/**
 * One class, as a plate: mark, name, subject, roster count. The name is the
 * biggest thing on it - that is the whole point of replacing the card face.
 */
@Composable
private fun ClassPlate(
    section: Section,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 168.dp)
            .pressScale(interaction)
            .cardClick(interaction, onClick = onSelect),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            ClassBadge(name = section.name, selected = selected, size = 46.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = section.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (section.subject.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = section.subject,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (section.students.size == 1) "1 student" else section.students.size.toString() + " students",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** One search hit: the same mark and name, squeezed onto a row. */
@Composable
private fun SearchRow(section: Section, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .cardClick(interaction, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        ClassBadge(name = section.name, selected = false, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (section.subject.isNotBlank()) {
                Text(
                    text = section.subject,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
