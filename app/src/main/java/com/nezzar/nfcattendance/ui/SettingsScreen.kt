package com.nezzar.nfcattendance.ui

import com.nezzar.nfcattendance.BuildConfig
import com.nezzar.nfcattendance.data.VisualStyle
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nezzar.nfcattendance.data.HapticStrength
import com.nezzar.nfcattendance.ui.theme.ThemeMode

/**
 * The page that takes the rarely-touched controls off the four working screens:
 * appearance, UID byte order (two cards' worth of clutter before this existed)
 * and the data facts a person asks about once.
 */
@Composable
fun SettingsScreen(state: AppState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            CollapsingTitle(
                title = "Settings",
                subtitle = "Appearance, card reading and where your data lives.",
                listState = listState,
            )
        }

        item(key = "appearance") {
            BrandCard {
                SectionLabel("Appearance")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        }
                        val selected = state.themeMode == mode
                        if (selected) {
                            Button(
                                onClick = { state.useThemeMode(mode) },
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = { state.useThemeMode(mode) },
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Note("System follows the phone's own day/night setting.")
            }
        }

        item(key = "late") {
            BrandCard {
                SectionLabel("Late after")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 20, 30).forEach { minutes ->
                        val selected = state.lateAfterMinutes == minutes
                        if (selected) {
                            Button(
                                onClick = { state.useLateAfterMinutes(minutes) },
                                modifier = Modifier.weight(1f),
                            ) { Text(minutes.toString(), maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = { state.useLateAfterMinutes(minutes) },
                                modifier = Modifier.weight(1f),
                            ) { Text(minutes.toString(), maxLines = 1) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Note(
                    "Minutes after the session start that still count as present. A card read after " +
                        "that is counted too, but marked late - which is why the session can be paused " +
                        "and left open for the rest of the class."
                )
            }
        }

        item(key = "style") {
            BrandCard {
                SectionLabel("Section style")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        VisualStyle.CARDS to "Cards",
                        VisualStyle.PLAIN to "Plain",
                        VisualStyle.SOLIDS to "Solids",
                    ).forEach { (style, label) ->
                        val chosen = state.visualStyle == style
                        if (chosen) {
                            Button(
                                onClick = { state.useVisualStyle(style) },
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = { state.useVisualStyle(style) },
                                modifier = Modifier.weight(1f),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Note(
                    "Cards is the default: every class is dealt a face from a 52-card deck, " +
                        "drawn dark and subtle with green shapes. Plain is the civil engineering " +
                        "sheet - a drafting grid with a truss mark and a title block. Solids gives " +
                        "each class a wireframe polyhedron - prism, cube, octahedron, icosahedron " +
                        "and so on."
                )
            }
        }

        item(key = "feedback") {
            BrandCard {
                SectionLabel("Scan feedback")
                Spacer(Modifier.height(10.dp))
                Text("Vibration", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                // Two rows of two: four buttons in one row truncate their labels.
                listOf(
                    listOf(HapticStrength.OFF, HapticStrength.LIGHT),
                    listOf(HapticStrength.NORMAL, HapticStrength.STRONG),
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { strength ->
                            val label = when (strength) {
                                HapticStrength.OFF -> "Off"
                                HapticStrength.LIGHT -> "Light"
                                HapticStrength.NORMAL -> "Normal"
                                HapticStrength.STRONG -> "Strong"
                            }
                            val selected = state.hapticStrength == strength
                            if (selected) {
                                Button(
                                    onClick = { state.useHapticStrength(strength) },
                                    modifier = Modifier.weight(1f),
                                ) { Text(label) }
                            } else {
                                OutlinedButton(
                                    onClick = { state.useHapticStrength(strength) },
                                    modifier = Modifier.weight(1f),
                                ) { Text(label) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.scanSound,
                        onCheckedChange = { state.useScanSound(it) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Beep when a card is read",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Note(
                    "Vibration rides the phone's own haptic feedback, so it asks for no extra " +
                        "permission. The beep is a single short tone, played only when a card is read."
                )
            }
        }

        item(key = "byteorder") {
            BrandCard {
                SectionLabel("Card UID byte order")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.uidReversed,
                        onCheckedChange = { state.useReversedUid(it) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Use the reversed reading",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Note(
                    "Android can print a card's UID in either byte order. Tap a card on the Scan tab " +
                        "and pick whichever reading below matches your roster."
                )
                Spacer(Modifier.height(8.dp))
                if (state.recentTaps.isEmpty()) {
                    Note("No taps yet.")
                } else {
                    for (tap in state.recentTaps) {
                        Text(
                            text = "as-read " + tap.asRead + "   |   reversed " + tap.reversed,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item(key = "data") {
            val cards = state.sections.sumOf { it.students.size }
            BrandCard {
                SectionLabel("Your data")
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Sections", state.sections.size.toString())
                KeyValueRow("Registered students", cards.toString())
                KeyValueRow("Exports", "Documents/NFC Attendance")
                Spacer(Modifier.height(8.dp))
                Note(
                    "Names and card UIDs live in this app's private storage only. There is no server " +
                        "and no network permission, so nothing is uploaded."
                )
            }
        }

        item(key = "guide") {
            BrandCard {
                SectionLabel("New here?")
                Spacer(Modifier.height(8.dp))
                Note("The four steps of a class session, in order, are on the How to use page.")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { state.openOverlay(Overlay.TUTORIAL) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open How to use") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { state.showTutorialOnNextLaunch() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Show it at startup again") }
                if (state.tutorialQueued) {
                    Spacer(Modifier.height(8.dp))
                    Note("Queued - the guide opens the next time the app starts.")
                }
            }
        }

        item(key = "about") {
            BrandCard {
                SectionLabel("About")
                Spacer(Modifier.height(8.dp))
                KeyValueRow("App", "NFC Attendance Checker")
                KeyValueRow("Version", BuildConfig.VERSION_NAME)
                KeyValueRow("Works without", "Internet, account, login")
                Spacer(Modifier.height(8.dp))
                Note(
                    "Students are identified by card UID and name only - no student number is ever " +
                        "recorded (RA 10173)."
                )
            }
        }
    }
}