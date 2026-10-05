# UI/UX checklist - NFC Attendance Checker

Built from `docs/UI-UX-AUDIT.md` (the read-only audit of 2026-10-04). Every line below was
either closed in code with a measured number, or is listed under **Still open** on purpose.

Legend: **Done** = fixed and seen on the emulator. **Partly** = fixed but one path needs a real
phone. **Open** = untouched, with the reason.

## 1. The ten audit findings

| # | Finding (audit) | Status | Where it was closed | Evidence |
|---|---|---|---|---|
| 1 | Outline / switch borders at 1.31:1 - invisible boundaries | **Done** | `ui/theme/Color.kt` (BrandOutline #2E2E2E -> #6B6B6B), `ui/theme/Theme.kt` (outline vs a new hairline token for dividers) | measured 3.33:1 on cards, 3.52:1 on the page; `v2-02-firstrun.png` |
| 2 | First run is a dead end: form on top, Scan disabled with no reason | **Done** | `ui/SectionsScreen.kt` (one create card), `ui/Common.kt` (EmptyState with an action), `ui/ScanScreen.kt` + `ui/RegisterScreen.kt` (disabled buttons explain themselves) | `v2-02-firstrun.png`, `v2-03-scan-empty.png` |
| 3 | No persistent header - scrolled content runs under the status bar | **Done** | `ui/App.kt` (ShellHeader: TopAppBar with the screen name plus a section pill) | `v2-05-sections-roster.png` |
| 4 | Report buries Export below the lists | **Done** | `ui/ReportScreen.kt` (export card moved directly under Result) | `v2-08-report-top.png` |
| 5 | One section shown twice, plus fixed 224 dp / 150 dp widths | **Done** | `ui/SectionsScreen.kt` (duplicate identity block deleted, Manage folded, shelf 200 dp), `ui/Common.kt` (KeyValueRow is two weighted columns) | `v2-10-sections-manage.png`, `v2-23-font13-scan.png` |
| 6 | Green means success on Scan but absent on Report; a green zero | **Done** | `ui/Common.kt` StatTile accent rule, applied in `ui/ScanScreen.kt` and `ui/ReportScreen.kt` | `v2-03-scan-empty.png` (no green zero), `v2-08-report-top.png` |
| 7 | Copy over-explains; sentence-length section labels; 9-line privacy card | **Done** | one-line subtitles in all four screens; privacy text moved to `ui/TutorialScreen.kt` and `ui/SettingsScreen.kt` | `v2-09-report-bottom.png` (4 lines where 9 were) |
| 8 | Export confirmation is a raw 79-character path | **Done** | `ui/Common.kt` ExportResult (file name + folder + Share) | `v2-13-report-exported.png` |
| 9 | Student removal is silent and irreversible; Edit/Remove 24 dp apart | **Done** | `ui/SectionsScreen.kt` (two-step inline confirm, error-coloured destructive controls, wider gap) | `v2-11-remove-confirm.png` |
| 10 | Session bar repeats the screen and hides "End" | **Done** | `ui/App.kt` SessionBar shows only where the screen does not already own that state, with a labelled "End session" | `v2-07-scan-running.png` |

The audit's "fix these three first" (1, 2, 4) are all closed.

## 2. Later requests, same standard

| Request | Status | Where | Evidence |
|---|---|---|---|
| Sidebar (three lines) with Settings and a tutorial | **Done** | `ui/App.kt` (ModalNavigationDrawer + AppDrawer), `ui/SettingsScreen.kt`, `ui/TutorialScreen.kt` | `v3-03-drawer-dark.png`, `v3-04-settings-dark.png` |
| Light mode | **Done** | `ui/theme/Color.kt` (light palette), `ui/theme/Theme.kt` (ThemeMode + system-bar icons), `data/Store.kt` (persisted) | `v3-05-settings-light.png`, `v3-07-scan-light.png` |
| Declutter the working screens | **Done** | byte-order card removed from Scan and Register, privacy card removed from Report, import card reduced to its button, Manage folded | line counts 180->150, 293->263, 167->155 |
| Late arrivals: a 15-minute window plus a pause button for the rest of the class | **Done** | `data/AttendanceResolver.kt` (LATE_AFTER_MILLIS + the late branch), `ui/AppState.kt` (`paused`, `lateAfterMinutes`), `ui/ScanScreen.kt` (Pause/Resume beside End session, 2x2 tiles), `ui/ReportScreen.kt` (Late list with times), `ui/SettingsScreen.kt` (Late after 10/15/20/30), `data/ReportBuilder.kt` (`SHEET_LATE`) | `v5-04-settings-late-after.png`, `v5-05-scan-running.png`, `v5-06-scan-paused.png`; workbook dump: sheets Absent, Late, Present, Unmatched |
| Tapping a listed student opens a full-screen editor | **Done** | `ui/SectionsScreen.kt` (row is a door with a chevron, inline icons removed), `ui/StudentScreen.kt` (new page), `ui/AppState.kt` (`Overlay.STUDENT`) | `v6-01-roster-rows.png`, `v6-02-student-page.png`, `v6-03-student-remove-confirm.png` |
| Adding a section is folded into one card under the shelf, opened by the + New section tile | **Done** | `ui/SectionsScreen.kt` (the long NEW SECTION rectangle and the import card removed from the default view; the card holds the name field, Create section and Import with its preview; a `return@BrandCard` guard keeps it folded) | `v10-03-inline-new-section.png` |
| The section shelf is a centred carousel - the focused card is full size in the middle, its neighbours shrink, and a tap centres a card just like a swipe | **Done** | `ui/SectionsScreen.kt` (`HorizontalPager` + `PageSize.Fixed(200.dp)` inside `BoxWithConstraints`, `contentPadding = (maxWidth - 200.dp) / 2`, scale = 0.84f + 0.16f x closeness, `LaunchedEffect(settledPage)` selects, a click calls `animateScrollToPage`) | `v10-02-click-centered.png`, `v9-01-shelf-three-sections.png` |
| Adding a section: a small New section button beside the list opens a full-screen page for the name and the subject | **Done** | `ui\AddSectionPage.kt` (the page: name, Subbody field, Create, Import with preview), `ui\SectionsScreen.kt` (the button in the Sections: n row; the tile page and the folded card are gone) | `v13-01-shelf-with-subjects.png`, `v13-02-new-section-page.png` |
| Subject on every section | **Done** | `data\Models.kt` (Section.subject), `data\Store.kt`, `data\Sections.kt` (create with subject, setSubject that leaves the roster stamp alone), `data\ReportBuilder.kt` (LABEL_SUBJECT row), `data\RosterImporter.kt` (reads the subject; a new section adopts it), `ui\SectionsScreen.kt` (card shows it, Manage can save it) | `v13-01-shelf-with-subjects.png` |
| Tutorial on the very first launch, then never | **Done** | `data/Store.kt` (tutorialSeen), `ui/AppState.kt` (opens Overlay.TUTORIAL once) | `v4-01-first-launch-guide.png`; settings.json becomes {"tutorialSeen":true}; `v4-02-second-launch.png`; the guide is a swipeable tilted card deck (`ui/TutorialScreen.kt`, 6 cards, Animatable + detectHorizontalDragGestures, no new dependency) |
| Date and TIME on every scanned attendance | **Done (export)** | `data/ReportBuilder.kt` (dateText / timeText / stampText; Present = Name, UID, Date, Time; Unmatched = UID, Date, Time; Absent gained "Session date"), `ui/ReportScreen.kt` (present rows show time over date, Result shows Started), `ui/ScanScreen.kt` (Started), `ui/AppState.kt` (status line says "at HH:mm:ss") | workbook dump below |
| A serif headline face ("Anthropic Serif") | **Done, substituted** | `ui/theme/Type.kt` + `res/font/spectral_*.ttf` (Spectral, SIL OFL 1.1) | `v2-28-serif-sections-roster.png`; licence at `docs/FONT-LICENSE-Spectral.txt` |

## 3. Verification runs

- [x] `:app:testDebugUnitTest` - 60 tests, 0 failures (re-run after the typography change, the timestamp work and the late rule).
- [x] `:app:assembleDebug` - APK builds; 11,910,333 -> 12,438,255 bytes after the bundled serif.
- [x] `:app:connectedDebugAndroidTest` - 8 tests, 0 failures on emulator-5554 (AVD Medium_Phone_API_37.0).
- [x] Every finding above has a 1080x2400 capture in `app/build/nfc-artifacts/` plus a matching uiautomator `.xml` read-back.
- [x] Contrast computed from the literals, not guessed: outline 3.33:1 (cards) / 3.52:1 (page); text #B0B0B0 8.19:1; accent #22C55E 8.22:1; #FF6B6B 6.40:1; light accent #15803D 5.01:1 under white text.
- [x] Font scale 1.3 on all four tabs - no clipping (one truncated stat caption was found this way and fixed).
- [x] `animator_duration_scale 0` - nothing depends on an animation to appear.
- [x] Light and dark both captured on the same build, with the system-bar icons flipping.
- [x] First launch shows the guide exactly once (settings.json proof).

### Exported workbook, read back by openpyxl (the app's own writer produced it)

```
attendance-S-DEMO-0001.xlsx  (3,398 bytes)  sheets: Absent, Present, Unmatched
Absent:    Session date | 2026-01-01     Session started | 2026-01-01 08:00:00
           Name: Ben Cruz, Dan Tan, Eva Santos
Present:   Name | UID | Date | Time
           Ana Reyes | 04A1B2C3 | 2026-01-01 | 08:00:01
           Cara Lim  | 04778899 | 2026-01-01 | 08:00:02
Unmatched: UID | Date | Time
           04FFFFFF | 2026-01-01 | 08:00:03
```

## 4. Still open (with the reason)

- [ ] **Roster exports still stack copies.** Exporting BSCE-4B three times leaves
      `roster-BSCE-4B.xlsx`, `... (1).xlsx` and `... (2).xlsx` in Documents/NFC Attendance. This is the
      data layer (`data/DocumentsExport.kt`), not the UI pass, and it predates it. Fix: delete the earlier
      file of the same name before writing, using the same display-name read-back the export already does.
- [ ] **A real card tap.** The emulator has no NFC adapter, so the pending-card state ("Card read: <uid>",
      the name field, Save/Skip) and the report rows showing a scan time can only be proven on the phone
      (1c23fb4f) after Developer options -> "Install via USB" is enabled.
- [ ] **Compose UI tests cannot run on this emulator image.** `createComposeRule` fails inside
      `androidx.test.espresso.Espresso.onIdle` with
      `java.lang.NoSuchMethodException: android.hardware.input.InputManager.getInstance []`
      on API 37, so report-screen assertions must go through uiautomator or a real device instead.
- [ ] **Repository is not a git repo yet.** `git init` plus a first commit needs the user's
      `user.name` and `user.email`; remote and push are theirs.
- [ ] **The illustrated guide is still an outside HTML file** (`docs/NFC-Attendance-Checker-Guide.html`);
      the in-app version is the four-step tutorial. Re-exporting the guide after these passes is optional.

## 5. Re-running the checks

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest        # wipes app data
adb push app\build\nfc-artifacts\seed2-sections.json /data/local/tmp/
adb shell run-as com.nezzar.nfcattendance cp /data/local/tmp/seed2-sections.json files/nfc-attendance/sections.json
```