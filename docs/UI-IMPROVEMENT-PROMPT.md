# Production prompt - Fluid UI pass on NFC Attendance Checker

Copy everything between the rules into a fresh agent session (working directory:
C:\Users\Administrator\AndroidStudioProjects\NFCAttendance).

---

## ROLE
You are a senior Android UI engineer working on an offline NFC attendance app
("NFC Attendance Checker", package com.nezzar.nfcattendance). You execute a design
pass that makes the app feel fluid and modern - closer to how Spotify feels on
Android - using ONLY Compose primitives that are already in the dependency graph.

## LOCKED CONSTRAINTS (a violation means the pass failed)
1. No new Gradle dependency, no version bump, no Gradle plugin change. The
   classpath is androidx.compose.material3 1.4.0 + animation/foundation/ui 1.10.4.
   There is NO navigation-compose, NO material-icons-core/extended, no Lottie, no Coil.
   Draw any new icon yourself as a 24dp vector drawable in res/drawable.
2. No new Android permission, no new manifest component, applicationId and namespace
   unchanged.
3. Every existing behaviour is preserved: create section, import/merge .xlsx, register
   a card, scan attendance with the 3-second de-duplication window, session bar,
   export roster/report to Documents/NFC Attendance, share, byte-order switch,
   RA 10173 privacy position. Do not touch the data layer, storage format or NFC code.
4. Existing tests must stay green: :app:testDebugUnitTest (56 tests) and
   :app:androidTest (8 instrumented tests). Run both.
5. Write only inside the project directory. No git commands. No build-file edits.
6. Imitation is of LAYOUT and MOTION language only. Never reproduce Spotify's logo,
   wordmark, icon shapes or its exact brand green #1DB954 - the accent stays #22C55E
   (pressed #16A34A).

## INPUT
Read docs/UI-UX-AUDIT.md first: 10 ranked weaknesses, each with a screenshot
(app/build/nfc-artifacts/audit/a01..a15) and file:line evidence. Treat those ten as
the work list. The three named "fix these first" are mandatory.

## THE PASS
P0 - tokens
- Raise the outline/border token until every OutlinedButton border and Switch track
  measures at least 3:1 against the surface it sits on (WCAG UI-component minimum).
  Record the measured ratio in a code comment. Keep a separate hairline token for
  purely decorative dividers.
- Establish one accent rule and apply it to every screen: the accent marks the single
  number or action that screen exists to produce, and it is absent while that number
  is still zero. Green never means "absent" and never paints a zero.

P1 - structure
- Give the app a persistent header (top app bar with the screen name plus a section
  pill) so scrolled content never runs under the status bar.
- One concept appears once per screen. Remove duplicated identity blocks, remove empty
  section headings that have no rows under them, and remove repeated sentences.
- Put each screen's primary action above the lists it would otherwise sit below.
- Every empty state carries a mark, one line of reason, and the action that fills it.
- Every disabled button states why it is disabled and offers the way out.
- Destructive actions get the same protection everywhere (two-step confirm).
- Cut the instructional prose: one-line subtitles, no sentence-length section labels.

P2 - motion (this is where "fluid" comes from)
- Touch: 220 ms, EmphasizedDecelerate; structural change: 300 ms; list stagger: 30 ms.
- Press feedback = scale 0.98 driven by a spring, sharing one interaction source with
  the ripple; keep the existing haptics on save and card read.
- Screen change: direction-aware slide + fade.
- Anything that appears or changes size animates (AnimatedVisibility, animateContentSize,
  Modifier.animateItem()); numbers animate with animateIntAsState.
- Reuse the section's green card as the visual anchor when moving between tabs; do not
  build a shared-element transition that needs a new dependency.

P3 - verification (evidence or it did not happen)
- Rebuild with the project's own wrapper (NEVER the android-studio MCP gradle tools):
  $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat ...
- :app:testDebugUnitTest and the instrumented suite must pass.
- Install on the running AVD and capture every state at 1080x2400 into
  app/build/nfc-artifacts/, reading each screen back with uiautomator dump so the
  claim is checked, not assumed.
- Re-run the four tabs at font_scale 1.3 and again with animator_duration_scale 0 -
  nothing may depend on an animation, and nothing may clip at large fonts.
- Recompute the contrast ratios from the literals in ui/theme/Color.kt and state the
  numbers.

## DONE WHEN
- The ten audit findings are closed, each with a file:line and a screenshot.
- Both test suites are green.
- Every measured number (contrast, dp, ms, sp) in the report comes from the code or a
  device dump, never from memory.
- The final report lists: what changed per file, the measured numbers, what was
  deliberately NOT done and why, and what a real phone with an NFC card still has to
  confirm (the emulator has no NFC adapter, so the pending-card state cannot be
  observed there).

## STOP AND ASK
Adding a dependency, touching anything outside the project, changing the storage
format or any behaviour, or editing a Gradle file.
