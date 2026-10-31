# Changelog

All notable changes to NFC Attendance Checker. This project follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses date-based
versions in the form `0.0.N`.

## [0.0.10] - 2026-10-31

### Changed

- **Each tab answers one question.** *Scan* states the session in four numbers on a
  single line - present, late, absent, unmatched - and keeps one control (Start, or
  Pause and End) pinned above the tab bar where scrolling cannot reach it. The
  last-read banner now appears only after a card has actually been read, and the
  reader sentence below it no longer repeats the same alarm a second time.
- **Report leads with the people to act on.** A header card carries the class name,
  the day and clock, and one **Export** pill; the four numbers share a line beneath
  it; the absent list follows, one line per learner with a green **Mark**; the chart
  card stays closed until it is tapped, and **Late** and **Present** open on demand.
- **The tab bar is a tab bar.** The selected tab is a green icon and label with no
  pill behind it, and a running session rides above it as a one-line player naming
  the class, its count and **End** / **Open**.
- **The reader has four short sentences** - no NFC, NFC off, paused, ready - sized to
  sit beside the dot instead of wrapping under it.

### Fixed

- **A card marked by hand no longer shows the antenna warning.** The last-read banner
  outranked the mark and said *No NFC on this phone* over a tap the teacher had just
  recorded by hand; it now names the learner and the method.
- **The unmatched-tap row keeps its time on one line.** *8:22:00 AM* wrapped in the
  Needs attention card and split the row in two.
- **An unmatched tap gets a hollow chip** in the chart legend: it is counted on the
  card, but it is not one of the class and must not look like an absent slice.

All 149 unit tests pass, including the new short-reader-line and day-line checks.

## [0.0.9] - 2026-10-31

### Added

- **A form-imported class can be armed without typing.** Register's *This card
  belongs to* panel now lists **every** learner still waiting for a card, with a
  search box, and binds the tapped card to the exact row the teacher points at.
  The old panel stopped at the first twelve names, so learners 13-30 could not be
  bound at all without typing.
- **A card bound mid-session works in that session.** The session's roster is a
  snapshot taken at Start; binding a card now fills the UID into that snapshot
  too, so the teacher does not have to end the session to arm a late arrival.
- **The Report tab opens on the class's latest session.** After a stop or a
  relaunch, the last session on disk is shown for the selected class, so the
  absent list a teacher reads after closing the app is still today's - and
  *Export this day* still writes that session's sheet.
- **A learner with no card can be marked present by hand.** *Mark present* on an
  absent row records a presence for that one learner - on a closed session as
  well - and writes the correction back with `live = false`.

### Changed

- **The tabs are Scan, Report, Sections, and Scan is the start screen.** The order
  matches the order of the school day rather than the order of setup.
- **The QR fallback is gone.** The camera scanner, the reader switch, the
  generated student QR code, the `CAMERA` permission and the camera/zxing
  dependencies were removed, and the student page no longer draws a code.
  `AttendanceMethod.QR` is kept as a value that only reads back, so sessions and
  exports recorded before this release still parse and still say how they were
  taken.

### Fixed

- **Opening a learner on a form-imported class no longer crashes.** Imported
  learners all had a blank UID, and the roster and report lists used the UID as
  their Compose key; two blank keys threw and killed the process. Lists are keyed
  by row position now.
- **Edits land on the child the teacher chose when two learners share a name.**
  Rename, remove, bind and hand-mark all address the roster row, and a presence
  recorded by hand for a card-less learner carries a key derived from the name and
  position instead of an empty string.
- **The student page no longer stutters.** The QR canvas drew hundreds of
  rectangles inside a scrolling list; it went with the feature.
- **A report never borrows another class's session.** The open session is only
  used when it belongs to the class on screen.

## [0.0.8] - 2026-10-30

### Fixed

- **An SF2 import no longer loses a learner.** The form merges columns A and B for
  its *No.* column, and a copy re-saved by another program can keep a number in the
  right half of that merge. The first girl on a real Grade 5 form had her `1` in
  column B, so her row was skipped and the class arrived with 29 learners instead of
  30 — silently, because the row looked unnumbered rather than broken. The importer
  now reads the number from either half of the merged cell; the same form imports as
  14 male and 16 female learners. A regression test pins it (`Sf2FormTest`).

