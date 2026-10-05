# Changelog

All notable changes to NFC Attendance Checker. This project follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses date-based
versions in the form `0.0.N`.

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
