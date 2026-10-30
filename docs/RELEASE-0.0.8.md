# Presencia 0.0.8 — every learner on the form

**A one-line fix to the school-form import.** Still fully offline: **no server, no
network permission, no student numbers.**

---

## 🐞 What went wrong

The form merges columns **A** and **B** for its *No.* column. A copy of the form
re-saved by another program can keep a number in the **right half** of that merge.
The first girl on a real Grade 5 form had her `1` in column B, so the importer saw
an unnumbered row and skipped her: the class came in with **29 learners instead of
30**, and nothing was reported as skipped, because the row did not look broken.

## ✅ What 0.0.8 does

- The importer reads the learner number from **either half** of the merged cell.
- The same form now imports as **14 male and 16 female learners, 30 in all**.
- A regression test pins the case, so a re-saved form cannot quietly lose a row again.

Everything else is unchanged from 0.0.7: the form comes in as a class, cards are
bound later on **Register**, and a month of attendance goes back out as the same
SF2 sheet, set to print on one landscape page.

## Install

Download `Presencia-NFC-0.0.8-debug.apk` from this release and open it. Android
will ask to allow installs from this source the first time.

Upgrading from 0.0.7 (or 0.0.6, 0.0.4) keeps every class, roster, session and
setting: the class file is version 4, and older files load with the new fields
empty.

> If you already imported a school form in 0.0.7, check the learner count against
> the form. Importing the same form again adds only the missing names to the same
> class, so the fix repairs an existing import in place: import it once more.
