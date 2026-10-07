# Changelog

All notable changes to NFC Attendance Checker. This project follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses date-based
versions in the form `0.0.N`.

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
