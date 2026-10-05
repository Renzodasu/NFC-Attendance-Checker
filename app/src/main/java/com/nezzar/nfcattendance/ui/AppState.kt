package com.nezzar.nfcattendance.ui

import androidx.compose.ui.geometry.Rect
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nezzar.nfcattendance.data.AttendanceResolver
import com.nezzar.nfcattendance.data.HapticStrength
import com.nezzar.nfcattendance.data.PlayingCards
import com.nezzar.nfcattendance.data.VisualStyle
import com.nezzar.nfcattendance.data.AttendanceSession
import com.nezzar.nfcattendance.data.DocumentsExport
import com.nezzar.nfcattendance.data.RegisterFlow
import com.nezzar.nfcattendance.data.RegisterState
import com.nezzar.nfcattendance.data.ReportBuilder
import com.nezzar.nfcattendance.data.ResolvedAttendance
import com.nezzar.nfcattendance.data.RosterImporter
import com.nezzar.nfcattendance.data.Section
import com.nezzar.nfcattendance.data.Sections
import com.nezzar.nfcattendance.data.Sheet
import com.nezzar.nfcattendance.data.Store
import com.nezzar.nfcattendance.data.Student
import com.nezzar.nfcattendance.data.Tap
import com.nezzar.nfcattendance.data.Uid
import com.nezzar.nfcattendance.data.XlsxReader
import com.nezzar.nfcattendance.data.XlsxWriter
import com.nezzar.nfcattendance.nfc.NfcState
import com.nezzar.nfcattendance.ui.theme.ThemeMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Screen { SECTIONS, REGISTER, SCAN, REPORT }

/** A full-screen page that opens over the tabs (from the sidebar, never a tab itself). */
enum class Overlay { SETTINGS, TUTORIAL, STUDENT, ROSTER, NEW_SECTION, SECTION }

/** The raw and byte-reversed readings, shown side by side while calibrating. */
data class RecentTap(val asRead: String, val reversed: String)

/** An export waiting for the user to pick a folder (the API 24-28 picker path). */
data class PendingExport(
    val kind: Kind,
    val fileName: String,
    val bytes: ByteArray,
) {
    enum class Kind { ROSTER, REPORT }
}

class AppState(context: Context) {

    private val appContext = context.applicationContext
    private val store = Store(appContext)

    var screen by mutableStateOf(Screen.SECTIONS)

    /** Non-null while Settings or the tutorial is open over the tabs. */
    var overlay by mutableStateOf<Overlay?>(null)

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    /** True while the session runs but its reader is paused. */
    var paused by mutableStateOf(false)

    /** Minutes after the start that still count as present. */
    var lateAfterMinutes by mutableStateOf(AttendanceResolver.DEFAULT_LATE_AFTER_MINUTES)

    /** Where the focused shelf card sits, in root pixels, for the tab transition. */
    var shelfCardRect by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    /** Set while a tab card is flying in from the shelf; null when it has landed. */
    var cardFlight by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    /** Asks the next tab card to animate in from the shelf. */
    fun armCardFlight() {
        cardFlight = shelfCardRect
    }

    /** The section texture: the playing deck by default, or the engineering sheet. */
    var visualStyle by mutableStateOf(VisualStyle.CARDS)

    /** How hard a card read buzzes, and whether it beeps. */
    var hapticStrength by mutableStateOf(HapticStrength.NORMAL)
    var scanSound by mutableStateOf(true)

    // Sections + roster ------------------------------------------------------
    var sections by mutableStateOf<List<Section>>(emptyList())
    var selectedName by mutableStateOf("")
    var sectionsMessage by mutableStateOf("No sections yet. Create one, then register students into it.")
    var sectionsError by mutableStateOf("")
    var uidReversed by mutableStateOf(false)
    var rosterPath by mutableStateOf<String?>(null)
    var rosterError by mutableStateOf<String?>(null)
    var rosterUri by mutableStateOf<Uri?>(null)

    /** Set only on API 24-28, where the folder picker has to choose the file first. */
    var pendingExport by mutableStateOf<PendingExport?>(null)

