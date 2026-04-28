#!/bin/bash
#
# CBLite Browser - Live document viewer for Couchbase Lite databases on Android emulators
#
# Auto-detects the app package and database name from the Android project
# this script lives in, then launches the cblite-browser viewer.
#
# Fully automated: installs missing prerequisites (cblite, python3) via
# Homebrew, clones the cblite-browser tool, and launches the viewer.
#
# Usage: ./launch_cblite_browser.sh
#

set -e

REPO_URL="https://github.com/abhijeetkb06/cblite-browser.git"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BROWSER_DIR="$SCRIPT_DIR/cblite-browser"

echo "============================================"
echo " CBLite Browser Launcher"
echo "============================================"
echo ""

# --- Step 1: Check and install prerequisites ---
echo "[1/4] Checking prerequisites..."

# Homebrew (needed to install cblite and python3)
ensure_brew() {
    if command -v brew &> /dev/null; then
        return 0
    fi
    echo "  Homebrew not found. Installing..."
    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
    # Add brew to PATH for Apple Silicon Macs
    if [ -f /opt/homebrew/bin/brew ]; then
        eval "$(/opt/homebrew/bin/brew shellenv)"
    fi
}

# adb
if ! command -v adb &> /dev/null; then
    echo "  ERROR: adb not found. Install Android SDK platform-tools first."
    exit 1
fi
echo "  adb: OK"

# python3
if ! command -v python3 &> /dev/null; then
    echo "  python3: not found. Installing via Homebrew..."
    ensure_brew
    brew install python3
fi
echo "  python3: OK ($(python3 --version 2>&1 | awk '{print $2}'))"

# cblite CLI
if ! command -v cblite &> /dev/null; then
    echo "  cblite: not found. Installing via Homebrew..."
    ensure_brew
    brew tap couchbase/tap 2>/dev/null || true
    brew install cblite
fi
echo "  cblite: OK"

# git
if ! command -v git &> /dev/null; then
    echo "  ERROR: git not found."
    exit 1
fi
echo "  git: OK"

# Running emulators
EMULATORS=$(adb devices 2>/dev/null | grep "emulator-" | awk '{print $1}')
if [ -z "$EMULATORS" ]; then
    echo ""
    echo "  WARNING: No running emulators found."
    echo "  Launch emulators first with: ./launch_demo.sh"
    echo "  The browser will start but show no data until emulators are running."
fi
echo ""

# --- Step 2: Auto-detect app package from app/build.gradle ---
echo "[2/4] Auto-detecting project config..."
GRADLE_FILE="$SCRIPT_DIR/app/build.gradle"
if [ -f "$GRADLE_FILE" ]; then
    APP_PACKAGE=$(grep -m1 'applicationId' "$GRADLE_FILE" | sed 's/.*applicationId[[:space:]]*["'"'"']\([^"'"'"']*\)["'"'"'].*/\1/')
fi
if [ -z "$APP_PACKAGE" ]; then
    echo "  ERROR: Could not detect applicationId from app/build.gradle"
    echo "         Make sure this script is in the root of an Android project."
    exit 1
fi

# Auto-detect database name from source code
DB_NAME=$(grep -r 'DATABASE_NAME\s*=' "$SCRIPT_DIR/app/src" 2>/dev/null \
    | grep -oE '"[^"]+"' | tr -d '"' | head -1)
if [ -z "$DB_NAME" ]; then
    DB_NAME="${APP_PACKAGE##*.}"
fi

echo "  App package : $APP_PACKAGE"
echo "  DB name     : $DB_NAME"
echo ""

# --- Step 3: Clone the cblite-browser repo if not already present ---
echo "[3/4] Checking CBLite Browser tool..."
if [ ! -f "$BROWSER_DIR/server.py" ]; then
    echo "  Not found locally. Cloning from GitHub..."
    git clone "$REPO_URL" "$BROWSER_DIR"
else
    echo "  Already cloned."
fi
echo ""

# --- Step 4: Launch ---
echo "[4/4] Launching CBLite Browser..."
cd "$BROWSER_DIR"
exec bash launch.sh --app "$APP_PACKAGE" --dbname "$DB_NAME" --port 8091 --interval 3
