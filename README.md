# Android Quick Links

Static GitHub Pages source with one-tap Android Settings intent links and copyable ADB fallbacks.

**Live page:** [https://specters312.github.io/](https://specters312.github.io/)

**Intent test runner:** [https://specters312.github.io/intent-lab.html](https://specters312.github.io/intent-lab.html)

The page does not include firmware, device data, credentials, exploit material, or third-party binaries. A standard webpage cannot reboot a correctly secured device; boot-mode shortcuts are provided as copyable ADB commands instead.

`intent-lab.html` provides a controlled action-only corpus runner. Each candidate requires a user tap, uses Chrome's encoded fallback URL, records local page-lifecycle observations, supports manual outcome labels, and exports JSON or CSV. It does not target explicit components or accept packages, data URIs, or extras.

## Local preview

```sh
python3 -m http.server 8000
```

Then open `http://127.0.0.1:8000/`.
