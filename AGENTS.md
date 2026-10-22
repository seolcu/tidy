# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

## What this is

Tidy organizes the folders of One UI's own apps screen (Galaxy phones).
There is no API for another app to change One UI Home's layout, so Tidy
does it the way a person would: an accessibility service opens the apps
screen and uses One UI's own controls (select mode, "add apps to folder",
folder menu). The user keeps the real apps screen; Tidy only changes what
folders are in it. Two ways to organize: keep the user's folders and file
the loose apps into them, or ignore them and rebuild everything from
Tidy's categories. Plus a new-app helper and undo. Single Kotlin/Compose
app module (`app/`) plus `testapp/`, no backend.

0.1.0 was a launcher that replaced the apps screen instead; the user found
it unfamiliar and buggy, and it only covered One UI's screen, so 0.2.0
dropped that approach entirely.

Sibling project: WakeLink (`../wakelink`). Tidy deliberately copies its
toolchain, conventions and some code (see "Shared with WakeLink" below);
when changing one of the shared pieces, consider porting the change.

## Commands

Gradle here (AGP 9.4.1 / Gradle 9.8.0, built-in Kotlin -- there is no
`kotlin-android` plugin) needs a **full JDK 17+** (with `javac`); the
system `/usr/lib/jvm/java-25-openjdk` is runtime-only. Same JDK and SDK
as WakeLink:

```bash
export JAVA_HOME=~/.local/share/jdks/jdk-21.0.12.1+1
export ANDROID_HOME=~/.local/share/android-sdk
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew lintDebug
./gradlew testDebugUnitTest   # single class: ./gradlew testDebugUnitTest --tests '*CategorizerTest'
./gradlew assembleDebug
./gradlew assembleRelease     # only with keystore/tidy-upload.properties (gitignored)
```

`app/src/test` holds plain-JVM unit tests for the pure logic
(`domain/`: classification, folder grouping, search; `update/`: version
compare, release notes). Keep that logic free of Android framework types
so it stays testable there (`organize/`: planning, the executor against a
fake drawer, the undo snapshot codec). There is no instrumented test
suite, and the emulator (`~/.local/share/android-sdk/emulator`) segfaults
when booted headless on this machine; anything in `oneui/` has to be
checked on a real Galaxy phone.

Testing on the phone, learned the hard way:

- `uiautomator dump` suspends every other accessibility service while it
  runs, Tidy's included (it unbinds and rebinds). After a dump, wait a few
  seconds before tapping anything that needs the service.
- After `adb install -r`, One UI may leave the already-enabled service
  stuck in "Binding services" (`dumpsys accessibility`). Writing the same
  `enabled_accessibility_services` value back (blank, then the old value)
  rebinds it.
- `am force-stop app.tidy.android` turns the accessibility service off
  (it has to be enabled again by hand), so the release script doesn't use
  it.
- `testapp/` is a code-free app labelled "테스트 페이" (a finance name) for
  trying the new-app helper: `./gradlew :testapp:assembleDebug`, then
  `adb install`/`adb uninstall app.tidy.testpay`.

The app is localized: all user-facing text lives in
`res/values/strings.xml` (English -- the default and the fallback) and
`res/values-ko/strings.xml` (Korean). Never hardcode UI text in Kotlin;
add every new string to both files. Korean tone (same as WakeLink):
the app name is always "Tidy", in Latin letters, in both languages (as
WakeLink); buttons, labels, statuses and explanations are short noun phrases
("자동 분류됨", "다른 폴더로 옮기기"); only text that speaks to the user --
dialog bodies, errors, empty states, onboarding title -- uses 해요체, one
or two short sentences.

### Releasing the APK

Same flow as WakeLink: bump `versionCode` and `versionName` in
`app/build.gradle.kts`, commit and push, connect a phone over adb, then
run `scripts/release-android.sh [notes.md]`. It builds `assembleRelease`
with the upload key, installs and launches that exact APK on the phone
(aborting on a crash -- R8 breaks reflection in ways debug builds never
show; see `proguard-rules.pro`), and publishes GitHub release
`v<versionName>` with the APK attached as exactly `tidy.apk`. The in-app
updater reads `releases/latest` of `BuildConfig.UPDATE_REPO`
(`seolcu/tidy`). Release notes are bilingual: `## English`, then
`## 한국어` (the update dialog splits on exactly those headings).

The release keystore (`keystore/tidy-upload.{jks,properties}`) is
gitignored. Always sign with the same key -- Android rejects an update
signed differently, and a debug build can't be updated to a release build
(uninstall first).

It's kept in the maintainer's Proton Drive under `/my-files/tidy-secrets/`,
laid out at the same path as in the repo (`keystore/`). To release from
another machine, from the repo root:

```bash
proton-drive auth login
proton-drive fs download -d merge -f remove /my-files/tidy-secrets/keystore .
```

If the keystore ever changes, upload the new copy there too
(`proton-drive fs upload -f replace keystore/<file> /my-files/tidy-secrets/keystore`).

## Architecture

### Driving One UI's apps screen (`oneui/`)

`OrganizerService` is the accessibility service (scoped to
`com.sec.android.app.launcher`, res/xml/organizer_service.xml); the user
turns it on in Accessibility settings. `OneUiDrawer` is the only code that
knows One UI: view ids, wording and timings are constants at its bottom,
and its class comment lists exactly how each operation is done (all found
on a real S24, One UI 8 / Android 17). Highlights:

- Icons are `icon_container` / `folder_icon_container`, named by their
  inner TextView (descriptions carry extras like ", 알림 1개"). Some
  folders sit inside an extra `icon_container`; reading those as apps once
  lost the top five folders.
- Read only once the screen has stopped moving (`settledIcons`): mid
  animation the top rows aren't "visible" yet.
