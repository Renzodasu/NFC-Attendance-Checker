# Presencia 0.0.6 — a friendlier face, and a teacher's own cues

**The playing cards are gone, every read now has a sound, and a legacy `.xls`
roster opens.** Still fully offline: **no server, no network permission, no
student numbers.**

---

## 🗂️ A class is a plate, not a card

The old shelf showed **one full-screen playing card per class**. The card face had
to be memorised, it competed with the learner's own ID card for the same word
("tap your card"), and it was missing from the two screens where identity
actually matters.

- **The class list is a grid of plates.** Every class is visible at once: the
  **name at reading size**, the subject as one grey line, the roster count at the
  bottom. Plates in a row size themselves to the taller one, so a two-line name
  never clips the count.
- **The mark is derived from the name**, never chosen: `Grade 3 - Rizal` reads
  **3R**, `Grade 3 - Sampaguita` reads **3S**, `BSCE-4B` reads **4B**, `Narra`
  reads **NA**. It cannot drift from its class, no class ever runs out, and there
  is no "this mark is already taken" error to explain.
- **Identity rides along.** The header pill, the Scan result header and the Report
  result card carry the same mark and name, so the class is named once and stays
  named on every screen.
- **There is one look now.** The Plain / Cards / Solids style switch is gone, with
  the deck art it styled.

## 🔔 The door tells you without looking

- **Every answer has a sound**, generated on the phone so nothing is bundled: a
  clean tick for on time, a **two-note rise** for late, a **low double knock** for
  a card already counted, a **buzz** for a card that is not on the roster, and one
  **long low tone** when there was nothing to record against.
- The tones stream as **media**, so a silenced ringer still plays them, and
  **Settings ▸ Sound on every read** turns them off.
- **The reader bar tells the truth.** If NFC is off it says **NFC is off** and
  offers one 48 dp button that opens the system's NFC switch (the quick-settings
  panel on Android 10+, the NFC settings page below that). A phone with no adapter
  says so. Pause beats everything, because a reader switched off on purpose is not
  a fault.

## 📄 A last crash, kept on the phone

An uncaught exception now writes **one stack trace** to private storage before the
process dies — the system's own dialog and kill are untouched. Settings shows a
**Last crash** card only when there is one, with a Clear action. "It closed by
itself" can now arrive with the words the system used.

## 📥 Legacy `.xls`, and a promise kept

- **The roster importer reads old Excel files too.** A real OLE2/BIFF8 reader
  (sectors and FAT, the shared-string table across CONTINUE records, LABEL,
  LABELSST, NUMBER, RK, MULRK, FORMULA and STRING cells), with hard caps on size,
  rows and columns. The import button accepts `.xlsx` **or** `.xls`.
- **The file still has to carry the app's own *Roster* sheet** — the layout a
  Presencia export writes. A DepEd **SF2 school form is not that format**, and the
  app now says so instead of promising a form it cannot read.
- **Learner files no longer travel to the cloud.** The app told teachers "nothing
  is uploaded" while Android auto-backup was copying the class list, the card UIDs
  and every session to Google Drive. Both backup policies now exclude the learner
  directory, the exported reports, **and** phone-to-phone transfer: the app's own
  export is the supported way a record moves.

## 🔤 Type that holds up across a room

Three **display styles** (34, 42, 52 sp) were added to the Spectral family, because
nothing above 34 sp existed and any larger heading silently fell back to Roboto.

---

**Install:** download `Presencia-NFC-0.0.6-debug.apk` below (debug-signed — fine
for classroom use, not for the Play Store).

**Upgrading from 0.0.4 or 0.0.5:** classes, rosters and saved sessions are kept.
The old `card` value in `sections.json` is simply ignored and dropped on the next
save; learner rows are matched by **card UID**, never by a face.
