package com.kitchensync.util;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for managing runtime permissions required by MultipeerReplicator's
 * DNS-SD discovery. On Android 13+ this includes NEARBY_WIFI_DEVICES; on
 * Android 10-12 it falls back to fine/coarse location permissions.
 */
public class PermissionHelper {

    public static final int REQUEST_CODE_PERMISSIONS = 1001;

    public static String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add("android.permission.NEARBY_WIFI_DEVICES");
        }
        permissions.add("android.permission.ACCESS_FINE_LOCATION");
        permissions.add("android.permission.ACCESS_COARSE_LOCATION");
        return permissions.toArray(new String[0]);
    }

    public static boolean hasAllPermissions(Activity activity) {
        for (String perm : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(activity, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void requestPermissions(Activity activity) {
        String[] perms = getRequiredPermissions();
        ActivityCompat.requestPermissions(activity, perms, REQUEST_CODE_PERMISSIONS);
    }

    public static boolean allGranted(int[] grantResults) {
        if (grantResults.length == 0) return false;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }
}
