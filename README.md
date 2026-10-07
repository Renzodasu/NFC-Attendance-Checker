# Presencia — NFC Attendance Checker

<img src="docs/screenshots/icon.png" width="140" alt="Presencia NFC: a tilted playing card with a tick and the near-field signal">

**Tap your ID. Be Present.**

An offline Android app for taking class attendance by tapping student ID cards.
Built for a class beadle: one roster per class, tap each card once, export the
session as an Excel workbook. The app ships as **Presencia NFC**; the repository
keeps its original name.

**No server. No network permission. No student numbers** - a student is a name
and a card UID, nothing else. When a card cannot be tapped, a QR fallback reads
the code already printed on the ID and asks the teacher to confirm which name it
belongs to; the code's own contents are never kept.

## What it does

- **Sections** - one card per class, each with its own subject and roster. The
  shelf is a centred carousel; the middle card is the selected class, and the
  magnifier beside *New section* searches the shelf by class name, subject or
  card face and brings a match to the middle.
- **Register** - tap an ID card, type the name, save. A card already on the
  roster updates its name instead of adding a second row.
- **Scan** - choose the reader: **NFC** or **QR**, never both at once. Tap each
  card once per session, or point the camera at the code on the ID when the card
  is broken or lost. A repeat read of the same student is refused with the time
  they were first recorded. A read more than 15 minutes after the session starts
  is still counted, but marked **late** (threshold selectable: 10/15/20/30). The
  session can be paused and left open for late arrivals, and the last card read
  stays in view at the top of the screen with an **Undo** beside it.
- **QR fallback** - the code on a school ID carries a student number, which this
  app never reads, keeps, shows or exports. A scan is used for one thing only: to
  look for a **name** already on the roster. The teacher confirms the name and
  verifies the card UID it belongs to, and the presence is recorded as **QR**.
  Every student's page also carries a code the app prints itself, holding just
  that student's card UID.
- **Report** - absent list first, then late, then present, then unmatched
  cards. Every row carries the date and time of the read.
- **Chart** - the Session report draws the same numbers it states: a **pie** of on
  time / late / absent, a **line** of how the room filled against the late mark, or
  a **bar** of how each presence was recorded (NFC, QR or by hand). Settings chooses
  the lens; the legend under every chart writes the figures out, so nothing depends
  on colour alone.
- **Export / Import** - write the report or the roster to `.xlsx` in the shared
  `Documents/Presencia` folder, and import a roster another phone
  exported. Rows merge by card UID; the subject travels with the file.

## Section styles

Settings carries a section style, applied to the section cards, the badges and
the tabs that work with a section.

| Plain (default) | Cards | Solids |
| --- | --- | --- |
| ![Plain: a drafting sheet](docs/screenshots/sections-plain.png) | ![Cards: a dark card with green symbols](docs/screenshots/sections-cards.png) | ![Solids: a wireframe polyhedron](docs/screenshots/sections-solids.png) |

- **Plain** - a civil engineering drawing sheet: drafting grid, truss mark,
  title block.
- **Cards** - a face from a complete 52-card deck, drawn dark and subtle with
  green symbols that glow when the class is the chosen one. The face can also
  be picked by hand when the class is created.
- **Solids** - a wireframe polyhedron (tetrahedron, cube, octahedron,
  dodecahedron, icosahedron, prism) as a quiet corner mark.

## Screens

| New section | Register |
| --- | --- |
| ![New section: name the class, write its subject, pick a card face](docs/screenshots/new-section.png) | ![Register: tap an ID card, type the name, save](docs/screenshots/register.png) |

| Scan | Report |
| --- | --- |
| ![Scan: start a session and tap each ID once](docs/screenshots/scan.png) | ![Report: the absent-first result, ready to export](docs/screenshots/report.png) |

Also included: a registered-students page for the full roster with per-student
standing, a full-screen student editor with the student's own QR code, Settings
and **How to use** as two icons in the header, light and dark themes, a
first-launch swipeable card tutorial, and scan feedback (haptics strength and a
beep on read).

## Privacy

Names and card UIDs live in this app's private storage only. The app has no
network permission, so nothing can be uploaded. The only data that leaves the
device is what you export yourself into 'Documents/Presencia'. The RA 10173
privacy paragraph is shown in the app.

The camera is used only by the QR fallback, and only to decode a code that is
already pointed at it: the frames are decoded in memory and dropped, never
photographed, stored or sent. Only **NFC** and **CAMERA** are declared - no
**INTERNET**. A student number in a scanned code is never recorded.

## Build

```bash
export JAVA_HOME="/path/to/Android/Studio/jbr"
./gradlew :app:assembleDebug          # APK
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (needs a device)
```

Kotlin, Jetpack Compose and Material 3. Gradle builds the `.xlsx` writer and
reader by hand over `java.util.zip` - no Apache POI, no third-party spreadsheet
library, no charting or image-loading dependency.

## Documentation

- [CHANGELOG.md](CHANGELOG.md) - what changed between releases
- [docs/UI-UX-AUDIT.md](docs/UI-UX-AUDIT.md) - the ten ranked usability findings
- [docs/UI-UX-CHECKLIST.md](docs/UI-UX-CHECKLIST.md) - what was fixed, with evidence
- [docs/NFC-Attendance-Checker-Guide.html](docs/NFC-Attendance-Checker-Guide.html) - illustrated guide
- [docs/GITHUB-PUBLISH.md](docs/GITHUB-PUBLISH.md) - release steps

## Licence

See [LICENSE](LICENSE).
