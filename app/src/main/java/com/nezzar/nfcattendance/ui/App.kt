package com.nezzar.nfcattendance.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.nfc.NfcAdapter
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.nezzar.nfcattendance.R
import com.nezzar.nfcattendance.data.DocumentsExport
import com.nezzar.nfcattendance.data.HapticStrength
import com.nezzar.nfcattendance.nfc.NfcScanner
import kotlinx.coroutines.launch

/** Bottom-bar destinations, in the same order as [Screen]. */
private enum class Destination(val label: String, val icon: Int, val blurb: String) {
    SECTIONS("Sections", R.drawable.ic_nav_sections, "Classes and their rosters"),
    REGISTER("Register", R.drawable.ic_nav_register, "Add a student card"),
    SCAN("Scan", R.drawable.ic_nav_scan, "Take attendance"),
    REPORT("Report", R.drawable.ic_nav_report, "Result and export"),
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
    // The tap feedback: a short tone (no permission needed) and the window's own
    // haptic feedback at the strength chosen in Settings.
    val haptics = LocalHapticFeedback.current
    val tone = remember {
        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (t: Throwable) {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose { tone?.release() }
    }
    DisposableEffect(state.screen, activity) {
        if (state.screen == Screen.REGISTER || state.screen == Screen.SCAN) {
            scanner.start { bytes ->
                val tapsBefore = state.session?.taps?.size ?: 0
                val pendingBefore = state.registerState.pendingUid
                if (state.screen == Screen.REGISTER) state.onRegisterTap(bytes) else state.onScanTap(bytes)
                val landed = (state.session?.taps?.size ?: 0) > tapsBefore ||
                    (pendingBefore == null && state.registerState.pendingUid != null)
                if (landed) {
                    // The same flag that buzzes the phone swells the chosen card's glow.
                    state.noteCardRead()
                    if (state.scanSound) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                    when (state.hapticStrength) {
                        HapticStrength.OFF -> Unit
                        HapticStrength.LIGHT ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        HapticStrength.NORMAL ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        HapticStrength.STRONG -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
            }
        }
        onDispose { scanner.stop() }
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
        onDispose { activity.unregisterReceiver(receiver) }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Back closes the page first, then the drawer.
    BackHandler(enabled = state.overlay != null) { state.closeOverlay() }
    BackHandler(enabled = state.overlay == null && drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = state.overlay == null,
        drawerContent = {
            AppDrawer(state) { scope.launch { drawerState.close() } }
        },
    ) {
        Scaffold(
            // Plain black behind every page: the theme's own background, unpainted.
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                ShellHeader(state, onMenu = { scope.launch { drawerState.open() } })
            },
            bottomBar = {
                // A page opens over the tabs on its own, so the bar would only be noise.
                if (state.overlay == null) {
                    Column {
                        SessionBar(state)
                        BottomBar(state)
                    }
                }
            },
        ) { padding ->
            val overlay = state.overlay
            if (overlay != null) {
                // imePadding lifts the page above the typing keyboard, and
                // consumeWindowInsets stops it from paying for the system bar twice.
                val body = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding()
                when (overlay) {
                    Overlay.SETTINGS -> SettingsScreen(state, body)
                    Overlay.TUTORIAL -> TutorialScreen(state, body)
                    Overlay.STUDENT -> StudentScreen(state, state.editingStudentUid ?: "", body)
                    Overlay.ROSTER -> RosterScreen(state, body)
                    Overlay.NEW_SECTION -> NewSectionScreen(state, body)
                    Overlay.SECTION -> SectionScreen(state, body)
                }
            } else {
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .consumeWindowInsets(padding)
                        .imePadding(),
                ) { screen ->
                    when (screen) {
                        Screen.SECTIONS -> SectionsScreen(state)
                        Screen.REGISTER -> RegisterScreen(state, activity)
                        Screen.SCAN -> ScanScreen(state, activity)
                        Screen.REPORT -> ReportScreen(state)
                    }
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
private fun ShellHeader(state: AppState, onMenu: () -> Unit) {
    val overlay = state.overlay
    val label = when (overlay) {
        Overlay.SETTINGS -> "Settings"
        Overlay.TUTORIAL -> "How to use"
        Overlay.STUDENT -> "Student"
        Overlay.ROSTER -> "Registered students"
        Overlay.NEW_SECTION -> "New section"
        Overlay.SECTION -> "Manage section"
        null -> Destination.entries[state.screen.ordinal].label
    }
    Column {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { if (overlay != null) state.closeOverlay() else onMenu() }) {
                    Icon(
                        painter = painterResource(
                            if (overlay != null) R.drawable.ic_back else R.drawable.ic_menu
                        ),
                        contentDescription = if (overlay != null) "Back" else "Open menu",
                    )
                }
            },
            title = {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (overlay == null) {
                        Spacer(Modifier.width(10.dp))
                        Pill(text = state.selectedSection?.name ?: "No section")
                    }
                }
            },
            actions = {
                // A page can lend the header one action. The Manage page lends it
                // Save, which is where a form's save belongs.
                val actionLabel = state.headerActionLabel
                val action = state.headerAction
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

/** The sidebar: where you are, then the two things that are not tabs. */
@Composable
private fun AppDrawer(state: AppState, onClose: () -> Unit) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp)) {
            Text("NFC Attendance", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(6.dp))
            Note("Card taps, no accounts, no internet.")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Destination.entries.forEachIndexed { index, destination ->
            NavigationDrawerItem(
                label = { Text(destination.label) },
                icon = { Icon(painter = painterResource(destination.icon), contentDescription = null) },
                badge = { Note(destination.blurb) },
                selected = state.overlay == null && state.screen.ordinal == index,
                onClick = {
                    state.openOverlay(null)
                    state.screen = Screen.entries[index]
                    onClose()
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = null) },
            selected = state.overlay == Overlay.SETTINGS,
            onClick = {
                state.openOverlay(Overlay.SETTINGS)
                onClose()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text("How to use") },
            icon = { Icon(painter = painterResource(R.drawable.ic_book), contentDescription = null) },
            selected = state.overlay == Overlay.TUTORIAL,
            onClick = {
                state.openOverlay(Overlay.TUTORIAL)
                onClose()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        Column(modifier = Modifier.padding(20.dp)) {
            Pill(text = "Offline", accent = false)
            Spacer(Modifier.width(6.dp))
            Note("The app holds no internet permission, so a roster cannot leave the phone by itself.")
        }
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
        else -> state.screen == Screen.REGISTER
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
                        if (running) state.stopSession() else state.screen = Screen.REGISTER
                    },
                ) {
                    Text(if (running) "End session" else "Open")
                }
            }
        }
    }
}