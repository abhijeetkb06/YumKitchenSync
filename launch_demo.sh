#!/usr/bin/env bash
# ============================================================================
# KitchenSync Demo Launcher
#
# Fully automated: checks prerequisites, creates AVDs if missing, builds the
# APK if needed, launches 3 emulators, installs and starts the app.
#
# AVDs:
#   - KS_Kiosk_Phone      (Pixel 4 phone   - Kiosk / self-order)
#   - KS_Kitchen_Tablet   (Nexus 9 tablet  - Kitchen display)
#   - KS_Manager_Phone    (Pixel 4 phone   - Store Manager)
#
# Usage:  ./launch_demo.sh
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SYSTEM_IMAGE="system-images;android-34;google_apis;arm64-v8a"
AVDS=("KS_Kiosk_Phone" "KS_Kitchen_Tablet" "KS_Manager_Phone")
DEVICES=("pixel_4" "Nexus 9" "pixel_4")
ROLES=("Kiosk" "Kitchen" "Store Manager")

# ── Auto-detect app package from app/build.gradle ──
GRADLE_FILE="$SCRIPT_DIR/app/build.gradle"
if [ -f "$GRADLE_FILE" ]; then
    APP_PACKAGE=$(grep -m1 'applicationId' "$GRADLE_FILE" | sed 's/.*applicationId[[:space:]]*["'"'"']\([^"'"'"']*\)["'"'"'].*/\1/')
fi
if [ -z "$APP_PACKAGE" ]; then
    echo "ERROR: Could not detect applicationId from app/build.gradle"
    exit 1
fi

# ── Auto-detect launcher activity from AndroidManifest.xml ──
MANIFEST="$SCRIPT_DIR/app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST" ]; then
    # Extract the activity name from the block containing LAUNCHER (macOS + Linux compatible)
    LAUNCHER_ACTIVITY=$(sed -n '/<activity/,/category.LAUNCHER/p' "$MANIFEST" \
        | grep 'android:name=' | head -1 \
        | sed 's/.*android:name="\([^"]*\)".*/\1/')
fi
if [ -z "$LAUNCHER_ACTIVITY" ]; then
    echo "WARNING: Could not detect launcher activity, will use monkey launcher."
    LAUNCH_CMD_PREFIX="adb -s %s shell monkey -p $APP_PACKAGE -c android.intent.category.LAUNCHER 1"
else
    LAUNCH_CMD_PREFIX="adb -s %s shell am start -n $APP_PACKAGE/$LAUNCHER_ACTIVITY"
fi

echo "============================================"
echo " KitchenSync Demo Launcher"
echo "============================================"
echo "  App package : $APP_PACKAGE"
echo "  Launcher    : ${LAUNCHER_ACTIVITY:-auto-detect via monkey}"
echo ""

# ── Step 1: Check prerequisites ──
echo "[1/6] Checking prerequisites..."

check_command() {
    if ! command -v "$1" &> /dev/null; then
        echo "  ERROR: $1 not found. $2"
        exit 1
    fi
    echo "  $1: OK"
}

check_command adb "Install Android SDK platform-tools."
check_command emulator "Install Android SDK emulator."
check_command avdmanager "Install Android SDK cmdline-tools."
check_command sdkmanager "Install Android SDK cmdline-tools."
echo ""

# ── Step 2: Ensure system image is installed ──
echo "[2/6] Checking system image..."
# Check if the system image directory exists on disk (most reliable cross-platform check)
ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
IMAGE_DIR="$ANDROID_SDK/system-images/android-34/google_apis/arm64-v8a"
if [ -d "$IMAGE_DIR" ]; then
    echo "  System image already installed."
else
    echo "  Installing $SYSTEM_IMAGE (this may take a few minutes)..."
    yes | sdkmanager "$SYSTEM_IMAGE"
    echo "  Installed."
fi
echo ""

# ── Step 3: Create AVDs if missing ──
echo "[3/6] Checking AVDs..."
EXISTING_AVDS=$(emulator -list-avds 2>/dev/null)

for i in "${!AVDS[@]}"; do
    avd="${AVDS[$i]}"
    device="${DEVICES[$i]}"
    role="${ROLES[$i]}"
    if echo "$EXISTING_AVDS" | grep -q "^${avd}$"; then
        echo "  $avd ($role): exists"
    else
        echo "  $avd ($role): creating (device=$device)..."
        echo "no" | avdmanager create avd \
            -n "$avd" \
            -k "$SYSTEM_IMAGE" \
            -d "$device" \
            --force > /dev/null 2>&1
        echo "  $avd ($role): created"
    fi
done
echo ""

# ── Step 4: Build APK if needed ──
APK="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
echo "[4/6] Checking APK..."
if [ -f "$APK" ]; then
    echo "  APK found: $APK"
else
    echo "  APK not found. Building with Gradle..."
    "$SCRIPT_DIR/gradlew" -p "$SCRIPT_DIR" assembleDebug --quiet
    if [ ! -f "$APK" ]; then
        echo "  ERROR: Build failed. APK not found after build."
        exit 1
    fi
    echo "  Build complete."
fi
echo ""

# ── Step 5: Launch emulators ──
echo "[5/6] Launching emulators..."
for i in "${!AVDS[@]}"; do
    avd="${AVDS[$i]}"
    role="${ROLES[$i]}"
    emulator -avd "$avd" -no-snapshot-load -no-snapshot-save -no-audio -memory 512 > /dev/null 2>&1 &
    echo "  Started: $avd ($role)"
done

echo ""
echo "  Waiting for emulators to boot..."
# Wait for all 3 emulators to appear and report boot_completed
WAITED=0
MAX_WAIT=120
while true; do
    BOOTED=0
    for emu in $(adb devices 2>/dev/null | grep "emulator-" | awk '{print $1}'); do
        if [ "$(adb -s "$emu" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
            BOOTED=$((BOOTED + 1))
        fi
    done
    if [ "$BOOTED" -ge 3 ]; then
        break
    fi
    if [ "$WAITED" -ge "$MAX_WAIT" ]; then
        echo "  WARNING: Timed out waiting for all 3 emulators (only $BOOTED booted). Continuing..."
        break
    fi
    sleep 3
    WAITED=$((WAITED + 3))
done
echo "  All emulators booted. ($WAITED seconds)"
echo ""

# ── Step 6: Install and launch app ──
echo "[6/6] Installing and launching app..."
for emu in $(adb devices 2>/dev/null | grep "emulator-" | awk '{print $1}'); do
    # Install
    adb -s "$emu" install -r "$APK" > /dev/null 2>&1
    # Launch
    if [ -n "$LAUNCHER_ACTIVITY" ]; then
        adb -s "$emu" shell am start -n "$APP_PACKAGE/$LAUNCHER_ACTIVITY" > /dev/null 2>&1
    else
        adb -s "$emu" shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 > /dev/null 2>&1
    fi
    model=$(adb -s "$emu" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    echo "  Installed and launched on $model ($emu)"
done

echo ""
echo "============================================"
echo " All 3 emulators ready!"
echo " Select roles on each device to start."
echo "============================================"
