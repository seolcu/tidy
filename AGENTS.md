# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

## What this is

Tidy: an Android launcher whose apps screen sorts every installed
app into category folders automatically. It exists because a Galaxy's own
apps screen (One UI Home) can't be rearranged by other apps -- there is no
API for another app to create folders there -- so Tidy *replaces* that
screen instead: it can be set as the default home app, or opened like any
app. Single Kotlin/Compose module, no backend.

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
so it stays testable there. There is no instrumented test suite, and the
emulator (`~/.local/share/android-sdk/emulator`) segfaults when booted
headless on this machine; check launcher behavior on a real phone.

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

### Why a launcher

Android gives third-party apps no way to edit another launcher's folders
(One UI Home keeps its layout in a private database). `MainActivity` is
therefore registered for both `LAUNCHER` and `HOME`
(`singleTask`, so the home button reuses it and arrives in `onNewIntent`,
which resets to a clean apps screen). As home, back does nothing at the
top level. `launcher/HomeRole` asks for `RoleManager.ROLE_HOME`, falling
back to the system's home-app settings. The window is transparent over the
wallpaper (`windowShowWallpaper`); `AppsScreen` paints a 90% wash.

v1 deliberately has no widgets, dock or home-screen icon grid: "home" is
the sorted apps screen itself.

### App list

`data/AppRepository` (one per process, owned by `TidyApplication`) lists
launchable activities with `LauncherApps.getActivityList` across all
`UserManager.userProfiles` (so work-profile apps appear, badged), and
re-lists on every `LauncherApps.Callback` event. No `QUERY_ALL_PACKAGES`:
a launcher doesn't need it. `onPackageAdded` also emits on `installed`,
which `AppsViewModel` turns into the "X → folder · Change" snackbar once
the reload containing that app lands. `data/IconCache` decodes badged
icons once at 56dp and evicts a package's icons when it changes.

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

A user's move is stored per package name in `SettingsRepository`
(`override.<pkg>` = `Category.name`) and always beats classification;
moving an app back to its automatic folder deletes the override.
Renamed folders are `rename.<CATEGORY>`. Both are keyed by enum *name*, so
never rename a `Category` entry -- only add. `buildFolders` drops empty
folders, sorts biggest first and keeps SYSTEM/ETC last.

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
