# Android Quick Links

Static GitHub Pages source with one-tap Android Settings intent links and copyable ADB fallbacks.

**Live page:** [https://specters312.github.io/](https://specters312.github.io/)

**Intent test runner:** [https://specters312.github.io/intent-lab.html](https://specters312.github.io/intent-lab.html)

**Root-path and automatic intent runner:** [https://specters312.github.io/root-intent-runner.html](https://specters312.github.io/root-intent-runner.html)

**Download the runner HTML:** [https://specters312.github.io/root-intent-runner.html](https://specters312.github.io/root-intent-runner.html) — use the page's “Download this HTML” link or the main page's “Download runner HTML” button.

**HyperOS intent inventory:** [https://specters312.github.io/hyperos-intents.html](https://specters312.github.io/hyperos-intents.html)

**Kunlun KP105 head unit intents:** [https://specters312.github.io/kunlun.html](https://specters312.github.io/kunlun.html) — USB mode chooser, developer options, CIT/FQC factory test, engineering secret codes, and PhoneApp intents for the SA8295P head unit.

**Generated `com.miui.*` candidates:** [https://specters312.github.io/miui-intents.html](https://specters312.github.io/miui-intents.html) — a separate manifest-derived page, deliberately kept apart from documented/confirmed intent links.

**Raw HyperOS manifest data:** [https://specters312.github.io/data/xiaomi-activity-intents.json](https://specters312.github.io/data/xiaomi-activity-intents.json)

**Native companion APK:** [https://specters312.github.io/downloads/android-intent-lab-v1.1-debug.apk](https://specters312.github.io/downloads/android-intent-lab-v1.1-debug.apk)

**Extensionless APK handler test:** [https://specters312.github.io/downloads/test](https://specters312.github.io/downloads/test) — byte-identical APK content, intentionally named `test` with no extension.

**Safe HyperOS handler probes:** [https://specters312.github.io/downloads/hyperos-handler-probe-pack.zip](https://specters312.github.io/downloads/hyperos-handler-probe-pack.zip)

The page does not include firmware, device data, credentials, exploit material, or third-party binaries. A standard webpage cannot reboot a correctly secured device; boot-mode shortcuts are provided as copyable ADB commands instead.

`intent-lab.html` provides a controlled action-only corpus runner plus a read-only browser file explorer. The explorer can inspect only a directory or files the user explicitly selects, keeps handles and contents in memory, and performs no upload or write operation. Each intent candidate requires a user tap, uses Chrome's encoded fallback URL, records local page-lifecycle observations, supports manual outcome labels, and exports JSON or CSV. It does not target explicit components or accept packages, data URIs, or extras.

`root-intent-runner.html` adds direct root/shared-storage/DocumentsUI dispatch probes and a best-effort automatic JavaScript queue backed by the preserved 2,294-case manifest corpus. Queue state and results remain in local browser storage, survive fallback reloads, and can be exported as JSON or CSV. Browser sandboxing still prevents direct enumeration of `/`, and timer-driven external intents may be blocked; the page records those limits rather than treating dispatch as success.

`hyperos-intents.html` publishes the full activity-filter inventory extracted from the two preserved HyperOS firmware APK sets. It provides package/component/action/URI search, exposure filters, raw evidence links, one-at-a-time browser launches for exported unprotected `BROWSABLE` entries, and copyable ADB commands for controlled testing. Private and permission-protected entries remain visible as manifest evidence but are not labeled as browser-launchable.

`android-app/` is the native companion source. It automatically resolves the generic and preserved-firmware corpora, inventories its host/WebView and installed packages, saves reports locally, includes an ADB-launched instrumentation runner, and provides an offline read-only filesystem explorer for the app UID.

`downloads/hyperos-handler-probes/` contains 32 inert canary formats for identifying HyperOS file handlers. The samples contain no shell command, executable payload, external entity, script import, or explicit Android component; successful opening proves handler dispatch only.

## Local preview

```sh
python3 -m http.server 8000
```

Then open `http://127.0.0.1:8000/`.
