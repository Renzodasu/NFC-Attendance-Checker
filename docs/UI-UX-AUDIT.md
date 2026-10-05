# UI/UX Audit - NFC Attendance Checker

**How it was done.** Debug build (com.nezzar.nfcattendance, 1.0) on AVD Medium_Phone_API_37.0, 1080x2400 / 420 dpi
(2.625 px per dp); all four tabs were driven, every state read back with uiautomator dump plus a screenshot, and the dp
figures below are measurements from those dumps. Roster state was seeded into files/nfc-attendance/ (data/Store.kt:18-21)
so roster, export and import-preview states are real. Not observed: a real card tap / the pending-card state
(ui/RegisterScreen.kt:164-244), because the emulator has no NFC adapter; a TalkBack session (labels read from source);
any dialog, since the app opens none. Screens are in app/build/nfc-artifacts/audit/, cited as a01, a03, ... .

| Rank | Weakness | Screen / state | Impact | Frequency | Fix effort |
| --- | --- | --- | --- | --- | --- |
| 1 | Outlined buttons and switch borders invisible | all tabs | 9 | always | S |
| 2 | First run dead-ends, no path in | Sections + Scan empty | 8 | always | M |
| 3 | No persistent header, text under status bar | any scrolled screen | 8 | often | M |
| 4 | Report buries Export below the lists | Report with absents | 8 | always | S |
| 5 | One section shown twice, fixed widths | Sections with roster | 7 | always | M |
| 6 | Green means success here, absent there | Scan vs Report tiles | 7 | always | S |
| 7 | Copy over-explains, sentence labels truncate | every screen | 7 | always | M |
| 8 | Export confirmation is a raw file path | after any export | 7 | always | S |
| 9 | Student removal is silent and final | Sections roster | 6 | rare | S |
| 10 | Session bar repeats the screen, hides End | while a session runs | 6 | often | M |

