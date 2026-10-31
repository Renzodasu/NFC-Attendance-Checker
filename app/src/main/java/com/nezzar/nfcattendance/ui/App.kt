package com.nezzar.nfcattendance.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.DocumentsExport
import com.nezzar.nfcattendance.data.HapticStrength
import com.nezzar.nfcattendance.data.ReadCue
import com.nezzar.nfcattendance.nfc.NfcScanner
import com.nezzar.nfcattendance.nfc.NfcState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bottom-bar destinations, in the same order as [Screen]. */
/** The three tabs. Registering is a page under the shelf, not a fourth one. */
private enum class Destination(val label: String, val icon: Int) {
    SCAN("Scan", R.drawable.ic_nav_scan),
    REPORT("Report", R.drawable.ic_nav_report),
    SECTIONS("Sections", R.drawable.ic_nav_sections),
}

@Composable
fun AppRoot(state: AppState, activity: Activity) {
    // Legacy folder picker (API 24-28): the only path that still needs the user to choose a folder.
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(DocumentsExport.MIME_XLSX)
    ) { uri -> state.completePendingExport(uri) }
    LaunchedEffect(state.pendingExport) {
        val pending = state.pendingExport
        if (pending != null) pickFolder.launch(pending.fileName)
    }

    // ONE reader, owned here, armed only for the tab that needs taps - two screens can
    // never fight over enableReaderMode, and switching tabs always disarms it.
    val scanner = remember(activity) { NfcScanner(activity) }
    // Bumped on every resume so the arming effect can re-register the reader after a
    // trip to Settings: the radio may already be on by the time the app is back, and
    // no other key would have changed while it was away.
    var resumeTick by remember { mutableIntStateOf(0) }
    // The tap feedback: a short rendered tone (no permission needed) and the
    // window's own haptic feedback at the strength chosen in Settings.
    val haptics = LocalHapticFeedback.current
    // No ToneGenerator here on purpose. Its notification stream is muted by silent
    // mode - in a classroom that is exactly when the teacher needs it - and one
    // beep cannot tell "present" from "already counted". ReadFeedback renders a
    // distinct tone per verdict on the media stream instead.
    val feedback = remember { ReadFeedback() }
    DisposableEffect(Unit) {
        onDispose { feedback.release() }
    }
    // Armed for the two pages that take taps: the Scan tab, and the register page
    // (which lives under the shelf now, over the tabs).
    // The reader is off while the session is paused, not merely ignored: the antenna
    // must not keep reading cards into an outcome called PAUSED that then vanishes.
    val tapping = state.overlay == Overlay.REGISTER ||
        (state.screen == Screen.SCAN && !state.paused)
    // The display stays on whenever a page is armed - even with the radio off: the
    // teacher may be walking to the quick-settings tile with the page open, and it
    // must not lock on the way there. Split from arming because arming now depends
    // on the radio, and a lock screen is not a hardware problem.
    DisposableEffect(tapping, activity) {
        if (tapping) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Arming is keyed on the radio too. A quick-settings toggle never pauses the
    // activity, so nothing else would re-register the reader: without this key the
    // app would stay deaf after the teacher switched NFC on, and a tap at the door
    // would vanish exactly as before. Switching it off disarms instead of leaving a
    // callback that can never fire.
    val radioOn = state.nfcState == NfcState.ENABLED
    DisposableEffect(tapping, radioOn, resumeTick, activity) {
        if (tapping && radioOn) {
            scanner.start { bytes ->
                if (state.overlay == Overlay.REGISTER) state.onRegisterTap(bytes) else state.onNfcTap(bytes)
            }
        }
        onDispose { scanner.stop() }
    }

    // Every read reports itself here, whichever reader and whatever the verdict:
    // the sound and the buzz follow the ANSWER, not whether the count moved, so
    // silence can never be mistaken for success at the door.
    LaunchedEffect(state.cueSeq) {
        val cue = state.lastCue ?: return@LaunchedEffect
        if (state.scanSound) feedback.play(cue)
        val presses = when (cue) {
            ReadCue.RECORDED -> 1
            ReadCue.LATE -> 2
            ReadCue.DUPLICATE -> 2
            ReadCue.UNKNOWN -> 3
            ReadCue.REFUSED -> 1
        }
        if (state.hapticStrength != HapticStrength.OFF) {
            repeat(presses) { index ->
                if (index > 0) delay(90)
                val type = if (state.hapticStrength == HapticStrength.LIGHT) {
                    HapticFeedbackType.TextHandleMove
                } else {
                    HapticFeedbackType.LongPress
                }
                haptics.performHapticFeedback(type)
            }
        }
    }

    // The reader light has to be right the moment a page opens, and again when the
    // user flips NFC in the system panel and comes back to the app.
    DisposableEffect(activity) {
        state.useNfcState(scanner.state())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                state.useNfcState(scanner.state())
            }
        }
        val filter = IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(receiver, filter)
        }
        // The broadcast is the fast path; a resume re-sample is the belt-and-braces
        // one, for a ROM that drops it while the app is in the background.
        val resume = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.useNfcState(scanner.state())
                resumeTick += 1
            }
        }
        // MainActivity is a ComponentActivity and therefore a LifecycleOwner; the
        // cast keeps this correct if the shell is ever handed a bare Activity.
        val owner = activity as? LifecycleOwner
        owner?.lifecycle?.addObserver(resume)
        onDispose {
            activity.unregisterReceiver(receiver)
            owner?.lifecycle?.removeObserver(resume)
        }
    }

    // Back closes the open page. There is no drawer to close any more.
    BackHandler(enabled = state.overlay != null) { state.closeOverlay() }

    Scaffold(
            // Plain black behind every page: the theme's own background, unpainted.
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ShellHeader(
                    state = state,
                    onSettings = { state.openOverlay(Overlay.SETTINGS) },
                    onGuide = { state.openOverlay(Overlay.TUTORIAL) },
                )
            },
            bottomBar = {
                // A page opens over the tabs on its own, so the bar would only be noise.
                // It slides out with the page instead of vanishing between frames.
                AnimatedVisibility(
                    visible = state.overlay == null,
                    enter = slideInVertically(
                        animationSpec = tween(MotionScreenMs, easing = EmphasizedDecelerate),
                    ) { height -> height } + fadeIn(tween(MotionTouchMs)),
                    exit = slideOutVertically(
                        animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
                    ) { height -> height } + fadeOut(tween(160)),
                ) {
                    Column {
                        SessionBar(state)
                        BottomBar(state)
                    }
                }
            },
        ) { padding ->
            val page = state.overlay
            // imePadding lifts a page above the typing keyboard, and
            // consumeWindowInsets stops it from paying for the system bar twice.
            val body = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
            // Tabs and pages share one transition: a page rises over the tab that
            // opened it while that tab steps back, and the reverse on the way home.
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (targetState != null) {
                        val enter = slideInVertically(
                            animationSpec = tween(MotionScreenMs, easing = EmphasizedDecelerate),
                        ) { height -> height / 7 } + fadeIn(tween(MotionScreenMs))
                        val leave = scaleOut(
                            animationSpec = tween(MotionScreenMs, easing = EmphasizedDecelerate),
                            targetScale = 0.97f,
                        ) + fadeOut(tween(MotionTouchMs))
                        enter togetherWith leave
                    } else {
                        val enter = scaleIn(
                            animationSpec = tween(MotionScreenMs, easing = EmphasizedDecelerate),
                            initialScale = 0.97f,
                        ) + fadeIn(tween(MotionScreenMs))
                        val leave = slideOutVertically(
                            animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
                        ) { height -> height / 7 } + fadeOut(tween(MotionTouchMs))
                        enter togetherWith leave
                    }
                },
                label = "page",
                modifier = body,
            ) { open ->
                if (open == null) {
                    AnimatedContent(
                        targetState = state.screen,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            val direction = if (forward) 1 else -1
                            val enter = slideInHorizontally(
                                animationSpec = tween(MotionScreenMs, easing = EmphasizedDecelerate),
                            ) { width -> width * direction / 6 } + fadeIn(tween(MotionTouchMs))
                            val exit = slideOutHorizontally(
                                animationSpec = tween(MotionTouchMs),
                            ) { width -> -width * direction / 8 } + fadeOut(tween(160))
                            enter togetherWith exit
                        },
                        label = "tab",
                        modifier = Modifier.fillMaxSize(),
                    ) { screen ->
                        when (screen) {
                            Screen.SECTIONS -> SectionsScreen(state)
                            Screen.SCAN -> ScanScreen(state, activity)
                            Screen.REPORT -> ReportScreen(state)
                        }
                    }
                } else {
                    val pageModifier = Modifier.fillMaxSize()
                    when (open) {
                        Overlay.SETTINGS -> SettingsScreen(state, pageModifier)
                        Overlay.TUTORIAL -> TutorialScreen(state, pageModifier)
                        Overlay.STUDENT -> StudentScreen(
                            state = state,
                            index = state.editingStudentIndex,
                            modifier = pageModifier,
                        )
                        Overlay.ROSTER -> RosterScreen(state, pageModifier)
                        Overlay.NEW_SECTION -> NewSectionScreen(state, pageModifier)
                        Overlay.SECTION -> SectionScreen(state, pageModifier)
                        Overlay.REGISTER -> RegisterScreen(state, activity, pageModifier)
                    }
                }
            }
        }
}

