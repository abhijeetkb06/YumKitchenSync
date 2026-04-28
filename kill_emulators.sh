#!/bin/bash
#
# Kill all running Android emulators
#
# Usage: ./kill_emulators.sh
#

EMULATORS=$(adb devices 2>/dev/null | grep "emulator-" | awk '{print $1}')

if [ -z "$EMULATORS" ]; then
    echo "No running emulators found."
    exit 0
fi

echo "Killing emulators..."
echo "$EMULATORS" | while read emu; do
    adb -s "$emu" emu kill 2>/dev/null
    echo "  Killed $emu"
done

sleep 2
echo "Done."
