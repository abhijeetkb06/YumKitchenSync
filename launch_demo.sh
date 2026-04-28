#!/usr/bin/env bash
# ============================================================================
# Launch 3 Kitchen Sync emulator instances
#   - KS_Kiosk_Phone      (Kiosk / self-order)
#   - KS_Kitchen_Tablet   (Kitchen display)
#   - KS_Manager_Phone    (Store Manager / Supervisor)
#
# Usage:  ./launch_demo.sh
# ============================================================================

echo "Launching 3 Kitchen Sync emulators..."
echo ""

emulator -avd KS_Kiosk_Phone -no-snapshot-load -no-snapshot-save -no-audio -memory 512 &
echo "  Started: KS_Kiosk_Phone (Kiosk)"

emulator -avd KS_Kitchen_Tablet -no-snapshot-load -no-snapshot-save -no-audio -memory 512 &
echo "  Started: KS_Kitchen_Tablet (Kitchen)"

emulator -avd KS_Manager_Phone -no-snapshot-load -no-snapshot-save -no-audio -memory 512 &
echo "  Started: KS_Manager_Phone (Supervisor)"

echo ""
echo "Waiting for all 3 to boot..."
sleep 35

echo "Launching Kitchen Sync app on all 3..."
for d in $(adb devices | grep -w "device" | awk '{print $1}'); do
    adb -s "$d" shell am start -n com.kitchensync/.ui.roleselection.RoleSelectionActivity > /dev/null 2>&1
    model=$(adb -s "$d" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    echo "  Launched on $model ($d)"
done

echo ""
echo "All 3 emulators ready. Select roles and start the demo."
