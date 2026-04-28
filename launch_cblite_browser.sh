#!/bin/bash
#
# CBLite Browser - Live document viewer for Couchbase Lite databases on Android emulators
#
# Usage: ./launch_cblite_browser.sh
#
# Prerequisites:
#   - Android emulators running with the KitchenSync app installed
#   - cblite CLI installed (brew tap couchbase/tap && brew install cblite)
#   - Python 3 installed
#
# This script:
#   1. Creates working directories if needed
#   2. Does an initial pull of databases from all running emulators
#   3. Starts a live server that auto-refreshes every 3 seconds
#   4. Opens the browser at http://localhost:8091
#

set -e

PORT=8091
VIEWER_DIR="/tmp/cblite_viewer"
DB_DIR="/tmp/cblite_dbs"

# Check prerequisites
if ! command -v cblite &> /dev/null; then
    echo "ERROR: cblite not found. Install with: brew tap couchbase/tap && brew install cblite"
    exit 1
fi

if ! command -v adb &> /dev/null; then
    echo "ERROR: adb not found. Make sure Android SDK platform-tools is in your PATH."
    exit 1
fi

if ! command -v python3 &> /dev/null; then
    echo "ERROR: python3 not found."
    exit 1
fi

# Check for running emulators
EMULATORS=$(adb devices 2>/dev/null | grep "emulator-" | awk '{print $1}')
if [ -z "$EMULATORS" ]; then
    echo "ERROR: No running emulators found. Launch emulators first with ./launch_demo.sh"
    exit 1
fi

echo "Found emulators:"
echo "$EMULATORS" | while read emu; do echo "  - $emu"; done

# Create DB directories
mkdir -p "$DB_DIR/kiosk.cblite2" "$DB_DIR/kitchen.cblite2" "$DB_DIR/manager.cblite2"

# Initial pull from emulators
echo ""
echo "Pulling databases from emulators..."

pull_db() {
    local serial=$1 local_dir=$2 name=$3
    if adb -s "$serial" shell "run-as com.kitchensync tar cf - /data/data/com.kitchensync/files/kitchensync.cblite2" 2>/dev/null | tar xf - -C "$local_dir" --strip-components=5 2>/dev/null; then
        echo "  $name ($serial): OK"
    else
        echo "  $name ($serial): FAILED (app may not be installed)"
    fi
}

pull_db "emulator-5558" "$DB_DIR/kiosk.cblite2" "Kiosk"
pull_db "emulator-5556" "$DB_DIR/kitchen.cblite2" "Kitchen"
pull_db "emulator-5554" "$DB_DIR/manager.cblite2" "Manager"

# Kill any existing server on the port
if lsof -ti:$PORT &>/dev/null; then
    echo ""
    echo "Stopping existing server on port $PORT..."
    kill $(lsof -ti:$PORT) 2>/dev/null || true
    sleep 1
fi

# Start the live server
echo ""
echo "Starting CBLite Browser on http://localhost:$PORT"
echo "Auto-refreshing from emulators every 3 seconds"
echo "Press Ctrl+C to stop"
echo ""

# Open browser after a short delay
(sleep 2 && open "http://localhost:$PORT") &

# Run the server (foreground, Ctrl+C to stop)
cd "$VIEWER_DIR"
python3 server.py