## [0.0.7] - 2026-10-30

### Added

- **A DepEd SF2 school form imports as a class.** The importer now recognises a
  form's own sheet (`school_form_2...`, or `SF2`), reads the header it prints
  (School ID, Name of School, School Year, Grade Level, Section, Report for the
  Month of) and the learner rows in the form's two sex blocks. The learners arrive
  with their **names** and **M/F**, with **no cards bound**, and the class keeps the
  form's header so the export can print the same school again.
- **Learners are matched the way the form is written.** A row named twice is
  reported, not added twice; a numbered row with no name is reported by row number;
  a class name is suggested as `Grade 5 - NARRA` and can be edited before
  importing. Importing the same form again adds only the names the class does not
  already have.
- **Cards are bound later, on Register.** When a class came from a school form and
  a card is read, the panel lists the learners who are **waiting for a card**
  ("This card belongs to"): tap the name, and the card is bound to that learner. A
  card already registered to someone in any class is refused by name.
- **The month prints back as SF2.** The Report tab gained a school-form card with a
  month picker over the months that hold a session. It writes the official layout:
  title and subtitle, the school fields, one column per **school day** (Mon-Fri),
  separate **MALE** and **FEMALE** blocks, each learner's day marks, per-day totals,
  ABSENT and PRESENT columns, REMARKS, enrolment M/F/total, days of classes,
  average daily attendance and percentage. **Blank is present, x is absent, L is
  late, - is no session that day, N is no card yet.**
- **The writer keeps the form's shape.** `XlsxWriter` gained column widths, row
  heights, merged cells and eight cell styles (title, bold, boxed, boxed bold,
  boxed centred, footnote), so an exported form looks like a form instead of a
  plain grid of text.
- **Session history is readable.** `Store.sessions()` returns every stored
  session, so a month can be built after the session that recorded it has ended.
- **A styled-sheet test** proves widths, merges and a boxed blank cell survive the
  writer, and that the result still opens in the app's own reader.

### Changed

- **A student can exist before their card does.** `Student.uid` may now be blank;
  such a learner is "no card yet", is listed for binding, is counted as neither
  present nor absent on the form, and can never be matched by a stray blank UID.
  Sessions keep the roster they were started with, so an old session is unchanged.
- **Storage schema 4.** Sections carry `sex` per student and an optional `sf2`
  header object. Old files load with the new fields empty, and a legacy `card`
  key is still ignored rather than rejected.
- **Register refuses a blank card.** Registering a name without a whole UID is now
  an error ("That card did not read as a whole UID.") instead of writing a row no
  tap can ever match.

### Fixed

- **The printed form fits the page.** Labels moved out of the five-character
  *No.* column so the school fields are no longer cut off, the NAME heading is
  short enough to read, and every worksheet now carries a landscape fit-to-page
  setup, so a month prints on one page instead of spilling after day 15.

## [0.0.6] - 2026-10-30

### Added

- **A friendly face: class plates instead of playing cards.** The class list is
  now a grid of plates - the class **name** at reading size, the **subject** as
  one grey line, the roster count at the bottom, and a two-letter **mark derived
  from the name** (`Grade 3 - Rizal` reads `3R`, `BSCE-4B` reads `4B`). The
  mark is computed, never chosen, so it cannot drift from its class and no class
  ever runs out of faces. Plates in a row size themselves to the taller one, so a
  two-line name grows both instead of clipping the count.
- **Every read has a sound.** Five tones are generated on the phone, so nothing
  has to be bundled or licensed: a clean tick for on time, a two-note rise for
  late, a low double knock for a card already counted, a buzz for a card that is
  not on the roster, and one long tone when there was nothing to record against
  (no session, paused, invalid). They stream as media, so a silenced ringer still
  plays them, and Settings has a **Sound on every read** switch.
- **The reader bar tells the truth.** A new pure rule decides what the top of the
  Scan and Register pages says: if NFC is off the bar reads **NFC is off**, if the
  phone has no adapter it says so, and a **paused** session is the only place that
  says paused. Pause wins over everything - a reader switched off on purpose is
  not a fault.
- **Turn on NFC from the app.** The system offers no API to switch NFC on, so the
  button opens the shortest settings surface that answers: the quick-settings
  panel on Android 10+, the NFC settings page on older phones, resolved through
  the package manager and never crashing a page when a ROM has neither.
