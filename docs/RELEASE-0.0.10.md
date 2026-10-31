# Presencia 0.0.10 — one question per tab

**A calmer screen for a teacher standing at a door.** Still fully offline: **no
server, no network permission, no student numbers.**

---

## 🎯 What 0.0.10 changes

The app had the right data and too much of it on screen at once. This release is a
layout release: nothing about the schema, the roster, the session or the export
changes, and every class, card and stored session survives the upgrade.

- **Scan states the session in four numbers** - present, late, absent, unmatched - on
  one line, and keeps exactly one control pinned above the tab bar. Start, Pause and
  End can no longer be scrolled away from, and the four-line paragraph that used to
  explain the session is gone.
- **The last-read banner waits for a read.** It appears when a card has been read or
  a tap needs attention, and not before, so an idle Scan is quiet.
- **Report opens on the absent list.** The header card carries the class, the day and
  the clock, and one Export pill; the numbers share a line under it; the absent
  learners follow, one line each with a green **Mark**; the chart card is closed until
  it is tapped and the **Late** / **Present** lists open on demand.
- **The tab bar is a tab bar.** One green icon and label, no pill behind it. A running
  session rides above it as a single line: the class, its count, and **End** or
  **Open**.
- **The reader speaks in four short sentences** - ready, no NFC, NFC off, paused -
  each short enough to sit beside the dot.

## 🔧 Also fixed

- A learner marked by hand no longer shows the antenna warning in the banner; the
  banner names the learner and says it was recorded by hand.
- The time in the Needs attention card stays on one line instead of splitting the row.
- An unmatched tap is drawn with a **hollow** chip in the chart legend: counted, but
  not a slice of the class.

## Install

Download `Presencia-NFC-0.0.10-debug.apk` from this release and open it. Android will
ask to allow installs from this source the first time.

Upgrading from 0.0.9 keeps every class, roster, session and setting: the class file is
still version 4 and the session file is unchanged.