/**
 * The always-there header. Screen identity used to scroll away with the list,
 * which left scrolled content unattributed under the status bar; now the tab
 * name and the section being worked on stay put above the content. On a page it
 * turns into a back button instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellHeader(state: AppState, onSettings: () -> Unit, onGuide: () -> Unit) {
    val overlay = state.overlay
    val label = when (overlay) {
        Overlay.SETTINGS -> "Settings"
        Overlay.TUTORIAL -> "How to use"
        Overlay.STUDENT -> "Student"
        Overlay.ROSTER -> "Registered students"
        Overlay.NEW_SECTION -> "New class"
        Overlay.SECTION -> "Edit class"
        Overlay.REGISTER -> "Register"
        null -> Destination.entries[state.screen.ordinal].label
    }
    Column {
        TopAppBar(
            navigationIcon = {
                // A page gets a back arrow; the tabs get nothing, because the bottom
                // bar is the only navigation they need.
                if (overlay != null) {
                    IconButton(onClick = { state.closeOverlay() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = "Back",
                        )
                    }
                }
            },
            title = {
                Crossfade(
                    targetState = label,
                    animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
                    label = "headerLabel",
                ) { current ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = current,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // The chip carries the class wherever the app goes: its mark,
                        // then its name, both short enough to sit next to the tab title.
                        state.selectedSection?.let { picked ->
                            Spacer(Modifier.width(10.dp))
                            ClassBadge(name = picked.name, selected = true, size = 26.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = picked.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 132.dp),
                            )
                        }
                    }
                }
            },
            actions = {
                // The two things that are not tabs live here, one tap from anywhere -
                // they used to be three rows deep inside a drawer that repeated the tabs.
                if (overlay == null) {
                    IconButton(onClick = onGuide) {
                        Icon(
                            painter = painterResource(R.drawable.ic_book),
                            contentDescription = "How to use",
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Settings",
                        )
                    }
                }
                // A page can lend the header one action. The Edit class page lends it
                // Save, which is where a form's save belongs.
                val actionLabel = state.headerActionLabel
                val action = state.headerAction
                AnimatedVisibility(
                    visible = actionLabel != null && action != null,
                    enter = fadeIn(tween(MotionTouchMs)) + slideInVertically(
                        animationSpec = tween(MotionTouchMs, easing = EmphasizedDecelerate),
                    ) { height -> -height / 2 },
                    exit = fadeOut(tween(140)),
                ) {
                    if (actionLabel != null && action != null) {
                        TextButton(onClick = action) {
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun BottomBar(state: AppState) {
    val destinations = Destination.entries
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        destinations.forEachIndexed { index, destination ->
            NavigationBarItem(
                selected = state.screen.ordinal == index,
                onClick = { state.screen = Screen.entries[index] },
                icon = {
                    Icon(
                        painter = painterResource(destination.icon),
                        contentDescription = destination.label,
                        modifier = Modifier.size(28.dp),
                    )
                },
                label = { Text(destination.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * The strip above the bar: a live session, or registration waiting for a card.
 * It appears only where the state is NOT already on screen, and it carries a
 * labelled action instead of a three-letter word inside an invisible tap target.
 */