    // Import from another phone's shared .xlsx -------------------------------
    var importedFile by mutableStateOf<RosterImporter.Parsed?>(null)
    var importSourceName by mutableStateOf("")
    var importTargetName by mutableStateOf("")
    var importError by mutableStateOf("")

    // Register mode ----------------------------------------------------------
    var registerState by mutableStateOf(RegisterState())

    // Scan mode --------------------------------------------------------------
    var session by mutableStateOf<AttendanceSession?>(null)
    var running by mutableStateOf(false)
    var statusText by mutableStateOf("Idle - no session running.")
    var lastTapKnown by mutableStateOf<Boolean?>(null)
    val recentTaps = mutableStateListOf<RecentTap>()
    var reportPath by mutableStateOf<String?>(null)
    var reportError by mutableStateOf<String?>(null)
    var reportUri by mutableStateOf<Uri?>(null)

    // The chosen card's live glow ---------------------------------------------
    /** The instant a card actually landed, so the chosen card can swell on the beat. */
    var lastCardReadAt by mutableStateOf(0L)

    /** True while the app is listening for cards: registering, or a live session. */
    val listeningForCards: Boolean
        get() = registerState.armed || (running && !paused)

    /** What the chosen card should be doing right now: a standby pulse, a swell per read. */
    val cardGlow: CardGlow
        get() = CardGlow(standby = listeningForCards, beat = lastCardReadAt)

    /** A card landed - registered or scanned - so the chosen card swells once. */
    fun noteCardRead() {
        lastCardReadAt = System.currentTimeMillis()
    }

    // The phone's reader ------------------------------------------------------
    /**
     * What the NFC hardware is doing right now. AppRoot owns the scanner, so it
     * reports this down here; the reader light draws itself from this value.
     */
    var nfcState by mutableStateOf(NfcState.UNSUPPORTED)
        private set

    /** AppRoot owns the scanner, so it tells the app what the hardware is doing. */
    fun useNfcState(value: NfcState) {
        if (value != nfcState) nfcState = value
    }

    init {
        sections = store.loadSections()
        uidReversed = store.isUidReversed()
        themeMode = when (store.themeMode().lowercase()) {
            "light" -> ThemeMode.LIGHT
            "system" -> ThemeMode.SYSTEM
            else -> ThemeMode.DARK
        }
        lateAfterMinutes = store.lateAfterMinutes()
        visualStyle = VisualStyle.fromStored(store.visualStyle())
        hapticStrength = HapticStrength.fromStored(store.hapticStrength())
        scanSound = store.isScanSoundOn()
        // Sections restored from a file written before cards existed get dealt one.
        if (sections.any { it.card.isBlank() }) {
            val taken = sections.map { it.card }.filter { it.isNotBlank() }.toMutableSet()
            sections = sections.map { section ->
                if (section.card.isNotBlank()) {
                    section
                } else {
                    val face = PlayingCards.pick(taken)
                    taken += face
                    section.copy(card = face)
                }
            }
            store.saveSections(sections)
        }
        // Very first launch of this install: show the four steps, then never again.
        if (!store.hasSeenTutorial()) {
            overlay = Overlay.TUTORIAL
            store.setTutorialSeen()
        }
        val stored = store.selectedSection()
        selectedName = Sections.find(sections, stored)?.name ?: sections.firstOrNull()?.name ?: ""
        if (sections.isNotEmpty()) {
            sectionsMessage = sections.size.toString() + " section(s) restored from local storage; selected " +
                (if (selectedName.isBlank()) "(none)" else selectedName) + "."
        }
    }

    val selectedSection: Section? get() = Sections.find(sections, selectedName)
    val roster: List<Student> get() = selectedSection?.students ?: emptyList()

    /** How the picked file will merge, recomputed whenever the name field or the sections change. */
    val importPlan: RosterImporter.Plan?
        get() {
            val parsed = importedFile ?: return null
            return RosterImporter.plan(parsed, sections, importTargetName)
        }

    // ------------------------------------------------------------------ sections