- **A last crash, written down.** An uncaught exception now saves one stack trace
  to the app's own private storage before the process dies (the system's own
  dialog and kill are untouched). Settings shows the **Last crash** card only when
  there is one, with the text and a Clear action - so "it closed by itself" can
  arrive with the words the system actually used.
- **Legacy `.xls` import.** The roster importer now reads real OLE2/BIFF8
  workbooks as well as `.xlsx`: sectors and FAT, the shared-string table across
  CONTINUE records, LABEL, LABELSST, NUMBER, RK, MULRK, FORMULA and STRING cells,
  whole numbers kept whole (no phantom decimal tail), and hard caps on size, rows
  and columns. The class screen's import accepts `.xlsx` or `.xls`.
- **Display type.** Three display styles (34, 42, 52 sp) were added to the
  Spectral family, because nothing above 34 sp existed and any larger heading
  silently fell back to Roboto.
- **An import that cannot freeze the screen.** The picked file is now read and
  parsed off the main thread, behind a **Reading the file...** label, with the
  size checked before the first byte is buffered and again while reading. A
  picked video or a 20 MB workbook can no longer lock the app or exhaust memory.
- **A wrong file gets a plain answer.** A password-protected workbook, an OLE
  file that is not a spreadsheet, and a format the app cannot open now say what
  happened instead of showing the raw exception text.

### Changed

- **The class list is a grid, not a carousel.** The old shelf showed one
  full-screen card per swipe and required a face to be memorised; the grid shows
  every class at once, at a glance, while standing in front of forty students.
  The chosen class is the only one with a green edge and a green mark.
- **Search finds by name and subject.** The old search also matched a card face,
  which was the one field a teacher never remembers.
- **The guide is six plain steps.** Numbered steps with a plain title and body,
  a **Step n of 6** label and a Next/Back footer, replacing the deck of cards with
  suits in the corner. Finishing it lands on **Start using the app**.
- **New class** no longer asks for a suit and rank: a class is its name and its
  subject, and the import note now says `.xlsx` or `.xls`.
- **Screens show the class itself.** The header pill, the Scan result header and
  the Report result card carry the class mark and name, so identity no longer
  depends on remembering a symbol.
- **The report leads with names.** The absent list now sits directly under the
  class identity, before the Result tiles, the chart and Export - the names are
  what a teacher opens the screen to read. **Absent** is the only tile that takes
  the amber warning tone, and only while there is someone to see; green stays off
  bad news.
- **Register and Edit stay put.** The pair is pinned above the session bar instead
  of sitting at the end of the class grid, where it scrolled away with forty
  classes on the list.
- **The chosen class is marked twice.** A check sits beside its roster count, so
  selection is readable without relying on the green edge.
- **Titles are said once.** The New class page and the guide no longer repeat the
  header's own title.

### Removed

- The playing-card deck: `PlayingCards.kt`, `FacePicker.kt`, `CardIndex.kt`,
  `SectionFace.kt` and `VisualStyle.kt`, with their tests (`VisualStyleTest.kt`
  and the card-face case in `SectionsTest.kt`).
- The **Section style** setting, and with it Plain / Cards / Solids. There is one
  look now, and it is the same on every screen.
- The `card` field from what the app writes: existing `sections.json` files still
  read, the value is simply ignored and dropped on the next save. Learner rows
  are untouched, because binding is by card UID and never by face.

### Fixed

- **Learner files no longer travel to the cloud.** The app promised "nothing is
  uploaded" while Android's auto-backup was copying the class list, the UIDs and
  every session to Google Drive. Both backup policies now exclude the learner
  directory, the exported reports, and device-to-phone transfer: the app's own
  export is the supported way a record moves.
- **A mark like `3A` no longer paints `33`.** The initial was taken from the first
  alphanumeric character of the noun, so a noun that started with a digit
  (`BSCE-3A`) repeated the grade. The first **letter** is preferred now, with the
  digit kept only for a noun that has no letters at all.
- **A two-line class name no longer clips the roster count** on its plate.
- **A damaged session file can no longer erase the history.** Every tap rewrites
  `sessions.json`; when that file could not be parsed the old code treated it as
  empty and saved the current session over every past one. Writes now fail closed
  - the damaged file is kept, the screen stays as it was, and nothing is
  overwritten.
