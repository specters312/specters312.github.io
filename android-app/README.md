# Android Intent Lab companion app

The native companion removes the two limitations that a normal webpage cannot remove:

- It automatically resolves the complete corpus through Android `PackageManager` without one tap per case.
- It inventories its host package, bundled components, current WebView provider, and installed packages.

The app loads only APK-bundled HTML. Network loads and navigation are blocked before the JavaScript bridge is attached.

The native UI also includes a local one-line command console. It runs `/system/bin/sh` as the app's sandboxed UID, has a 15-second timeout and 1 MiB output cap, and has no network permission, listener, background service, or remote command channel. It is not an ADB or root shell unless a separate `su` implementation explicitly grants access.

## Build

```sh
gradle :app:assembleDebug
```

The project requires Android SDK platform 34 and Android Gradle Plugin 8.13.2.

## Install

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.specters312.intentlab/.MainActivity
```

The native UI automatically resolves the generic Android corpus and the corpus generated from the two preserved firmware APK sets. Reports are saved under the app's external `intent-lab` files directory.

## Automated launch instrumentation

Resolution-only mode:

```sh
adb shell am instrument -w -r \
  io.github.specters312.intentlab/.IntentFuzzInstrumentation
```

Launch every generic action, capture `am start -W` and the focused window, press Back, and continue:

```sh
adb shell am instrument -w -r \
  -e launch true \
  -e delayMs 750 \
  io.github.specters312.intentlab/.IntentFuzzInstrumentation
```

Android restricts ordinary background activity launches, so the unattended launch loop is instrumentation-only. Device-derived deep links are automatically enumerated but are not batch-launched.

## Regenerate the preserved-firmware corpus

Run `tools/scan_browsable_apks.py` with one or more labeled APK roots. The committed asset records the source-relative APK path and SHA-256 for every extracted manifest filter.

`QUERY_ALL_PACKAGES` is used because this is a sideloaded security-lab inventory tool. The permission is restricted for general Google Play distribution.