    fun createSection(rawName: String, subject: String = "", card: String = "") {
        val edit = Sections.create(sections, rawName, subject = subject, card = card)
        applyEdit(edit)
        if (edit.error.isEmpty()) {
            val created = rawName.trim()
            if (created.isNotEmpty() && Sections.find(sections, selectedName) == null) selectSection(created)
        }
    }

    fun selectSection(name: String) {
        val match = Sections.find(sections, name) ?: return
        selectedName = match.name
        store.setSelectedSection(match.name)
        sectionsError = ""
        sectionsMessage = "Selected " + match.name + " (" + match.students.size + " registered)."
        if (registerState.armed) registerState = RegisterFlow.arm(registerState, match)
    }

    fun renameSelectedSection(rawName: String) {
        val before = selectedName
        val edit = Sections.rename(sections, before, rawName)
        applyEdit(edit)
        if (edit.error.isEmpty()) {
            val renamed = rawName.trim()
            if (renamed.isNotEmpty() && renamed != before) selectSection(renamed)
        }
    }

    fun deleteSection(name: String) = applyEdit(Sections.delete(sections, name))

    /** The class subject of the selected section, set from its Manage card. */
    fun useSubject(subject: String) = applyEdit(Sections.setSubject(sections, selectedName, subject))

    /**
     * The Manage page saves the whole section at once - name, subject and card
     * face - because one Save button that means everything is easier to trust
     * than three that each mean a part. The name goes first: the other two
     * address the section by it.
     *
     * Everything that can be refused is refused BEFORE anything is written, so a
     * taken face or a blank name leaves the section exactly as it was.
     */
    fun saveSectionEdits(name: String, subject: String, suit: String, rank: String) {
        val target = selectedSection ?: return
        if (suit.isNotEmpty() != rank.isNotEmpty()) {
            sectionsError = "Pick both a symbol and a number, or leave the face as it is."
            return
        }
        val face = if (suit.isNotEmpty() && rank.isNotEmpty()) rank + suit else ""
        if (face.isNotEmpty() && face != target.card) {
            val clash = sections.firstOrNull { it.name != target.name && it.card == face }
            if (clash != null) {
                sectionsError = clash.name + " already holds " + face + ". Pick another face."
                return
            }
        }
        if (name.trim() != target.name) {
            renameSelectedSection(name)
            if (sectionsError.isNotEmpty()) return
        }
        useSubject(subject)
        if (sectionsError.isNotEmpty()) return
        if (face.isNotEmpty() && face != target.card) {
            applyEdit(Sections.setCard(sections, selectedName, face))
        }
    }

    fun renameStudent(uid: String, newName: String) =
        applyEdit(Sections.renameStudent(sections, selectedName, uid, newName))

    fun removeStudent(uid: String) = applyEdit(Sections.removeStudent(sections, selectedName, uid))

    private fun applyEdit(edit: Sections.Edit) {
        sections = edit.sections
        if (edit.error.isEmpty()) {
            sectionsError = ""
            if (edit.message.isNotEmpty()) sectionsMessage = edit.message
        } else {
            sectionsError = edit.error
        }
        store.saveSections(sections)
        ensureSelection()
    }

    private fun ensureSelection() {
        if (Sections.find(sections, selectedName) == null) {
            selectedName = sections.firstOrNull()?.name ?: ""
        }
        store.setSelectedSection(selectedName)
    }

    fun useReversedUid(value: Boolean) {
        uidReversed = value
        store.setUidReversed(value)
    }

    fun useLateAfterMinutes(minutes: Int) {
        lateAfterMinutes = minutes
        store.setLateAfterMinutes(minutes)
        statusText = if (running) {
            "Late window is now " + minutes + " minute(s) after the start. The session keeps running."
        } else {
            "A card read more than " + minutes + " minute(s) after the start counts as late."
        }
    }

    fun useVisualStyle(value: VisualStyle) {
        visualStyle = value
        store.setVisualStyle(value.name.lowercase())
    }

    fun useHapticStrength(value: HapticStrength) {
        hapticStrength = value
        store.setHapticStrength(value.name.lowercase())
    }

