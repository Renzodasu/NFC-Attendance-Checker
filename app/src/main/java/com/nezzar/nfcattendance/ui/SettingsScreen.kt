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
import com.nezzar.nfcattendance.data.ChartKind
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
                Points(
                    listOf(
                        "Present - a card read in the first " + state.lateAfterMinutes + " minutes.",
                        "Late - a card read after that. It still counts, it is only marked.",
                        "Pause instead of ending: the session stays open, so latecomers are still tapped.",
                        "Applies to the sessions you start from now on - one already running, or already exported, keeps the window it began with.",
                    )
                )
            }
        }

        item(key = "chart") {
            BrandCard {
                SectionLabel("Chart")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartKind.entries.forEach { kind ->
                        val chosen = state.chartKind == kind
                        val pick = { state.useChartKind(kind) }
                        if (chosen) {
                            Button(
                                onClick = pick,
                                modifier = Modifier.weight(1f),
                            ) { Text(kind.label, maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = pick,
                                modifier = Modifier.weight(1f),
                            ) { Text(kind.label, maxLines = 1) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Note(
                    "Pie shows the attendance itself: on time, late and absent. Line shows " +
                        "when the room filled, with the late mark. Bar shows how each presence " +
                        "was recorded: NFC, QR or by hand."
                )
                Spacer(Modifier.height(8.dp))
                Note("This changes the chart on the report only. The numbers and the exported sheet are the same either way.")
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
            }
        }

        item(key = "about") {
            val cards = state.sections.sumOf { it.students.size }
            BrandCard {
                SectionLabel("About")
                Spacer(Modifier.height(8.dp))
                Text("Presencia NFC", style = MaterialTheme.typography.titleSmall)
                Note("Tap your ID. Be Present.")
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Version", BuildConfig.VERSION_NAME)
                KeyValueRow("Sections", state.sections.size.toString())
                KeyValueRow("Registered students", cards.toString())
                KeyValueRow("Exports", "Documents/Presencia")
                KeyValueRow("Reads", "NFC cards and QR codes")
                KeyValueRow("Camera", "used only to decode a code, in memory")
                KeyValueRow("Works without", "Internet, account, login")
                Spacer(Modifier.height(8.dp))
                Points(
                    listOf(
                        "Names and card UIDs live in this app's private storage only.",
                        "The app holds no network permission, so a roster cannot leave the phone by itself.",
                        "A student is a card UID and a name - no student number is recorded (RA 10173).",
                    )
                )
            }
        }

        // Real controls, but cosmetic or calibration: not what Settings is opened for.
        item(key = "advanced") {
            BrandCard {
                SectionLabel("Advanced")
                Spacer(Modifier.height(10.dp))
                Text("Section style", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.uidReversed,
                        onCheckedChange = { state.useReversedUid(it) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Use the reversed UID reading",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.recentTaps.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    for (tap in state.recentTaps) {
                        Text(
                            text = "as-read " + tap.asRead + "   |   " + tap.reversed,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Note("Only if your cards read backwards: tap one on Scan, then compare the codes here.")
                }
            }
        }
    }
}