- **Two `.xls` reader defects** were caught by the new tests before release:
  wrong BOUNDSHEET field offsets (every workbook reported no worksheet) and a
  shared-string read that could straddle a record edge on a wide character.
- **Names with accents keep their apostrophes** in `.xls` files: compressed
  strings were decoded one byte at a time, so `D'Angelo` arrived with a control
  character where the right single quote should be. The reader now uses the
  Windows-1252 table.
- **A big mark no longer wraps** inside its badge: `12M` stays on one line.

## [0.0.4] - 2026-10-07

### Added

- **A QR fallback for when the card reader is not there.** The Scan tab now opens
  with a reader switch - **NFC** or **QR** - and only ever one of them is live:
  choosing QR turns reader mode off completely. QR mode shows a camera preview and
  decodes the code with **ZXing**, entirely on the phone.
  - **The code on a school ID does not identify a student**, and this app does not
    pretend otherwise. It carries a student number, which is never read, kept,
    shown or exported. The code is used for exactly one thing: to look for a
    **name** that is already on the roster. The teacher confirms the name and then
    verifies the card UID it belongs to before anything is recorded, so a scan
    resolves to a person the same way a tap does - only by hand.
  - **Attendance is written by the same rules either way.** A card tap, a code that
    carries a card UID, and a code the teacher had to resolve by name all pass
    through one function (`AttendanceProcessor`), so registered, unknown, duplicate,
    session, pause, timestamp and present-or-late cannot differ between readers.
  - **Every row now records the method**: NFC, QR or Manual, in a `Method` column in
    the exported sheet, with a hand-recorded and QR-recorded count on the Absent
    sheet. A report can always say how a student's presence was established.
  - **A code the app prints itself** is on every student's page: it carries that
    student's card UID and nothing else, so it resolves without asking anybody.

- **Analytics on the session report.** A chart card now sits between the result
  tiles and the export button, and **Settings > Chart** chooses the lens: **Pie**,
  **Line** or **Bar**. Each answers a different question rather than redrawing the
  same number:
  - **Pie** - on time, late and absent as a ring over the roster. Unmatched taps are
    deliberately **not** a slice: they are cards that matched nobody, so they are
    not students, and putting them in would make the slices sum past the roster and
    contradict the sheet. Late is drawn with a hatch as well as a tone, because green
    and amber are the pair a colour-blind reader is most likely to confuse.
  - **Line** - cumulative students read against minutes since the start, with the
    session's late boundary drawn in. One student at minute 0 and three at minute 31
    is the shape of a class, and the chart says so.
  - **Bar** - how each presence was actually recorded: NFC, QR or by hand, with the
    unmatched taps set apart and captioned.
  - Every kind writes its figures out in a legend underneath, the whole card
    collapses to one summary line, and a kind with nothing honest to draw (a line
    needs two readings) steps aside for the pie with a note saying why.
  - **No chart library.** The three charts are drawn with the same Compose Canvas
    the app already uses for its card art, so the APK grew by ~44 KB rather than a
    dependency.

- **A session survives anything.** The app's state is a ViewModel, so rotating the
  phone keeps a running session instead of rebuilding the state and throwing it
  away - and a session is written to disk on every tap, so being killed in the
  background no longer costs a class its attendance. A session that was still
  running comes back on the next launch, announced as "restored with N tap(s)".
- **Fix a wrong tap.** *Undo* on the Scan tab's new top line, and *Mark present*
  on every absent row of the report - for the student whose card is broken, lost
  or was never made. A hand-made presence is tagged as such: the exported sheet
  carries a **Recorded** column reading "card" or "marked by hand", and the
  Absent sheet counts them.
- **The last card read, always in sight.** The Scan tab leads with a line that
  never scrolls away: who was just recorded (or "NOT ON ROSTER"), how many taps
  the session holds, and Undo.
- **A damaged class list is reported, not swallowed.** If the class file cannot
  be read, the Sections tab says so and the app refuses to write - the file is
  left exactly as it was, with *Try again* and *Start fresh*, the latter moving
  the unreadable file aside under a new name rather than deleting it.

### Changed