    fun useScanSound(value: Boolean) {
        scanSound = value
        store.setScanSound(value)
    }

    fun useThemeMode(mode: ThemeMode) {
        themeMode = mode
        store.setThemeMode(mode.name.lowercase())
    }

    fun openOverlay(target: Overlay?) {
        overlay = target
    }

    /** Which student the full-screen editor is showing while Overlay.STUDENT is open. */
    var editingStudentUid by mutableStateOf<String?>(null)

    /** Naming a class (and importing one) happens on its own page, off the shelf. */
    fun openNewSection() {
        overlay = Overlay.NEW_SECTION
        sectionsError = ""
        importError = ""
    }

    /** The Manage control on a section card opens this: subject, name, and delete. */
    fun openSection() {
        overlay = Overlay.SECTION
        sectionsError = ""
    }

    /** Every registered card of the selected section, on one page of its own. */
    fun openRoster() {
        overlay = Overlay.ROSTER
        sectionsError = ""
    }

    /** A roster row opens this: everything you can do to one student, on its own page. */
    fun openStudent(uid: String) {
        editingStudentUid = uid
        overlay = Overlay.STUDENT
        sectionsError = ""
    }

    fun closeOverlay() {
        overlay = null
        editingStudentUid = null
        setHeaderAction(null, null)
    }

    /**
     * A page can lend the header ONE action, and takes it back when it closes.
     * The Manage page lends it Save, so the button sits where a form's save
     * belongs instead of at the bottom of a list you have to scroll to find.
     */
    var headerActionLabel by mutableStateOf<String?>(null)
    var headerAction by mutableStateOf<(() -> Unit)?>(null)

    fun setHeaderAction(label: String?, run: (() -> Unit)?) {
        headerActionLabel = label
        headerAction = run
    }

    /** True after the guide has been queued to open again on the next start. */
    var tutorialQueued by mutableStateOf(false)

    fun showTutorialOnNextLaunch() {
        store.setTutorialSeen(false)
        tutorialQueued = true
    }

    /** "Date updated: ..." for the selected section, or "Not recorded". */
    fun selectedUpdatedText(): String =
        ReportBuilder.dateUpdatedText(selectedSection?.updatedAt ?: 0L)

    // -------------------------------------------------------------------- export

    fun exportRoster() {
        val section = selectedSection
        if (section == null) {
            rosterError = "Create and select a section first."
            return
        }
        if (section.students.isEmpty()) {
            rosterError = "Nothing to export - " + section.name + " has no registered students."
            return
        }
        rosterUri = null
        rosterPath = null
        rosterError = null
        deliver(
            PendingExport.Kind.ROSTER,
            "roster-" + safeFileName(section.name) + ".xlsx",
            bytes(ReportBuilder.rosterSheets(section)),
        )
    }

    fun exportReport() {
        val current = session ?: return
        reportUri = null
        reportPath = null
        reportError = null
        deliver(
            PendingExport.Kind.REPORT,
            "attendance-" + current.sessionId + ".xlsx",
            bytes(ReportBuilder.sheets(current, lateAfterMillis())),
        )
    }

