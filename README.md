# Android Quick Links

Static GitHub Pages source with one-tap Android Settings intent links and copyable ADB fallbacks.

**Live page:** [https://specters312.github.io/](https://specters312.github.io/)

**Intent test runner:** [https://specters312.github.io/intent-lab.html](https://specters312.github.io/intent-lab.html)

**Native companion APK:** [https://specters312.github.io/downloads/android-intent-lab-v1.1-debug.apk](https://specters312.github.io/downloads/android-intent-lab-v1.1-debug.apk)

The page does not include firmware, device data, credentials, exploit material, or third-party binaries. A standard webpage cannot reboot a correctly secured device; boot-mode shortcuts are provided as copyable ADB commands instead.

`intent-lab.html` provides a controlled action-only corpus runner plus a read-only browser file explorer. The explorer can inspect only a directory or files the user explicitly selects, keeps handles and contents in memory, and performs no upload or write operation. Each intent candidate requires a user tap, uses Chrome's encoded fallback URL, records local page-lifecycle observations, supports manual outcome labels, and exports JSON or CSV. It does not target explicit components or accept packages, data URIs, or extras.

`android-app/` is the native companion source. It automatically resolves the generic and preserved-firmware corpora, inventories its host/WebView and installed packages, saves reports locally, includes an ADB-launched instrumentation runner, and provides an offline read-only filesystem explorer for the app UID.

## Local preview

```sh
python3 -m http.server 8000
```

Then open `http://127.0.0.1:8000/`.