@Composable
private fun SessionBar(state: AppState) {
    val running = state.running
    val register = state.registerState
    val pending = register.pendingUid
    val registering = register.armed || pending != null

    val ownedHere = when {
        running -> state.screen == Screen.SCAN || state.screen == Screen.REPORT
        else -> state.overlay == Overlay.REGISTER
    }
    val visible = (running || registering) && !ownedHere

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(MotionScreenMs, easing = EmphasizedDecelerate)) { it } +
            fadeIn(tween(MotionTouchMs)),
        exit = slideOutVertically(tween(MotionTouchMs)) { it } + fadeOut(tween(140)),
    ) {
        val session = state.session
        val resolved = state.resolved()
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painter = painterResource(if (running) R.drawable.ic_nav_scan else R.drawable.ic_nav_register),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NfcLight(state, dot = 8.dp)
                        Text(
                            text = if (running) (session?.sectionName ?: "Session") else (state.selectedSection?.name ?: "Registering"),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = if (running) {
                            (if (state.paused) "Paused  ·  " else "") +
                                "Present " + (resolved?.present?.size ?: 0) +
                                "  ·  Late " + (resolved?.late?.size ?: 0) +
                                "  ·  Absent " + (resolved?.absent?.size ?: 0) +
                                "  ·  Unmatched " + (resolved?.unmatched?.size ?: 0)
                        } else {
                            if (pending != null) "Card read: " + pending else "Waiting for a card"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = {
                        if (running) state.stopSession() else state.openRegister()
                    },
                ) {
                    Text(if (running) "End session" else "Open")
                }
            }
        }
    }
}