# Audit & roadmap - Presencia NFC Attendance Checker

**Scope.** Read-only audit of the 0.0.3 tree (`app/src/main/java/com/nezzar/nfcattendance`, 39 Kotlin files,
~7,830 lines), the manifest, both backup rule files, `app/build.gradle.kts` and the two earlier docs in this folder.
Method: full reads of the state machine, the store, the resolver, the scanner, the export layer and all four tab
screens, plus targeted greps to prove the negative claims (no `ViewModel`, no `rememberSaveable`,
no `SavedStateHandle`, no `onSaveInstanceState`, no `Dispatchers`/`withContext`, no `loadSessions`).
No emulator was running during this pass, so nothing here is a new device capture; the one screenshot cited is an
existing current-build capture from `app/build/nfc-artifacts/`.

**What this supersedes.** `docs/UI-UX-AUDIT.md` (2026-10-04, ten visual findings) and its
`docs/UI-UX-CHECKLIST.md` are treated as *already closed* and are **not** repeated here. Verified still fixed in the
code: outline contrast, the first-run path with self-explaining disabled buttons
(`ui/ScanScreen.kt:127-147`), the persistent `ShellHeader`, export placed directly under the Result tiles
(`ui/ReportScreen.kt:105-124`), the single accent rule in `StatTile`, one-line subtitles, the file-name export
confirmation (`ui/Common.kt` `ExportResult`), the two-step student/section confirms, and the `SessionBar`
`ownedHere` de-duplication (`ui/App.kt:472-481`).

This pass is about what a visual sweep cannot see: durability, data integrity, correction paths, and state
architecture.

---

## 1. Findings, severity-ranked

### Blocker

**B1 - A live attendance session exists only in RAM.**
`ui/AppState.kt` is constructed in `onCreate` and held as a local (`MainActivity.kt:23`). There is no
`ViewModel`, no `SavedStateHandle`, no `rememberSaveable` and no `onSaveInstanceState` anywhere in the app
(grep: zero hits), and the manifest declares no `configChanges` (`AndroidManifest.xml:20-26`). `session` lives in
`mutableStateOf` and is written to disk only in `stopSession()` (`ui/AppState.kt:660-666`).
Consequences of one rotation, or of Android killing the process while the teacher pockets the phone:
- every tap of the session is gone, with no warning and no recovery path;
- the card already read in Register is gone (`registerState`), and the name being typed is gone too, because it is a
  plain `remember` (`ui/RegisterScreen.kt:69`), so the teacher must re-tap and re-type;
- the open page, the current tab and the import name all reset.
**Fix.** `AttendanceViewModel` + persist the session on every tap (see §3). Put only the session *id* in
`SavedStateHandle` and re-read the session from disk - never the roster, which is a `TransactionTooLargeException`
waiting to happen at 100 students.

**B2 - A damaged `sections.json` reads as "no classes", and the next edit overwrites it.**
`Store.write` is a bare `file.writeText` with no temp-file-and-rename (`data/Store.kt:34-36`), so a process kill
mid-write leaves a truncated file. `loadSections` catches every throwable and returns `emptyList()`
(`data/Store.kt:41-42, 77-80`) - deliberately, and the comment says so. The UI then shows the ordinary empty state
("No sections yet. Create one...", `ui/AppState.kt:97`). The teacher sees their whole timetable vanish, creates one
section to get going, and `saveSections` - which rewrites the entire file - serialises an array of length one
(`data/Store.kt:85-105`). The original data is now gone for good.
This is the single most dangerous path in the codebase because the failure is silent and then self-inflicted.
**Fix.** Atomic write (`tmp` + `renameTo`), a `Result<List<Section>>`-typed load, and a distinct "your file could
not be read - nothing has been changed" state that blocks saving until the user decides.

### High

