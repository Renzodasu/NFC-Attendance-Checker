package com.nezzar.nfcattendance.ui

import android.app.Activity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.NfcAlarm
import com.nezzar.nfcattendance.data.nfcAlarm
import com.nezzar.nfcattendance.data.readerLine

@Composable
fun RegisterScreen(state: AppState, activity: Activity, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    // AppRoot owns the reader and reports what the hardware is doing: one source of
    // truth, so the light and the sentence below can never disagree.
    val nfc = state.nfcState

    val register = state.registerState
    val section = state.selectedSection
    var nameDraft by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(register.pendingUid) {
        if (register.pendingUid != null) {
            nameDraft = ""
            focus.requestFocus()
            try {
                WindowCompat.getInsetsController(activity.window, view)
                    .show(WindowInsetsCompat.Type.ime())
            } catch (t: Throwable) {
                // The keyboard is a convenience: never let it break registration.
            }
        }
    }

    // The editor is the last card in this list, which is exactly where the
    // keyboard lands: lift it to the top of the viewport as the keyboard opens.
    RevealTypingField(
        listState = listState,
        index = (if (state.selectedSection != null) 1 else 0) + 3,
        active = register.pendingUid != null,
    )

    // A small pulse every time a card lands, so the tap is felt as well as seen.
    val pulse by animateFloatAsState(
        targetValue = if (register.pendingUid != null) 1.03f else 1f,
        animationSpec = tween(durationMillis = 220, easing = EmphasizedDecelerate),
        label = "pulse",
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            CollapsingTitle(
                title = "Register students",
                subtitle = "Tap an ID, type the name, save. This never touches the attendance log.",
                listState = listState,
            )
        }

        item(key = "status") {
            BrandCard(modifier = Modifier.animateContentSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NfcLight(state)
                    SectionLabel("Reader")
                }
                Spacer(Modifier.height(6.dp))
                // The reader's sentence turns with the hardware, not with a tap:
                // it cross-fades so the eye follows what changed.
                Crossfade(
                    targetState = readerLine(nfc, paused = false, registering = true),
                    animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
                    label = "readerLine",
                ) { line ->
                    Text(text = line, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = section?.students?.size ?: 0,
                        label = "registered",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        value = register.savedCount,
                        label = "saved this run",
                        modifier = Modifier.weight(1f),
                        accent = register.savedCount > 0,
                    )
                }
                Spacer(Modifier.height(12.dp))
                KeyValueRow("Section", section?.name ?: "(none selected)")
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        state.setRegistering(!register.armed)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    enabled = register.armed || section != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    SwapLabel(if (register.armed) "Stop registering" else "Start registering")
                }
                if (section == null) {
                    Spacer(Modifier.height(8.dp))
                    Note("Create a section first, then register its students.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { state.closeOverlay() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Go to Sections")
                    }
                }
            }
        }

        item(key = "live") {
            BrandCard {
                SectionLabel("Live")
                Spacer(Modifier.height(8.dp))
                AnimatedStatusLine(text = register.status, emphasise = register.armed)
                if (register.message.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Note(register.message)
                }
                Spacer(Modifier.height(10.dp))
                KeyValueRow(
                    label = "Last saved",
                    value = register.lastSaved ?: "nothing yet",
                )
            }
        }

        val pending = register.pendingUid
        if (pending != null) {
            item(key = "pending", contentType = "liveCard") {
                AnimatedVisibility(
                    visible = true,
                    modifier = Modifier.animateItem(),
                    enter = slideInVertically(tween(MotionScreenMs, easing = EmphasizedDecelerate)) { it / 4 } +
                        fadeIn(tween(MotionTouchMs)),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        },
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Card read: " + pending,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            val duplicate = register.duplicateOf
                            if (duplicate != null) {
                                Spacer(Modifier.height(8.dp))
                                Note(
                                    "Already registered as " + duplicate +
                                        " - saving updates that name. Skip to leave the roster untouched."
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = nameDraft,
                                onValueChange = { nameDraft = it },
                                label = { Text("Student name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        state.saveRegistration(nameDraft)
                                        nameDraft = ""
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (duplicate != null) "Update name" else "Save")
                                }
                                OutlinedButton(onClick = {
                                    state.skipPendingCard()
                                    nameDraft = ""
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }) {
                                    Text("Skip card")
                                }
                            }
                        }
                    }
                }
            }
        } else if (register.armed) {
            // Armed means the app is ready to read; it must not be the only thing
            // that is. With the radio off the page says so and offers the switch,
            // instead of holding the teacher's hand over a dead antenna.
            when (nfcAlarm(nfc, paused = false)) {
                NfcAlarm.RADIO_OFF -> item(key = "waiting-radio-off", contentType = "liveCard") {
                    Box(modifier = Modifier.animateItem()) {
                        EmptyState(
                            icon = R.drawable.ic_nav_register,
                            title = "NFC is off",
                            body = "No card can be read until it is switched on. Turn it on, come " +
                                "back, and the reader re-arms itself.",
                            actionLabel = "Turn on NFC",
                            onAction = { openNfcSettings(activity) },
                        )
                    }
                }
                NfcAlarm.NO_HARDWARE -> item(key = "waiting-no-nfc", contentType = "liveCard") {
                    Box(modifier = Modifier.animateItem()) {
                        EmptyState(
                            icon = R.drawable.ic_nav_register,
                            title = "No NFC reader on this phone",
                            body = "Registration taps need the antenna. Import a roster instead, or " +
                                "take attendance with the QR reader on the Scan page.",
                        )
                    }
                }
                NfcAlarm.NONE -> item(key = "waiting", contentType = "liveCard") {
                    Box(modifier = Modifier.animateItem()) {
                        EmptyState(
                            icon = R.drawable.ic_nav_register,
                            title = "Waiting for a card",
                            body = "Hold the student ID flat against the phone's NFC antenna.",
                        )
                    }
                }
            }
        } else {
            item(key = "idle", contentType = "liveCard") {
                BrandCard(modifier = Modifier.animateItem()) {
                    Note("Not registering yet. Press Start registering, then tap a card.")
                }
            }
        }

    }
}