### 1. Outlined buttons and switch borders are invisible
**Looks like.** The borders on "End session", "Delete section", "Cancel", "Import section from .xlsx", "Share this
.xlsx" and the byte-order Switch are a hairline that sinks into the card behind them; they read as disabled.
**Evidence.** ui/theme/Color.kt:1-21 (BrandOutline #2E2E2E on BrandSurface #181818) = 1.31:1, 1.38:1 on the page
background (WCAG wants 3:1 for UI boundaries); a07 (End session), a03 (Delete section), a17 (Switch), a01 (Import).
**Why it hurts.** It is the most unfinished-looking detail in the app, and five secondary controls - two of which end
or destroy data - are found by reading rather than seeing.
**Fix.** Use about #4A4A4A (approx. 3.2:1 on #181818) for OutlinedButton borders and the Switch track; keep #2E2E2E
for dividers.

### 2. First run dead-ends: a form on top, a dead primary button, one-line empty states
**Looks like.** A fresh install opens on an essay and a "New section" form filling the top half, with ~600 px of empty
space below the Import card; Scan's only primary action is greyed out with no reason and Report's empty state is one
muted line.
**Evidence.** ui/SectionsScreen.kt:86-118 (create card first), :116 and :124 ("No sections yet" twice, two wordings),
:120 ("SECTIONS: 0"), a01; ui/ScanScreen.kt:74-95 (enabled = state.roster.isNotEmpty()), a17;
ui/ReportScreen.kt:48-51 and ui/RegisterScreen.kt:236-243 (one-line empty states).
**Why it hurts.** It reads as a settings page, not a tool; the user must guess the
create-then-register-then-scan order and cannot tell why Scan refuses to start.
**Fix.** On empty Sections show one line of purpose, a "Create your class section" button opening the form, then a
three-step strip (Create, Register, Scan); on Scan with no roster show "Register students first" with a one-tap "Open
Register"; on Report, an icon, a sentence and "Go to Scan".

### 3. No persistent header, and scrolled content runs under the status bar
**Looks like.** Once a list scrolls the title fades to ~10% alpha with nothing replacing it, while body content keeps
an 8 dp top inset, so rows slide under the clock and a label can sit behind the status bar.
**Evidence.** uiautomator reports "SELECTED SECTION" at y=63 px in a03 (the status bar covers roughly y<110);
ui/Common.kt:78-106 (scale 1-0.18s, alpha 1-0.9s from a 260 px offset); ui/App.kt:80-108 (no topBar);
ui/SectionsScreen.kt:74 (top = 8.dp).
**Why it hurts.** The screen loses its identity, collides with system chrome, and one label becomes unreadable.
**Fix.** Pin a compact 56 dp header strip with the screen name (or stop the collapse at ~0.5 alpha / 0.95 scale) and
set contentPadding.top to 56 dp plus the status-bar inset.

### 4. Report buries its primary action below the lists
**Looks like.** Below the result tiles sit every absent name in its own full-width card and then two sub-sections
whose whole content is a heading ending in "0", so with six absentees "Export .xlsx report" is off-screen and needs
three swipes.
**Evidence.** a08 (button outside the viewport) vs a08b (button at y=914 only after scrolling); ui/ReportScreen.kt:81-93
(absent cards), :95-105 and :107-117 (empty sub-sections), :119-169 (export card).
**Why it hurts.** The page has no climax, and the loop's last step is hidden behind everything else.
**Fix.** Move the export card under the RESULT tiles (or pin it above the session bar) and hide PRESENT / UNMATCHED
when their counts are zero, instead of printing an empty heading.

### 5. One section is shown twice, and fixed widths waste space
**Looks like.** Sections shows the selected section twice: a 224 dp green card leaving ~55% of its row empty, then a
"SELECTED SECTION" card repeating the name and date.
**Evidence.** ui/SectionsScreen.kt:222-261 (width at :228) vs :384-428, a02 (both on screen); a12 (the 150 dp label
column wraps "Sheet Roster - section from the file" while its value stays on line one, ui/Common.kt:144-158); a13 (the
same card's date wraps at 1.3x font scale).
**Why it hurts.** The most repetitive screen in the app, and the wrapped label breaks the alignment that makes
key/value rows scannable.
**Fix.** Drop the duplicate card and put Rename / Delete / the roster under the selected shelf card; size the card from
content (min 200 dp) and use widthIn(min = 120.dp, max = 150.dp).

### 6. Green means "success" on one tab and "absent" on another
**Looks like.** Scan paints the PRESENT tile in the accent green, so an untouched session shows a green "0 PRESENT",
while Report paints ABSENT green because that is the conclusion list.
**Evidence.** a17 and a07 (green 0 PRESENT, ui/ScanScreen.kt:103-120) vs a08 (green 6 ABSENT,
ui/ReportScreen.kt:53-79).
**Why it hurts.** The accent stops meaning anything, and the user must read each caption to know whether green is good
or bad.
**Fix.** Give the accent one job - the figure that matters on that screen (PRESENT while scanning, ABSENT on the
report) - and leave the other tiles neutral, uncoloured while their count is 0.

### 7. Copy over-explains, and sentence-length labels truncate
**Looks like.** Every screen opens with a two-to-four line paragraph and cards carry further notes; one label is a
sentence cut off mid-word ("UNMATCHED TAPS (UID NOT ON THE ROSTER - FLAGGED, NO..."), Report's privacy card is nine
lines of RA 10173 text, and empty Sections says "No sections yet" twice.
**Evidence.** ui/ReportScreen.kt:41-43 (four-line subtitle), :107-117 (truncated label) and :171-187 (privacy block,
~700 px) in a09; ui/SectionsScreen.kt:80-81, :116 and :124 in a01; ui/ScanScreen.kt:146-149 and
ui/RegisterScreen.kt:264-267 (notes); ui/Common.kt:49-58 (labels are maxLines = 1 + ellipsis).
**Why it hurts.** Walls of grey text and a truncated heading read as unfinished and bury the numbers.
**Fix.** Cut subtitles to one line, shorten labels to nouns ("UNMATCHED TAPS: 0"), move the RA 10173 text behind a
"Privacy" row opening a sheet, and delete the duplicate "No sections yet" line.

### 8. Export confirmation is a raw absolute path
**Looks like.** After an export the card grows a green 79-character path ("Written: /storage/emulated/0/Documents/NFC
Attendance/attendance-S-20261004-142341.xlsx") that wraps onto two lines, and "Share this .xlsx" appears below it a
beat later.
**Evidence.** a09 and a10 (ui/ReportScreen.kt:138-142, ui/SectionsScreen.kt:517-521). The same line reported success
while adb shell ls showed both roster-BSCE-4B.xlsx and roster-BSCE-4B (1).xlsx in that folder, so it also hides which
file is new.
**Why it hurts.** The least designed moment of the loop, and the only readable confirmation arrives late.
**Fix.** Show the file name on one line ("roster-BSCE-4B.xlsx saved to Documents/NFC Attendance") with a small accent
check, and put Open / Share side by side under the export button.

### 9. Student removal is silent and final, with cramped targets
**Looks like.** The trash glyph deletes a student on the first tap - no confirmation, no undo - while "Delete section"
demands two taps; the pencil and trash share one weight, sit flush right (4 dp inset) and are 24 dp apart.
**Evidence.** ui/SectionsScreen.kt:488-490 (state.removeStudent(student.uid) on click) vs :407-425 (two-step confirm,
a11); :469 (4 dp end padding); a03, where uiautomator reports Edit at x 796-859 px and Remove at 922-985 px (63 px =
24 dp glyphs, 24 dp gap).
**Why it hurts.** Both actions look equally important, so a destructive tap feels as casual as an edit, and one
mis-tap silently removes the only record mapping that UID to a name.
**Fix.** Make removal recoverable: an inline "Remove <name>?" row reusing the existing confirm pattern, or delete with
an undo snackbar; give the trash lower emphasis and 16 dp clearance from the pencil.

### 10. The session bar repeats the screen and hides End
**Looks like.** While a session runs the bar restates what is above it - the same "Registering into BSCE-4B - tap a
student ID." sentence or the same present/absent/unmatched counts - and its action is a three-letter "End" / "Open"
button inside a strip that also navigates when tapped.
**Evidence.** a05 (sentence at y=1151 in the Live card and again at y=2024 in the bar); a07 (counts in the THIS
SESSION tiles and again in the bar); ui/App.kt:138-198 (strip clickable at :158, three-letter label at :192-194).
**Why it hurts.** Duplicate information takes prime space, and the strip and the button sit adjacent while the
destructive action carries the smallest label in the app.
**Fix.** One line in the bar - section name plus the single figure that matters on the current tab - with an explicit
"End session" pill (48 dp) on the right and no click handler on the strip.

## Fix these three first
1. **Outline contrast (rank 1)** - one token repairs five controls plus the switch, and it beats the rest because
   every other fix is judged through a UI whose buttons cannot be seen.
2. **First-run path (rank 2)** - the only weakness that decides what a first-time user thinks the app is; the
   mid-loop items assume the user already got past this screen.
3. **Export placement (rank 4)** - a reorder with no scrolling maths, removing the last friction in the loop.

## Already working
- The palette and single accent are consistent and pass text contrast (secondary #B0B0B0 8.19:1 on #181818, primary
  #F5F5F5 16.29:1, accent 8.22:1 on the background); only the outline token fails.
- The NavigationBar with its green indicator pill and real vector icons improves clearly on the old 2x2 grid, and
  roster rows have a readable two-line hierarchy (name bodyLarge over UID bodySmall).
- Feedback is present and honest: haptics on card read and save, animated counters, and a plain "No NFC adapter on
  this device" instead of a silent dead end.