**H1 - The only feedback that names the student is below the fold.**
`ui/ScanScreen.kt` renders, in order: the section face hero (`:58`), the title (`:62`), the reader card
(`:70`) and the whole "This session" card (`:177`) - and the sentence that says who was just recorded
(`"Present: Maria Santos (04A1B2C3) at 07:42:10"`, `ui/AppState.kt:692-699`) is the **last** thing in that card
(`ui/ScanScreen.kt:222`). The current-build capture `app/build/nfc-artifacts/lite2-04-scan-hero.png` shows the hero
card and the Start button filling the viewport: everything under it is at least one scroll away.
So the beep and the buzz prove a tap *registered*, never *who*, and never whether the card was even on the roster.
For a teacher working down a queue of 40 students, that is the one fact the screen exists to deliver.
**Fix.** A sticky last-read line at the top of the Scan column - name (or "NOT ON ROSTER"), running present/taps
count, and the reader dot - 44-56 dp, above the hero card.

**H2 - The late window retroactively rewrites history, including the exported sheet.**
`resolved()` passes the *current* setting on every call (`ui/AppState.kt:702-713`), and `exportReport()` builds the
workbook with the current value too (`ui/AppState.kt:435`). `AttendanceSession` has no window field
(`data/Models.kt:38-44`). Change "Late after" from 15 to 30 in Settings mid-term and yesterday's report silently
moves students from Late to Present; export again and the file handed to the registrar differs from the one handed
out last week, with no visible change.
**Fix.** Store `lateAfterMinutes` in the session at `startSession()` and resolve from that snapshot. Settings then
governs new sessions only, which is also the only behaviour a teacher would predict.

**H3 - There is no way to correct attendance.**
The only write to the log is a physical tap (`ui/AppState.kt:668-700`); absence is defined as roster-minus-taps
(`data/AttendanceResolver.kt:39-47`). No undo, no "mark present", no post-session edit. One broken or lost card
therefore produces a permanent, unexplained *Absent* in an exported document - the exact case a school will ask
about. This is the highest classroom value in the feature list (§2) and it is a correctness gap, not a nicety.
**Fix.** "Undo last tap" on the sticky line, plus long-press/menu "Mark present" on an absent row; record manual
marks with a source flag so the export keeps its provenance.

**H4 - `allowBackup="true"` ships names and card UIDs to cloud backup while the app says nothing is uploaded.**
`AndroidManifest.xml:12-14` points at `backup_rules.xml` and `data_extraction_rules.xml`, and both are still the
untouched samples (empty `<full-backup-content>` / empty `<cloud-backup>`), so the default applies: the whole
private `filesDir` - including `nfc-attendance/sections.json` with every student name and NFC UID - goes to the
user's Google Drive backup. Meanwhile `ui/SettingsScreen.kt:253` tells the teacher *"No server, and no network
permission - nothing is uploaded."*
**Fix.** Either exclude `domain="file" path="nfc-attendance/"` in both rule files, or set `allowBackup="false"`. If
backup is kept deliberately as free device migration, the Settings line must say so.

**H5 - Registering a student during a live session cannot mark them present.**
`startSession` snapshots the roster into the session (`ui/AppState.kt:639-651`) - correctly, so later roster edits
cannot rewrite an in-flight session - but `saveRegistration` only writes `sections`
(`ui/AppState.kt:627-637`). Register is reachable mid-session from a section card
(`ui/SectionsScreen.kt:370`). The new student's card then lands in **Unmatched**, and they stay *Absent* in a report
that claims to be exhaustive.
**Fix.** Add the student to the running session's roster as well as the section, so the next tap resolves.

### Medium

