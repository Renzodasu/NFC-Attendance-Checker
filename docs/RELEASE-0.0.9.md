# Presencia 0.0.9 — bind a card without typing

**The imported roster is now the register.** Still fully offline: **no server, no
network permission, no student numbers.**

---

## 🐞 What went wrong

Three things were in the way of a teacher who had just imported the school form:

- Imported learners come in with **no card**, and the Register page only listed the
  first twelve of them. A class of thirty could not be bound without typing names.
- Tapping an imported learner on **Sections → Registered students** crashed the app,
  because every learner without a card had the same blank identifier and the list
  could not tell the rows apart. On a hand-built class it opened, but the page drew
  the QR code on every scroll and stuttered.
- The **Report** showed the session only while it was running. After the app was
  closed and reopened, the day's attendance was on disk but the Report said
  *"No session yet"*.

## ✅ What 0.0.9 does

- **Register works without typing.** The card panel lists the whole waiting roster
  with a *Find a learner* search box. Tap a card on the reader, then tap the name —
  the card is bound to that learner, even in the middle of a session.
- **No crash on imported learners.** Rows are addressed by position, not by the card
  identifier, so blank-card learners can be opened, renamed and removed. Two learners
  with the same name are handled as two separate rows.
- **The Report remembers the day.** It shows the session that is running, or the
  latest one stored for that class after a relaunch, with the same absent-first list
  and the same **Mark present** button. A hand correction also works for a learner
  who has no card yet, and it can still be made after the session has ended.
- **QR is gone.** No QR generator, no camera scanner, no camera permission, no
  camera dependency. Older sessions that recorded a tap as QR still open and still
  export; the value is simply read-only now.
- **Tabs are in working order:** **Scan → Report → Sections**, and the app opens on
  Scan.

## Install

Download `Presencia-NFC-0.0.9-debug.apk` from this release and open it. Android
will ask to allow installs from this source the first time.

Upgrading from 0.0.8 keeps every class, roster, session and setting: the class file
is still version 4, and a session saved with a QR tap still loads.

> The camera permission disappears on upgrade — nothing else in the app used it.