- **A second permission, and only that one.** The manifest adds `CAMERA` (with the
  camera declared optional, so a tablet without one still installs and still taps
  cards). There is still no `INTERNET` permission: the decoder runs on the device,
  the frames are decoded in memory and dropped, and nothing is photographed, stored
  or sent. Two dependencies were added for this: `com.google.zxing:core` (decoder
  and encoder, Apache-2.0, pure Java) and the Jetpack `androidx.camera` family for
  the preview and frames - no Google Play Services, no model download. The APK grew
  from 13.0 MB to 15.3 MB.
- **A repeat read is now refused rather than appended.** Scanning or tapping the
  same student twice says "Already recorded: <name> at <time>", instead of adding a
  row that is later de-duplicated. The rule is the same for both readers.

- **Every write is atomic.** Files go to a temporary name and are renamed into
  place, so a process killed mid-write leaves the previous file intact instead
  of a half-written one. Both stores carry a schema version; older files still
  read.
- **The late window is snapshotted when a session starts.** Changing "Late after"
  in Settings used to re-classify sessions that had already happened, and quietly
  change a report that may already have been exported. It now applies to the
  sessions you start from here on.
- **The drawer is gone.** The sidebar listed the same three destinations as the
  bottom bar, so Settings and How to use moved into the header as two icons, one
  tap from anywhere, and nothing is listed twice.
- **The Scan tab is about scanning.** The class card that opened the page is no
  longer drawn above the reader, the class is named once instead of three times,
  and the duplicate "Taps recorded", "Late after" and "Reader" rows are gone.
  Pause and End session are above the fold again, not under the bottom bar.
- **The report no longer wears another class's face.** The card drawn on the
  Report page came from the shelf selection, so it could show a different class
  from the session it was labelling. It is gone from Report, Register and Scan,
  and stays on the class's own page, where the face is what you edit.
- **"Manage" is now "Edit class"**, and the page it opens says so.
- **Settings, shorter.** Section style and UID byte order moved under one
  "Advanced" heading at the end; four explainer paragraphs and the "New here?"
  card are gone; "Your data" merged into "About".

### Removed

- Dead UI: the card chip row no screen ever showed (and the 26 dp of card face
  reserved for it), `CardActionRow`, `SectionBadge`, an unused accessor, the
  unused `ic_delete` drawable, and the duplicate "Close" / "Back to the list"
  buttons at the bottom of three pages - the header's back arrow was always there.

### Fixed

- Rotating the phone, or the process being killed, during a session no longer
  discards the session.
- A truncated or damaged class file no longer reads as "no classes", and is no
  longer overwritten by the first edit made afterwards.
- Moving the "Late after" setting no longer rewrites sessions that already ran.
- The Report page no longer shows one class's card above another class's report.
- The Scan tab's Start/Pause/End controls are no longer pushed under the bottom
  bar by the class card that used to sit above them.

## [0.0.3] - 2026-10-05

### Added

- **A name of its own.** The app is now **Presencia: NFC Attendance Checker**
  (`Presencia NFC` inside the app), and exports go to the shared
  `Documents/Presencia` folder. The rename covers the launcher label, the
  sidebar title, the Settings rows, the export path and the APK file name.
- **A new mark.** The launcher icon is a tilted playing card carrying a tick,
  with the near-field signal stepping off its top-right corner and
  **ATTENDANCE** across the bottom. The vector is drawn by
  `app/build/nfc-artifacts/make_launcher_icons.py`, which also renders the five
  legacy bitmap densities, so the adaptive icon and the bitmaps cannot drift.
- **Search on the Sections tab** - the magnifier beside *New section* opens a
  bar that filters classes by name, subject or card face. A tap on a result
  brings that class to the middle of the shelf and makes it the selected one.
- **An NFC status light** - the reader line carries a dot: green with a soft
  glow while the reader is on, amber while it is off, and dark when the device
  has no reader at all. The sentence next to it and the dot read from one piece
  of state, so they can never disagree.
- **Card faces by name** - a face picker that lists the 52 cards in words, for
  choosing a class's face by hand.
- **First-launch tutorial rewritten** as a swipeable, tilted card deck that can
  be skipped, and never shown twice.

### Changed

- **The section cards were redrawn** for both themes: the light-theme wash over
  every card is gone, the suit watermark now scales with the card instead of
  colliding with the date, and the Solids caption no longer clips mid-date.