| # | Finding | Evidence | Fix |
|---|---|---|---|
| M1 | `sessions.json` is write-only and unbounded: `saveSession` appends and *nothing* ever reads it back (no `loadSessions` exists). No history, no prune, no way back after End session - which is itself unconfirmed on both paths. | `data/Store.kt:166-193`, `ui/AppState.kt:660-666`, `ui/ScanScreen.kt:161-169`, `ui/App.kt:536-542` | Session history + reopen (F2), and a confirmation on End that states the counts |
| M2 | Errors are scattered across five channels (`sectionsError`, `rosterError`, `reportError`, `importError`, `statusText`) rendered in five different places, and both share URIs are memory-only, so Share disappears on process death | `ui/AppState.kt:98-124, 488-500` | One `UiMessage` channel rendered by one host; derive the share URI from the stored path |
| M3 | The roster filter exists only on the Roster page and only above 8 students; the Scan tab - where a 100-student roster is actually worked - has no filter or "not yet tapped" view | `ui/RosterScreen.kt:66, 121-131` | A filter chip row on Scan (All / Absent / Not yet tapped) |
| M4 | The NFC light encodes state by colour alone and carries no `contentDescription`; on the SessionBar row it is the only indicator of reader state | `ui/NfcLight.kt:70-101` (no `semantics`), used at `ui/App.kt:521` | `semantics { contentDescription = "NFC " + word }` + a one-word label beside the dot |
| M5 | Every write is synchronous file IO on the main thread: `sections.json` is re-serialised in full inside click handlers | `data/Store.kt:34-36`, `ui/AppState.kt:277-287` | `Dispatchers.IO` behind a suspend store; debounce bursts |
| M6 | UID validation is implemented and the importer uses it, but there is no manual entry path - a card can only enter the app by being physically tapped or by importing a workbook | `data/Uid.kt:30-34`, used at `data/RosterImporter.kt:114` | A "paste a list / type a UID" path in the section page (F4) |

### Polish

- `ui/ScanScreen.kt:80-99`: with NFC present and on but **no session running**, the line says "Reader mode active -
  hold each student ID to the phone's NFC antenna", while taps are discarded (`ui/AppState.kt:675-679`). It should
  say "Reader idle - press Start session".
- The scan tone is a `ToneGenerator` on `STREAM_NOTIFICATION` at a fixed volume (`ui/App.kt:107-115`), which plays
  even when the phone is muted. In a quiet classroom that is a real embarrassment; consider respecting the ringer
  mode, or defaulting the sound off.
- `DocumentsExport.displayPath` builds the shown path from `Environment.getExternalStorageDirectory()`
  (`data/DocumentsExport.kt:29-32`) rather than querying the row, so the confirmation can print a path that does not
  exist on adopted/additional storage.
- No `windowSizeClass` or horizontal layout anywhere; tablets and Chromebooks get a stretched phone column.

---

## 2. Features, ranked by classroom value

| # | Feature | Value | Effort | Risk if skipped | Smallest worthwhile version |
|---|---|---|---|---|---|
| F1 | **Fix a wrong tap**: undo last tap + mark present/late by hand | Highest | S | One broken card = a permanent Absent in an official-looking file | Undo on the sticky line; "Mark present" from an absent row, recorded with a source flag |
| F2 | **Session history + reopen a past session** | High | M | End session is a one-way door; a mis-tap costs the whole session | List `sessions.json` on the Report tab (the data is already on disk) with a read-only reopen; needs H2 first |
| F3 | **Quick-add an unmatched card** | High | S | The Scan tab already flags unmatched cards and does nothing with them | A button on the unmatched row that registers that UID into the section *and* the live session (H5) |
| F4 | **Paste / bulk import a class list** | High for onboarding | M | 100 students means 100 taps to set up before the app does anything | A textarea accepting `Name<TAB>UID` lines, validated with the existing `Uid.isValid` |
| F5 | **Per-student attendance rate** | Medium | M | Sessions pile up on disk unused | One line on the student page: "Present 8 of the last 10 sessions" |
| F6 | **Filter chips on Scan** (all / absent / not yet tapped) | Medium | S | Long rosters are unreadable at a glance | Three chips above the session card |
| F7 | **NFC tag writing / provisioning** | Medium | L | Blank cards cannot be prepared by the app | Do not write tags yet - the safer half (read a card, label it to a student) is F1+F3 |
| F8 | **Tablet / horizontal layout** | Low-medium | L | Feels like a stretched phone on a Chromebook | Defer until B1 is fixed |
| F9 | Photo/receipt of the exported sheet | Low | M | None - the .xlsx opens in Sheets | Skip; it would add a camera permission |

