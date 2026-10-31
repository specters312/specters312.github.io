HyperOS read-only handler probe pack

Canary: HYPEROS_HANDLER_PROBE_20261031_7F3A

These files are inert parser/handler probes. They contain no shell command, Android
component target, executable payload, script import, external entity, remote media,
credential, or device-specific data. Opening a file tests which installed application
claims the extension or MIME type. It does not establish code execution.

The .sh, .py, .js, and .lua samples contain comments or constant declarations only.
The .desktop sample is Type=Link and intentionally has no Exec field. The .url and
.webloc samples point only to the public Intent Lab canary fragment.

Record the filename, chooser/handler shown, package name if visible, whether the file
rendered, and any crash. Do not interpret a successful open as shell access.