- **Type and touch targets are larger** throughout, and every tab, card and
  button animates in and out instead of snapping.
- Light mode was rebuilt around its own palette (a `#F1F2F4` page, `#FFFFFF`
  raised surfaces, `#E9EAEC` high surfaces, `#D9DBDF` hairlines) instead of
  reusing the dark theme's greys.
- The Scan tab leads with the selected class drawn full size - the same card as
  the shelf - above the reader state.

### Fixed

- The glow behind the selected class's card was clipped at the card's own
  bounds, so it read as a hard band instead of a pool of light.
- The class name and details on the dark card style were painted in the old
  ivory-era ink and were nearly invisible.
- The **Plain** section style did not survive a restart: choosing it stored
  `plain`, but the reader only understood `cards` and `solids`, so the app came
  back wearing card faces. Every style now reads back as itself, and a unit test
  holds that door shut.

## [0.0.2] - 2026-10-04

### Added

- **Section subject.** Every class carries what it is about ("Surveying",
  "Hydraulics"), shown on its card, editable under Manage, written into the
  exported roster as a `Subject` row and read back on import.
- **New section page.** A small button beside the section list opens a
  full-screen page for naming a class, choosing its subject, importing an
  existing roster from `.xlsx`, and previewing the merge before committing.
- **Card faces.** Each class is dealt a face from a complete 52-card deck
  (spades, hearts, diamonds, clubs, A-K), unique while the deck lasts, stored
  with the class so it never changes. The face can also be chosen by hand when
  the class is created, and the app refuses a face another class is holding.
- **Section styles**, chosen in Settings and applied everywhere:
  - **Plain** (default) - a civil engineering drawing sheet: drafting grid,
    truss mark, title block.
  - **Cards** - the playing card face, drawn dark and subtle with green
    symbols that glow when the class is the chosen one.
  - **Solids** - a wireframe polyhedron (tetrahedron, cube, octahedron,
    dodecahedron, icosahedron, prism) as a quiet corner mark.
- **Scan feedback**: vibration strength (Off / Light / Normal / Strong) and a
  beep on card read, both from Settings. No extra permission is involved.
- **Registered students page** - the full roster for the selected class on its
  own screen, with per-student late/absent standing, a filter when the list is
  long, editing and removal.
- **Student editor page** - tap a student to open a full screen with the card
  UID, a name field, and a two-step "remove from roster".
- **Sidebar** with Settings and a tutorial, plus **light mode** alongside the
  dark theme (`System / Light / Dark`).
- **First-launch tutorial** - a swipeable, tilted card deck shown once, never
  again.
- **Late rule and pause**: a card read more than 15 minutes after the session
  starts is counted but marked late (threshold selectable: 10/15/20/30), and a
  paused session stays open for late arrivals while it stops recording.
- **Date and time on every attendance**, in the app and in the workbook.
- **Section card on the working tabs**: the chosen class is drawn, centred, at
  full size on Register, Scan and Report, and flies in from its place on the
  shelf.

### Changed

- The section shelf is a centred carousel; the middle card is the selected one,
  and a tap brings any card to the centre.
- Adding a class moved off the shelf entirely: the long "New section"
  rectangle and the tile inside the carousel are gone.
- The header keeps a single pill with the selected class name.
- Report sheets are ordered Absent, Late, Present, Unmatched.

### Fixed

- The "New section" tile sat 20 dp left of the centre line and could not match a
  card's size; it now fills its page slot exactly.
- The class name and details on the dark card style were painted in the old
  ivory-era ink and were nearly invisible.
- The vibration strength buttons truncated their labels; they are laid out two
  per row.
- Test expectations updated for the roster sheet's new `Subject` row.

## [0.0.1] - 2026-09-30

### Added

- Initial release: three tabs (Sections, Register, Scan, Report), one class
  roster per section, card registration by UID and name only, attendance
  scanning with 3-second duplicate suppression, and a session report.
- `.xlsx` export to the shared `Documents/NFC Attendance` folder and
  `.xlsx` roster import that merges by UID.
- Offline by design: no server, no network permission, no student numbers
  stored anywhere.
- The RA 10173 privacy paragraph, a dark theme, and the illustrated guide.
