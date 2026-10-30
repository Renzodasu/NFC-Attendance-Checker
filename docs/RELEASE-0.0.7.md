# Presencia 0.0.7 — the school form, in and out

**A DepEd SF2 school form now comes in as a class, and one month of it goes back
out as the same form.** Still fully offline: **no server, no network permission,
no student numbers.**

---

## 🏫 The form comes in

The class screen's import used to accept one thing: a file with the app's own
*Roster* sheet. It now also opens the form a teacher already has.

- **It reads the form's own sheet** (`school_form_2_ver2014.2.1.1`) and the
  header the school printed: **School ID**, **Name of School**, **School Year**,
  **Grade Level**, **Section**, and **Report for the Month of**.
- **The learners come in order, male then female**, from the numbered rows the
  form itself prints, with the **M/F** the form states and **no cards bound**.
- **It tells you what it skipped**: a name that appears twice, or a numbered row
  with no name, is listed by row — the import never silently invents a learner.
- **The class name is suggested** as `Grade 5 - NARRA` and can be edited
  before the import. Importing the same form again adds only the names that are
  missing.

## 🪪 The card comes later

A card is not in a school form, so it is bound the first time it is used.

- Read a card on **Register** and the panel lists the learners **waiting for a
  card** — *This card belongs to*. Tap the name and the card is bound.
- A card that is already somebody else's is **refused by name**, in whatever
  class holds it.
- An unbound learner shows as **N — no card yet** on the form, never as absent.

## 📄 The month goes back out

The **Report** tab carries a school-form card. Pick a month (only months with a
session are listed) and export:

- One column per **school day** — Monday to Friday, with the day number and the
  weekday letter the form prints.
- **MALE** and **FEMALE** blocks in the form's order, each learner's marks across
  the month, per-day totals, **ABSENT** and **PRESENT** counts, and **REMARKS**.
- **Enrolment**, **Days of Classes**, **Average Daily Attendance** and
  **Percentage of Attendance**, then the form's own legend.
- The marks are the app's, written plainly: **blank is present**, **x absent**,
  **L late**, **-** no session that day, **N no card yet**. A late arrival counts
  as present, exactly as the app's own report already treated it.
- The sheet is set up to print: landscape, fit to one page wide, so a whole month
  lands on a single sheet instead of breaking after the middle of the month.

## 🔒 Nothing leaves the phone

The form is written by the app's own `.xlsx` writer — no Apache POI, no
third-party spreadsheet library — into the same shared **Documents/Presencia**
folder as every other export, and it only contains what this phone recorded.
There is still **no INTERNET permission**: the school's form is a file the teacher
picks and a file the teacher shares.

## Install

Download `Presencia-NFC-0.0.7-debug.apk` from this release and open it. Android
will ask to allow installs from this source the first time.

Upgrading from 0.0.6 (or 0.0.4) keeps every class, roster, session and setting:
the class file is version 4 now, and older files load with the new fields empty.
