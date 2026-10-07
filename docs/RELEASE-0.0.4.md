# Presencia 0.0.4 — a session you cannot lose, and a QR fallback

**Two blockers fixed, a QR fallback added, and the report now has charts.**
Still fully offline: **no server, no network permission, no student numbers.**

---

## 🛡️ A running session survives anything

- The app's state is a **ViewModel** now, so **rotating the phone keeps the session** instead of throwing it away.
- A session is **written to disk on every tap**, so being killed in the background no longer costs a class its
  attendance. A session that was still running comes back on the next launch.
- **The late window is snapshotted when a session starts.** Changing *Late after* in Settings used to re-classify
  sessions that had already happened — and quietly change a report that may already have been exported.
- **Every write is atomic** (temporary file, then rename), so a process killed mid-write can no longer leave a
  half-written class list.
- **A damaged class file is reported, not swallowed.** The app says so, refuses to overwrite it, and offers
  *Try again* or *Start fresh* — which moves the unreadable file aside rather than deleting it.

## 📷 QR fallback for a broken card reader

- The Scan tab opens with a reader switch — **NFC** or **QR** — and only one is ever live.
- **The code on a school ID carries a student number, and this app never reads, keeps, shows or exports one.**
  A scan is used for one thing: to look for a **name** already on the roster. The teacher confirms the name and
  **verifies the card UID** it belongs to before anything is recorded.
- **Both readers obey the same rules.** A card tap, a code that carries a card UID, and a code resolved by hand all
  pass through one function, so registered, unknown, duplicate, session, pause and present-or-late cannot differ.
- **Every row records its method** — `NFC`, `QR` or `Manual` — in a **Method** column in the exported sheet.
- Every student's page carries a code the app prints itself, holding just their card UID.
- **Two dependencies, explained before they were added:** `com.google.zxing:core` (decoder *and* encoder, pure
  Java) and the Jetpack `androidx.camera` family. **No Google Play Services. No model download.** The manifest
  gains **CAMERA** (declared optional) and **still has no INTERNET permission.**

## 📊 Charts on the session report

A chart card now sits between the result tiles and the export button, and **Settings ▸ Chart** chooses the lens:

| Lens | What it draws |
| --- | --- |
| **Pie** | On time / late / absent over the roster, with the roster size in the hole |
| **Line** | Cumulative students read against minutes, with the session's own late mark drawn in |
| **Bar** | How each presence was recorded: NFC, QR or by hand |

- **Unmatched taps are deliberately not a pie slice** — they are cards that matched nobody, so a fourth slice would
  sum past the roster and contradict the sheet.
- **Late is a hatch as well as an amber tone**, because green and amber are the pair a colour-blind reader is most
  likely to confuse; every figure is also written out in a legend beneath the chart.
- **No chart library:** the three charts are Compose Canvas, like the app's card art. **+44 KB**, not a dependency.

## 🧹 Tidier screens

- **The drawer is gone.** It listed the same three destinations as the bottom bar; **Settings** and **How to use**
  are two icons in the header now.
- **The Scan tab is about scanning** — the class card that opened it is gone, the class is named once, the duplicate
  rows are gone, and **Pause / End session** are above the fold again.
- **A damaged-file recovery card**, an **Undo** on the last read, and **Mark present** for the student whose card is
  broken or lost — recorded as `Manual` so the sheet never claims a card was scanned.
- *Manage* is now **Edit class**; Settings moved its cosmetic and calibration controls under **Advanced**.
- Dead UI removed: the card chip row no screen ever showed, two unused composables, an unused drawable, and the
  duplicate *Close* / *Back to the list* buttons.

## ✅ Verification

- **101 unit tests, 0 failures.**
- Verified on **emulator-5554** through the Android Studio MCP: a session surviving **rotation** and a **process
  kill**; a **truncated class file** producing the recovery card with the file **byte-identical afterwards**; the
  **NFC | QR switch**, camera permission and live preview; and the generated QR code **decoded back with ZXing** to
  exactly `presencia:04A1B2C3`.
- **Known limit:** the end-to-end *camera reads a printed code → confirm → record* step was not run — the emulator's
  camera renders a synthetic scene with no QR in it, and stubbing one was ruled out. It needs a real phone and a
  real ID; everything either side of it is covered by the tests and the captures above.

---

**Install:** download `Presencia-NFC-0.0.4-debug.apk` below (debug-signed — fine for classroom use, not for the
Play Store).