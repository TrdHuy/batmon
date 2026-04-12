#!/bin/bash

set -e

PACKAGE="com.android.synclab.glimpse"
PORT=8700
MODE=${1:-full}   # full | attach
ADB=${ADB:-adb}

echo "======================================"
echo " Android Debug Script"
echo " Mode: $MODE"
echo " Package: $PACKAGE"
echo "======================================"

# ========================
# 1. Check device
# ========================
DEVICE_COUNT=$($ADB devices | grep -w "device" | wc -l)

if [ "$DEVICE_COUNT" -eq 0 ]; then
  echo "❌ No device connected"
  exit 1
fi

# ========================
# 2. Clean port forward
# ========================
$ADB forward --remove tcp:$PORT 2>/dev/null || true

# ========================
# 3. Mode FULL
# ========================
if [ "$MODE" = "full" ]; then
  echo "[FULL] Build & install..."
  ./gradlew installDebug -x test

  echo "[FULL] Set debug-app (wait for debugger)..."
  $ADB shell am set-debug-app -w $PACKAGE

  echo "[FULL] Launch app..."
  $ADB shell monkey -p $PACKAGE -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

# ========================
# 4. Mode ATTACH
# ========================
else
  echo "[ATTACH] Skip build & install"
  echo "[ATTACH] Ensure debug-app is cleared..."

  # ⚠️ reset debug-app để tránh app bị stuck WAIT
  $ADB shell am clear-debug-app || true

fi

# ========================
# 5. Get PID (retry)
# ========================
echo "[PID] Finding process..."

PID=""

for i in {1..10}; do
  PID=$($ADB shell pidof $PACKAGE 2>/dev/null | tr -d '\r')

  if [ ! -z "$PID" ]; then
    break
  fi

  echo "   ⏳ Waiting... ($i)"
  sleep 1
done

if [ -z "$PID" ]; then
  echo "❌ Cannot find PID"
  exit 1
fi

echo "   ✅ PID = $PID"

# ========================
# 6. Forward JDWP
# ========================
echo "[JDWP] Forwarding..."
$ADB forward tcp:$PORT jdwp:$PID

echo "======================================"
echo " ✅ Ready to attach debugger"
echo "======================================"