Explicitly **not** recommended: any multi-teacher, multi-school or cloud sync feature. It is the one requirement that
would break the manifest's single `NFC` permission, and that permission list is part of the product's promise.

---

## 3. How I would have built it

**State.** The root cause of B1, H5 and the untestable UI is that one 742-line object
(`ui/AppState.kt`) is simultaneously the state holder, the repository, the xlsx byte factory, the import pipeline,
the navigation graph and the error channel. The target shape:

- `AttendanceViewModel : ViewModel()` created in `MainActivity`, exposing one
  `StateFlow<UiState>` plus a `(Event) -> Unit` sink. Screens become functions of `uiState` - which is the shape
  the pure objects already want.
- **Keep** `Sections`, `RegisterFlow`, `AttendanceResolver`, `ReportBuilder`, `Uid`, `XlsxReader/Writer` exactly as
  they are. They are Android-free, pure, and the reason 69 JVM tests run without Robolectric. That is the best
  engineering decision in the repo and I would defend it in review.
- A `savedStateHandle` holding the session **id** only; the session itself is re-read from disk.

**Persistence.** `Store` becomes an interface with a `FileStore` implementation whose methods are `suspend` and run
on `Dispatchers.IO`:
- **atomic writes** - write to `x.json.tmp`, then `renameTo` (fixes B2's truncation);
- **typed failures** - `Result<List<Section>>` instead of `emptyList()` on a parse error, surfaced as a real state;
- **a schema version** - `{"version": 2, "sections": [...]}` today is an unversioned object and `sessions.json` a bare
  array; the legacy defaults already exist (`updatedAt` falls back to 0 at `data/Store.kt:72`), so the v1→v2
  migration is the identity;
- **durable sessions** - persist on start and append on every tap (a 100-student session is ~100 rows), and prune in
  the same write (last 50 sessions or 12 months). B1 then stops being data loss and becomes a redraw.

**Errors.** Replace the five scattered error fields with one `UiMessage(id, text, severity)` host, so an export
failure is reported where the teacher is looking rather than inside a card they may have scrolled past.

**Order of work, realistically one week:**

1. `AttendanceViewModel` + `SavedStateHandle` holding the session id, session re-read from disk. (Days 1-2)
2. Atomic, versioned writes; `Result`-based load errors; persist-per-tap. (Days 2-3)
3. Move the store onto `Dispatchers.IO` behind suspend functions. (Day 4)
4. The sticky last-read line on Scan (H1). (Day 5)
5. End-session confirmation stating the counts, and the session list. (Day 6)
6. Snapshot the late window into the session (H2), plus tests for each of the above. (Day 7)

**If only one change were allowed tomorrow:** persist the session on every tap and restore it from
`SavedStateHandle`. It is the only finding that can destroy a class's attendance with no way back, it is a
prerequisite for F1/F2/F3/F5, and it removes the reason the app currently cannot afford to show the teacher what just
happened.

---

## 4. Verification notes

- Confirmed by reading the code, with line references above: B1, B2, H1, H2, H3, H4, H5 and every Medium finding.
- **Confirmed absent** by grep across the whole `com/nezzar/nfcattendance` tree: `ViewModel`, `SavedStateHandle`,
  `rememberSaveable`, `onSaveInstanceState`, `Dispatchers`, `withContext`, `StateFlow`, `suspend`,
  and any `loadSessions`.
- **Corrected during review:** an earlier draft of this audit claimed `Uid.isValid` was dead code. It is not - it is
  called at `data/RosterImporter.kt:114`. The real gap is that no UI exposes manual UID entry (M6).
  A second draft claim that the unsupported-device NFC dot is invisible was also checked and dropped: the border at
  `ui/NfcLight.kt:90-98` does apply, so the dot reads as an outlined circle; the genuine issue is the missing
  `contentDescription` (M4).
- Not verified: anything requiring a device or a real card. The emulator was not running during this pass, so
  B1/H2 are proven from the code, not from a capture. The recommended first step for any fix round is a 10-minute
  device check: start a session, rotate the phone, and watch the session disappear.
- Existing captures referenced: `app/build/nfc-artifacts/lite2-04-scan-hero.png` (current build, light theme) for H1.
---

## 5. On-device verification (2026-10-07, emulator-5554)

Run through the Android Studio MCP (`as_*` tools): AVD `Medium_Phone_API_37.0`, the debug APK installed with
`as_adb_install`, driven with `as_adb_input`, captured with `as_adb_screenshot`. Captures live in
`_inbox/presencia-mcp/`. The bench data was backed up before the destructive test and restored afterwards.

| Finding | Test | Result |
|---|---|---|
| **B1** session durability | Started a live session on the Scan tab, then rotated the device (`settings put system user_rotation 1`) | **Confirmed.** Before: "Pause / End session" and a glowing selected card. After: the tab itself had reset to Sections, the `SessionBar` was absent, and the Scan tab was back to "Start session". The session did not survive the configuration change. |
| **B1** nothing persisted | `run-as com.nezzar.nfcattendance ls -l files/nfc-attendance`, mid-session and after | **Confirmed.** `sessions.json` still carried its 2026-10-05 08:32 timestamp while the live session started at 17:56. Nothing was written to disk during the session, so a process kill loses it just as a rotation does. |
| **B2** silent read failure | Replaced `sections.json` with a truncated file (what a write killed mid-flush leaves behind), force-stopped and relaunched | **Confirmed.** The app rendered its ordinary first-run empty state: "No sections yet", header pill "No section". No error, no warning - indistinguishable from a fresh install. |
| **B2** data loss | Then tapped "Create your first section" and created one section | **Confirmed, unrecoverable.** `sections.json` went from 3 sections / 9 students to `{"sections":[{"name":"RECOVERED","students":[],"card":"6♥"}]}`. The original file was overwritten. Only the pre-test backup made this recoverable. |
| **H1** feedback placement | Position of the status line in the Scan column | Confirmed from the current-build capture `lite2-04-scan-hero.png`; the hero card, title, reader card and Start button fill the viewport. |
| Landscape layout | Rotated on the Scan tab | The hero card and the whole session card are pushed off-screen; landscape is a stretched portrait column. Confirms the polish finding. |

Not verified on device, and still code-only: **H2** (retroactive late re-classification) and **H3** (no correction
path) both need a card tap, and this emulator has no NFC adapter. **H5** likewise. **H4** needs a configured backup
account to observe end-to-end; the manifest and both rule files are conclusive on their own.

Bench state afterwards: `sections.json` restored to 3 sections / 9 students, verified by read-back and by capture.
The emulator was left running.

Tooling note: `as_gradle_assemble` through MCP timed out (>600 s) on this project and did not report a result. It
was not needed here - only launcher icon assets were newer than the APK, so the installed binary already contained
the exact Kotlin logic under audit - but a future verification pass that depends on a fresh build should run Gradle
as a background job rather than through that call.

---

## 6. What shipped in 0.0.4

Both blockers and H1-H3, verified on emulator-5554 with the Android Studio MCP, plus the redundancy pass.

| Finding | What changed | Verified by |
|---|---|---|
| **B1** session durability | `AppState` is an `AndroidViewModel` created through `ViewModelProvider` (`MainActivity.kt`), so it outlives a configuration change; the running session is written to disk on start, on every tap and on pause (`ui/AppState.kt` `persistSession`), and `Store.liveSession()` restores it on the next launch. No new dependency: `lifecycle-viewmodel` already arrives through `activity-compose`. | Started a session, rotated: still running. Force-stopped the process and relaunched: the session came back and the SessionBar announced it. |
| **B2** silent data loss | `Store.write` is write-then-rename; `loadSections()` returns `SectionsLoad(sections, error, missing)`; while `sectionsBroken` is set the app refuses every write (`applyEdit`, `confirmImport`); the Sections tab shows a recovery card with *Try again* and *Start fresh*; *Start fresh* renames the file to `sections.json.unreadable-<stamp>` instead of deleting it. Both stores now carry `version: 2` and still read v1 files. | Truncated `sections.json` on the device: the recovery card appeared, and the file was byte-identical afterwards (36 bytes, untouched). *Try again* restored all three sections. |
| **H1** feedback below the fold | A non-scrolling bar at the top of the Scan column: the reader dot, the name just recorded (or NOT ON ROSTER), the tap count and Undo (`LastReadBar` in `ui/ScanScreen.kt`, state in `AppState.lastRead`). | Seen on the device, with the session controls no longer pushed under the bottom bar. |
| **H2** retroactive late window | `AttendanceSession.lateAfterMinutes` is snapshotted at `startSession()`, `AttendanceResolver.windowFor()` is the single place that chooses it, and both `resolved()` and the export use it. Settings now governs new sessions only, and the Late-after card says so. | `AttendanceResolverTest.aSessionIsJudgedByTheWindowItWasStartedWith`, plus the setting note on the device. |
| **H3** no correction path | Undo on the sticky line; *Mark present* on every absent row of the report; `Tap.source` (`CARD`/`MANUAL`) travels through the resolver into a new **Recorded** column, and a "Marked by hand" count on the Absent sheet. | Marked Ana Reyes on the device: tiles went 6 absent 0 present to 5/1, the sticky line read "Marked by hand: Ana Reyes", and `sessions.json` held `"source":"MANUAL"`. |
| Redundancy | Drawer deleted (Settings + How to use are header icons); hero card off Scan, Report and Register; Scan's duplicate Section/Roster/Taps/Late-after/Reader rows gone; dead chip row, `CardActionRow`, `SectionBadge`, `selectedUpdatedText` and `ic_delete.xml` deleted; duplicate bottom Close/Back buttons gone; "Manage" renamed "Edit class"; Settings trimmed with an Advanced heading and Your data merged into About. | Built and captured on the device: 3 tabs, no drawer, Scan fits its controls, Report offers Mark present. |

Tests: **71 passing, 0 failures** (69 before, plus the late-snapshot test and the provenance test). The two `XlsxReaderTest` cases that pinned the old four-column sheet were updated for the new `Recorded` column.

### Deliberately left open

- **H4** (`allowBackup="true"` still ships names and card UIDs to cloud backup, while Settings says nothing is uploaded). One line in two rule files, but it changes a backup behaviour the user may want - out of the requested scope.
- **H5** (registering a student mid-session still cannot mark them present, because the session's roster is a frozen snapshot).
- The Register and Scan reader cards remain two copies of the same sentences; extracting one `ReaderCard` is an internal dedupe with no visible change.
- The card-flight animation (`armCardFlight`) is now inert - arming it was removed with the hero card, and `SectionFace.kt`'s flight branch simply never triggers. Removing the mechanism entirely would touch the class page's entrance for no user-visible gain.
- The export still cannot always replace an earlier file of the same name on API 29+ when the old copy belongs to a previous install.
---

## 7. The QR fallback (0.0.4)

**The requirement that shaped it.** The QR on the school's ID cards carries a **student number**, and the rule is
that only names are acquired. A student number is never read, kept, shown or exported, so a scanned code cannot
identify anybody by itself. Resolution is therefore by **name**, confirmed by the teacher, then verified against the
card UID.

**Dependencies, chosen and explained before they were added** (the user approved this pair):

- `com.google.zxing:core:3.5.3` - pure-Java decoder *and* encoder, Apache-2.0. No Android framework, no Play
  Services, no network.
- `androidx.camera:camera-core` / `camera-camera2` / `camera-lifecycle` / `camera-view` `1.4.2` - Jetpack, the same
  family as the app's existing dependencies. Only `CameraController.IMAGE_ANALYSIS` is enabled, so the app cannot
  take a photograph or record video with the camera it borrows.
- Cost, measured on the shipped artifact: APK **13,047,721 to 15,285,143 bytes (+2.24 MB)**, and one manifest change: `CAMERA`, declared
  optional. `INTERNET` is still absent - checked on the built APK, which declares NFC + CAMERA only.

**One gate for every reader.** `AttendanceProcessor` is the single decision point: `process()` for anything that
carries an identifier (an NFC UID, or a code the app printed), `processForStudent()` for a code the teacher resolved
by hand. Both funnel into the same private `decide()`, and the late rule lives in `AttendanceResolver.statusOf` so
the processor and the report cannot disagree. `AttendanceMethod` (NFC / QR / Manual) rides on every `Tap` and lands
in a `Method` column in the export, with hand-recorded and QR-recorded counts on the Absent sheet.

**What a scan means** (`QrCode.read`, pure and tested):

- `presencia:<uid>` - a code the app printed: resolves to that card UID, no question asked.
- text containing a roster **name** - suggests that student, tolerating case, spacing, punctuation and URL-encoding.
- anything else, **including a bare eight-character string** - asks the teacher. A student number and a card UID can
  look identical, so the app never guesses; the scheme prefix is required for automatic resolution.
- empty - nothing was read.

**Verified on the device** (emulator-5554, through the Android Studio MCP):

- the NFC | QR switch renders and switches, the reader label follows, and only one reader is ever armed
  (`App.kt`: `tapping = overlay == REGISTER || (screen == SCAN && scanMode == NFC)`).
- the runtime camera permission is requested, and the CameraX preview comes up live once it is granted.
- the student page draws the QR code, and **decoding that screenshot with ZXing** yields exactly
  `presencia:04A1B2C3` - the card UID and nothing else.

**Not verified, and why:** the end-to-end *camera reads a printed code, confirm, record* loop. The emulator's camera
renders a synthetic virtual scene with no QR in it, and making one appear would mean adding a test hook or a stubbed
scanner to production code, which was explicitly ruled out. That last step needs a real phone and a real ID;
everything either side of it is covered by the 89 unit tests and the device checks above.

---

## 8. Session analytics (0.0.4)

**Three lenses, one at a time**, chosen in Settings > Chart and remembered: a **pie** of on time / late / absent
over the roster, a **line** of cumulative reads against minutes since the start with the session's own late
boundary, and a **bar** of how each presence was recorded (NFC / QR / Manual).

**The design decision that mattered: unmatched taps are not a slice.** They are cards that matched nobody on the
roster, so they are not students. A four-slice pie would sum past `session.roster.size` and contradict the exported
sheet. They are counted separately - a footnoted grey bar and a legend row - and a test asserts the slices always sum
to the roster.

**No dependency.** The charts are Compose Canvas, like the app's card art and its QR codes: the APK grew ~44 KB.
Compose Text carries every label (the hole count, the legend, the axis ends) rather than painted text, so the figures
scale with the system font size. Late is a **hatch as well as an amber tone**, because green and amber are the pair a
colour-blind reader is most likely to confuse, and the legend writes every figure out.

**The honest-empty rule.** A kind with nothing truthful to draw steps aside for the pie and says why: a line needs two
readings, a one-student roster is not a comparison, and a session with no reads shows the pie with "unmarked, not
absent" beneath it. `ChartKind` substitution is decided in the UI, but every number comes from `ChartData`, which is
pure and covered by 12 JVM tests (101 total) - including percentages that always total exactly 100 by largest
remainder, the empty roster, the four-hour session, and the on-time/late boundary at window plus one millisecond.