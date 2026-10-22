#!/usr/bin/env bash
# Builds a release-signed APK and publishes it as a GitHub release.
#
#   scripts/release-android.sh [notes.md]
#
# The release is tagged v<versionName> from app/build.gradle.kts (bump
# versionCode + versionName first) and carries the APK as "tidy.apk",
# which is the name both the in-app updater (update/AppUpdater.kt) and the
# stable link https://github.com/seolcu/tidy/releases/latest/download/tidy.apk
# rely on. Without a notes file, GitHub generates notes from commits.
#
# Needs: gh (authenticated), a phone connected over adb (launch smoke
# test, see below), a full JDK 17+, the Android SDK, and
# keystore/tidy-upload.properties -- the APK must be signed with the same
# key every time, or installed copies can't update to it.
#
# Adapted from WakeLink's scripts/release-android.sh.
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"

notes_file=${1:-}
if [[ -n "$notes_file" && ! -f "$notes_file" ]]; then
    echo "notes file not found: $notes_file" >&2
    exit 1
fi

if [[ -n $(git status --porcelain) ]]; then
    echo "working tree is dirty; commit first so the tag matches the APK" >&2
    exit 1
fi
git fetch --quiet origin
if [[ $(git rev-parse HEAD) != $(git rev-parse '@{upstream}') ]]; then
    echo "HEAD is not pushed (or is behind upstream); push/pull first" >&2
    exit 1
fi

version=$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)
tag="v$version"
if [[ -z "$version" ]]; then
    echo "could not read versionName from app/build.gradle.kts" >&2
    exit 1
fi
if git rev-parse -q --verify "refs/tags/$tag" >/dev/null || gh release view "$tag" >/dev/null 2>&1; then
    echo "$tag already exists; bump versionCode/versionName first" >&2
    exit 1
fi

[[ -f keystore/tidy-upload.properties ]] || { echo "missing keystore/tidy-upload.properties" >&2; exit 1; }

export JAVA_HOME=${JAVA_HOME:-$HOME/.local/share/jdks/jdk-21.0.12.1+1}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/.local/share/android-sdk}
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --quiet clean assembleRelease

out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
cp app/build/outputs/apk/release/app-release.apk "$out/tidy.apk"

# Smoke-test the exact APK on a real device before publishing: release
# builds are minified by R8, which can break reflection-based libraries in
# ways debug builds never show (WakeLink 0.2.0-0.2.2 all crashed on launch
# because of this). Installs over the existing app (same key, so data is
# kept), launches it and fails on any crash.
adb_bin=${ADB:-$ANDROID_HOME/platform-tools/adb}
if [[ "${SKIP_DEVICE_CHECK:-}" != 1 ]]; then
    if ! "$adb_bin" get-state >/dev/null 2>&1; then
        echo "no device connected for the launch smoke test; connect one (USB debugging)" >&2
        echo "or rerun with SKIP_DEVICE_CHECK=1 if you really must publish untested" >&2
        exit 1
    fi
    pkg=app.tidy.android
    "$adb_bin" install -r "$out/tidy.apk" >/dev/null
    "$adb_bin" logcat -b crash -c
    # No `am force-stop`: it turns Tidy's accessibility service off (the user
    # would have to enable it again). The install already restarted the app.
    "$adb_bin" shell am start -W -n "$pkg/.MainActivity" >/dev/null
    sleep 8
    if "$adb_bin" logcat -d -b crash | grep -q "$pkg" || [[ -z $("$adb_bin" shell pidof "$pkg" | tr -d '\r') ]]; then
        echo "release APK crashed on launch on the device; not publishing:" >&2
        "$adb_bin" logcat -d -b crash | grep -E 'FATAL|Exception|Caused by' >&2 || true
        exit 1
    fi
    echo "launch smoke test passed on $("$adb_bin" shell getprop ro.product.model | tr -d '\r')"
fi

notes_args=(--generate-notes)
[[ -n "$notes_file" ]] && notes_args=(--notes-file "$notes_file")

gh release create "$tag" "$out/tidy.apk" \
    --target "$(git rev-parse HEAD)" \
    --title "Tidy $version" \
    --latest \
    "${notes_args[@]}"

echo "published $tag"
echo "latest APK: https://github.com/seolcu/tidy/releases/latest/download/tidy.apk"