- Folders show 12 apps per page; later pages exist in the tree only after
  swiping to them.
- Adding to a folder uses the folder's "add apps" picker (search, tap the
  row -- its checkbox isn't clickable -- done); new folders come from select
  mode's "폴더 추가"; dissolving is the folder menu's "삭제", which keeps
  the apps. No drag and drop anywhere.
- An app's long-press menu has "설치 삭제" (uninstall) and select mode has an
  uninstall button: menu items are matched by exact text only, after
  checking the menu header is the icon that was long-pressed.
- New folders are made in one pass down the list in select mode: long-press
  the first of the folder's apps that's fully in view, "선택", then tap
  every wanted app on each screen while scrolling (a real tap -- an
  accessibility click doesn't tick in select mode), up to three passes.
  Searching from the top for each app was far too slow with 160 loose apps.
- Never touch the navigation bar or the apps screen's floating search bar.
  Every gesture goes through `gesture()`, which refuses points outside
  `safeArea()` and, on the apps screen with no folder open, below the
  search bar (remembered when seen: it hides in select mode and while
  scrolling, then comes back). Stray taps there ended select mode mid-run,
  after which the next taps opened apps or the search screen. Each tap in
  select mode also re-checks that select mode is still on and re-reads the
  icon's position first.
- Folder titles: tap the title, wait for edit mode (it wipes the field),
  set the text, commit with the keyboard's enter. Setting text before the
  keyboard is up, or BACK while editing, loses the name.
- Coming back to Tidy: close the apps screen with BACK, not HOME -- right
  after a home press Android blocks other apps' activity starts for a few
  seconds, so Tidy never reappeared.
- Anything the user does mid-run (opening recents, tapping) makes the next
  step's check fail and the run stops; re-running continues from the
  current state.
- One UI can unbind and rebind the same service object; the coroutine
  scope is recreated on every `onServiceConnected`.

`Organizer` is the app-wide face: `work` (reading / organizing n of m /
finished / failed), `lastRead` layout, `ready` (service connected), and
`read()` / `apply(target)` / `undo()` / `stop()`. A run plans on the last
read if it's under 10 minutes old (read → preview → apply), otherwise
reads first; it stops at the first step that doesn't verify, and
afterwards takes the outcome from `DrawerLayout.after(steps)` instead of
reading again (or forgets the read after a stop/failure). Reading the
screen takes ~15 s and each new folder ~10 s on the S24.

The undo snapshot is the screen before the first run of a series that
didn't finish (`lastRunFinished`): retrying an interrupted run must not
replace it with the half-done state (that happened once and lost the
user's original folders).

### Planning (`organize/`)

Pure Kotlin. `DrawerLayout` is the apps screen by labels (folders + loose
apps). `Planner.keepFolders` files loose apps into the user's folders --
`Taxonomy` gives each folder the category most of its apps have, and a
folder without a majority is left alone -- or into a new folder when two
or more share an uncovered category. `Planner.rebuild` ignores the user's
folders and sorts everything by Tidy's categories. `Planner.edit` applies
the preview's hand edits. `Planner.steps(current, target)` is the one diff
for all of it, including undo (target = the snapshot saved before the last
run): a folder survives when the target folder of the same name still
holds all its apps (the rest are added); otherwise it's dissolved and
remade -- unless a current folder holds exactly a wanted folder's apps,
which is just renamed. One UI has no nested folders and Tidy never makes
any.

### App list

`data/AppRepository` (one per process, owned by `TidyApplication`) lists
launchable activities with `LauncherApps.getActivityList`, which is how
One UI labels map to packages for classification. `onPackageAdded` also
emits on `installed`; the service turns that into the new-app helper's
notification ("put X in folder Y?" → "Add"), or adds it right away in
AUTO mode, using the folders from the last read.

### Classification (`domain/`)

`Categorizer.classify` runs, in order: `KnownApps` (exact package, then
longest prefix -- Korean apps rarely declare a category, and Samsung's own
apps live under `com.samsung.`/`com.sec.`), the app's own
`ApplicationInfo.category` when it's a specific one (game, audio, video,
image, maps), keyword match on package segments and label
(ASCII keywords under 5 letters must match a whole word, so "tv" doesn't
hit "activity"; Hangul keywords match anywhere in the label), then a
vague declared category (social, news, productivity -- on a real phone,
payment apps, e-book readers and VPNs all declared "productivity"), then
SYSTEM for built-in apps, else ETC. Everything runs on the phone; the app
list is never sent anywhere (the only network use is the updater).

To tune classification against a real phone, a debug build logs every
app's inputs and result:
`adb logcat -s TidyClassify` (package | label | declared category id |
system flag | folder). Add misses to `KnownApps` or the keyword lists, with
a `CategorizerTest` case for any new ordering rule.

Names for Tidy's own folders can be stored as `rename.<CATEGORY>`, keyed
by enum *name*, so never rename a `Category` entry -- only add.
`buildFolders` drops empty folders, sorts biggest first and keeps
SYSTEM/ETC last.

### Shared with WakeLink

Copied from `../wakelink/android` with only the package renamed:
`update/*` (GitHub-release self-update, release-notes parsing, periodic
`UpdateCheckWorker`, `UpdateDialog`) and their tests, `ui/OnResume`,
`ui/components/Sections` (`SectionCard`, `SettingsRow`), `ui/theme/Type`
(Pretendard, `res/font/`, license in `third_party/pretendard/`),
`scripts/release-android.sh`, the CI workflow, the version catalog, and
the Gradle wrapper. `SettingsRepository` keeps WakeLink's names for the
update flags (`autoUpdateCheck`, `notifiedUpdateVersion`,
`skippedUpdateVersion`) so `update/` stays a drop-in copy.