    private fun deliver(kind: PendingExport.Kind, fileName: String, body: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val written = DocumentsExport.save(appContext, fileName) { out -> out.write(body) }
                publish(kind, written.displayPath, written.uri, null)
                return
            } catch (t: Throwable) {
                val fallback = privateCopy(fileName, body)
                publish(
                    kind,
                    fallback,
                    null,
                    "Could not write to the shared Documents folder (" + (t.message ?: t.toString()) +
                        "). The file was saved in the app's private reports folder instead" +
                        (if (fallback == null) " - and that failed too." else "."),
                )
                return
            }
        }
        // API 24-28: MediaStore cannot choose the folder, so the user does.
        pendingExport = PendingExport(kind, fileName, body)
    }

    /** The folder picker came back (null when the user cancelled). */
    fun completePendingExport(uri: Uri?) {
        val pending = pendingExport ?: return
        pendingExport = null
        if (uri == null) {
            publish(pending.kind, null, null, "Export cancelled - nothing was written.")
            return
        }
        try {
            val stream = appContext.contentResolver.openOutputStream(uri, "w")
                ?: throw IllegalStateException("The picked document could not be opened for writing.")
            stream.use { out -> out.write(pending.bytes) }
            publish(pending.kind, uri.toString(), uri, null)
        } catch (t: Throwable) {
            publish(pending.kind, null, null, "Export failed: " + (t.message ?: t.toString()))
        }
    }

    private fun privateCopy(fileName: String, body: ByteArray): String? = try {
        val file = File(store.reportsDir(), fileName)
        file.outputStream().use { out -> out.write(body) }
        file.absolutePath
    } catch (t: Throwable) {
        null
    }

    private fun publish(kind: PendingExport.Kind, path: String?, uri: Uri?, note: String?) {
        when (kind) {
            PendingExport.Kind.ROSTER -> {
                rosterPath = path
                rosterUri = uri
                rosterError = note
            }
            PendingExport.Kind.REPORT -> {
                reportPath = path
                reportUri = uri
                reportError = note
            }
        }
    }

    fun shareFile(uri: Uri?) {
        val target = uri ?: return
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = DocumentsExport.MIME_XLSX
                putExtra(Intent.EXTRA_STREAM, target)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "Share the .xlsx file")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(chooser)
        } catch (t: Throwable) {
            when {
                rosterUri == target -> rosterError = "No app on this phone can share the file."
                reportUri == target -> reportError = "No app on this phone can share the file."
            }
        }
    }

    private fun bytes(sheets: List<Sheet>): ByteArray {
        val buffer = ByteArrayOutputStream()
        XlsxWriter.write(buffer, sheets)
        return buffer.toByteArray()
    }

    // -------------------------------------------------------------------- import

    /** Reads a shared .xlsx picked through the system file picker. Nothing is saved yet. */
    fun stageImport(uri: Uri) {
        importError = ""
        try {
            val parsed = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                val book = XlsxReader.read(stream)
                val sheet = book.sheet(RosterImporter.SHEET_ROSTER)
                    ?: throw IllegalArgumentException(
                        "That workbook has no sheet named Roster. Sheets found: " +
                            book.sheetNames.joinToString(", ") + "."
                    )
                RosterImporter.parse(sheet)
            } ?: throw IllegalArgumentException("The picked file could not be opened.")
            if (parsed.error.isNotEmpty()) {
                importedFile = null
                importError = parsed.error
                return
            }
            importedFile = parsed
            importSourceName = displayName(uri)
            importTargetName = parsed.sectionName.ifBlank { "Imported section" }
        } catch (t: Throwable) {
            importedFile = null
            importError = t.message ?: t.toString()
        }
    }

    fun importSectionNameChanged(value: String) {
        importTargetName = value
        importError = ""
    }

    fun confirmImport() {
        val plan = importPlan ?: return
        if (plan.error.isNotEmpty()) {
            importError = plan.error
            return
        }
        sections = plan.sections
        store.saveSections(sections)
        importedFile = null
        importSourceName = ""
        importError = ""
        val summary = RosterImporter.summary(plan)
        selectSection(plan.targetName)
        sectionsMessage = summary
    }

    fun cancelImport() {
        importedFile = null
        importSourceName = ""
        importTargetName = ""
        importError = ""
    }

    private fun displayName(uri: Uri): String {
        try {
            val cursor = appContext.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && it.moveToFirst()) {
                    val value = it.getString(index)
                    if (!value.isNullOrBlank()) return value
                }
            }
        } catch (ignored: Throwable) {
            // fall through to the Uri itself
        }
        return uri.lastPathSegment ?: "picked file"
    }

    // ------------------------------------------------------------------ register

    fun setRegistering(value: Boolean) {
        registerState = if (value) {
            RegisterFlow.arm(registerState, selectedSection)
        } else {
            RegisterFlow.disarm(registerState)
        }
    }

    /** Register-mode tap: touches only the pending slot, never the attendance log. */
    fun onRegisterTap(bytes: ByteArray) {
        val asRead = Uid.canonical(bytes)
        val flipped = Uid.reversed(bytes)
        noteTap(asRead, flipped)
        registerState = RegisterFlow.onTap(
            registerState,
            if (uidReversed) flipped else asRead,
            selectedSection,
        )
    }

    fun skipPendingCard() {
        registerState = RegisterFlow.cancelPending(registerState)
    }

    fun saveRegistration(name: String) {
        val outcome = RegisterFlow.save(registerState, name, sections, selectedName)
        registerState = outcome.first
        if (outcome.second != sections) {
            sections = outcome.second
            store.saveSections(sections)
            sectionsMessage = outcome.first.message
        }
    }

    // ---------------------------------------------------------------------- scan

    fun startSession() {
        val section = selectedSection ?: return
        if (section.students.isEmpty()) return
        val now = System.currentTimeMillis()
        val id = "S-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        session = AttendanceSession(
            sessionId = id,
            sectionName = section.name,
            startedAtMillis = now,
            taps = emptyList(),
            roster = section.students,
        )
        running = true
        paused = false
        reportPath = null
        reportError = null
        reportUri = null
        lastTapKnown = null
        statusText = "Session " + id + " started for " + section.name + ". Tap student IDs one by one."
    }

    fun stopSession() {
        val current = session ?: return
        running = false
        paused = false
        store.saveSession(current)
        statusText = "Session " + current.sessionId + " saved with " + current.taps.size + " tap(s)."
    }

    fun onScanTap(bytes: ByteArray) {
        val asRead = Uid.canonical(bytes)
        val flipped = Uid.reversed(bytes)
        noteTap(asRead, flipped)

        val uid = if (uidReversed) flipped else asRead
        val current = session
        if (current == null || !running) {
            lastTapKnown = null
            statusText = "No session running - tap of " + uid + " ignored."
            return
        }
        if (paused) {
            lastTapKnown = null
            statusText = "Paused - tap of " + uid + " not recorded. Press Resume when the class is ready."
            return
        }
        val now = System.currentTimeMillis()
        val previous = current.taps.lastOrNull()
        if (previous != null && previous.uid == uid && now - previous.atMillis < 3000L) {
            statusText = "Repeat tap of " + uid + " ignored (same UID within 3 s)."
            return
        }
        val student = current.roster.firstOrNull { Uid.normalize(it.uid) == uid }
        lastTapKnown = student != null
        val at = ReportBuilder.timeText(now)
        statusText = if (student != null) {
            "Present: " + student.name + " (" + uid + ") at " + at
        } else {
            "NOT ON ROSTER at " + at + ": " + uid + " - flagged, not dropped."
        }
        session = current.copy(taps = current.taps + Tap(uid, now))
    }

    fun resolved(): ResolvedAttendance? {
        val current = session ?: return null
        return AttendanceResolver.resolve(
            roster = current.roster,
            taps = current.taps,
            startedAtMillis = current.startedAtMillis,
            lateAfterMillis = lateAfterMillis(),
        )
    }

    /** The window in milliseconds, from the stored minutes. */
    fun lateAfterMillis(): Long = lateAfterMinutes.toLong() * 60L * 1000L

    /**
     * Pause keeps the session open - it only stops recording. The reader itself is
     * switched off in AppRoot while paused, so the phone is not listening in class.
     */
    fun togglePause() {
        if (!running) return
        paused = !paused
        statusText = if (paused) {
            "Paused at " + ReportBuilder.timeText(System.currentTimeMillis()) +
                ". Taps are not recorded until you press Resume."
        } else {
            "Resumed at " + ReportBuilder.timeText(System.currentTimeMillis()) +
                ". Late arrivals count as late."
        }
    }

    // -------------------------------------------------------------------- shared

    private fun noteTap(asRead: String, reversed: String) {
        recentTaps.add(0, RecentTap(asRead, reversed))
        while (recentTaps.size > 5) recentTaps.removeAt(recentTaps.size - 1)
    }

    private fun safeFileName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.isEmpty()) "section" else cleaned
    }
}