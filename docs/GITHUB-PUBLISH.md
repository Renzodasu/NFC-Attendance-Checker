# Publishing this repository on GitHub

Everything below is copy-paste ready. The repository files themselves (README, LICENSE,
.gitignore, the CI workflow, the guide and the screenshots) already live in this project.

## 1. Create the repository

Go to **https://github.com/new** and paste:

| Field | Value |
| --- | --- |
| Repository name | `NFC-Attendance-Checker` |
| Description | Offline Android app for class attendance: students tap a MIFARE ID card to join a section roster, then tap again to be marked present. Exports an absent-first .xlsx to the phone's Documents folder. No server, no accounts, NFC permission only. |
| Visibility | Public (or Private) |
| Initialize this repository with | **leave all three boxes unticked** - README, .gitignore and LICENSE already exist here |

**Topics** (paste one at a time, or comma-separated):

```text
android, kotlin, jetpack-compose, nfc, mifare-classic, attendance-tracker, offline-first, xlsx, material3, education
```

## 2. Push the project

```powershell
cd C:\Users\Administrator\AndroidStudioProjects\NFCAttendance

git init
git add .
git commit -m "NFC Attendance Checker 1.0 - offline card-tap attendance with Excel export/import"
git branch -M main
git remote add origin https://github.com/<your-account>/NFC-Attendance-Checker.git
git push -u origin main
```

Two things that bite people here:

- **Keep the wrapper executable.** On Windows the `gradlew` exec bit is often lost, and CI then
  fails with `Permission denied`. Fix it once with
  `git update-index --chmod=+x gradlew && git commit -m "Make gradlew executable"`.
- **First run only:** `git config --global user.name "Your Name"` and
  `git config --global user.email "you@example.com"`.

## 3. Fill in the About panel

On the repository page, press the cog next to **About**:

- **Description** - the same sentence as above.
- **Topics** - the list above.
- **Website** - leave empty.
- Tick **Releases** and **Packages** only if you publish them.

## 4. Cut a release

1. Build the APK: `./gradlew :app:assembleDebug` (or `.\gradlew.bat` on Windows).
   The artefact lands at
   `app/build/outputs/apk/debug/Presencia-NFC-<version>-debug.apk`.
2. Commit and tag: `git tag v.0.0.6` (the version in `app/build.gradle.kts`),
   then `git push --follow-tags`.
3. On GitHub: **Releases → Draft a new release** → choose the tag, title it
   **Presencia 0.0.6**, and paste the newest `CHANGELOG.md` section as the notes.
4. Attach `Presencia-NFC-0.0.6-debug.apk` (debug-signed - fine for classroom
   use, not for the Play Store).
5. The **Actions** tab builds and unit-tests every push to `main` or `master`
   and keeps the APK as a workflow artefact.

## 5. What is in the repository

```text
README.md                                  project front page (what it does, install, build, tests)
LICENSE                                    MIT
.gitignore                                 Android/Gradle/IDE noise + signing material
.github/workflows/android.yml              CI: assembleDebug + testDebugUnitTest, uploads the APK
docs/GITHUB-PUBLISH.md                     this file
docs/NFC-Attendance-Checker-Guide.html     the illustrated how-to-use guide (self-contained)
docs/NFC-Attendance-Checker-Guide.png      the same guide as an image
docs/screenshots/*.png                     the images the README shows
app/                                       the Android app
```

## 6. Optional next steps

- **CI:** the workflow runs on `ubuntu-latest` with JDK 21. If Gradle reports a missing
  `platforms;android-37`, add a step that runs
  `sdkmanager --install "platforms;android-37"` before the build.
- **Branch protection:** Settings → Branches → require the *Build and unit-test* check before merge.
- **Release signing:** generate a keystore, keep it out of git (already ignored), and add the
  `signingConfigs` block plus GitHub secrets when you want a release build.
- **Issues:** Settings → Features → Issues gives you a tracker with zero setup.
