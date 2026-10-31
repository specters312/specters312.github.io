#!/bin/sh
set -eu

ADB_BIN=${ADB_BIN:-adb}
DEVICE_SERIAL=${DEVICE_SERIAL:-}
DELAY_MS=${DELAY_MS:-750}

if [ -n "$DEVICE_SERIAL" ]; then
  set -- -s "$DEVICE_SERIAL"
else
  set --
fi

exec "$ADB_BIN" "$@" shell am instrument -w -r \
  -e launch true \
  -e delayMs "$DELAY_MS" \
  io.github.specters312.intentlab/.IntentFuzzInstrumentation
