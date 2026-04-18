package com.kitchensync.ui.roleselection;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.kitchensync.R;
import com.kitchensync.data.CouchbaseManager;
import com.kitchensync.data.model.DeviceRole;
import com.kitchensync.data.repository.MenuRepository;
import com.kitchensync.ui.main.MainActivity;
import com.kitchensync.util.Constants;
import com.kitchensync.util.PermissionHelper;

/**
 * Launcher activity that presents the three QSR device roles (Kiosk, Kitchen, Store Manager).
 *
 * On creation, this activity:
 * 1. Ensures the Couchbase Lite database is open and menu items are seeded
 * 2. Starts MultipeerReplicator BEFORE the user selects a role, giving DNS-SD
 *    time to discover peers while the user is still on this screen
 * 3. Handles runtime permission requests (location, nearby WiFi devices)
 *
 * Once a role is selected, it writes the device document and launches MainActivity.
 */
public class RoleSelectionActivity extends AppCompatActivity {
    private static final String TAG = "RoleSelection";

    private DeviceRole pendingRole;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_role_selection);

        initDatabaseAndStartSync();
        setupRoleCards();
    }

    private void initDatabaseAndStartSync() {
        try {
            CouchbaseManager manager = CouchbaseManager.getInstance();
            // init() + preWarm() already called in KitchenSyncApp.onCreate(),
            // but ensure DB is open on main thread if pre-warm hasn't finished yet
            if (!manager.isDatabaseReady()) {
                manager.init(this);
                manager.openDatabase();
            }
            new MenuRepository().seedMenuIfNeeded();
            Log.i(TAG, "Database initialized and menu seeded");

            // Start P2P sync IMMEDIATELY -- before the user picks a role.
            // This gives DNS-SD time to discover peers while the user is
            // still looking at the role selection screen.
            manager.startPeerSyncEarly();
            Log.i(TAG, "P2P sync started early -- discovering peers while user selects role");

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize database or start sync", e);
            Toast.makeText(this, "Startup error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void setupRoleCards() {
        MaterialCardView kioskCard = findViewById(R.id.card_kiosk);
        MaterialCardView kitchenCard = findViewById(R.id.card_kitchen);
        MaterialCardView storeManagerCard = findViewById(R.id.card_store_manager);

        kioskCard.setOnClickListener(v -> onRoleSelected(DeviceRole.KIOSK));
        kitchenCard.setOnClickListener(v -> onRoleSelected(DeviceRole.KITCHEN));
        storeManagerCard.setOnClickListener(v -> onRoleSelected(DeviceRole.STORE_MANAGER));
    }

    private void onRoleSelected(DeviceRole role) {
        this.pendingRole = role;
        if (PermissionHelper.hasAllPermissions(this)) {
            launchWithRole(role);
        } else {
            PermissionHelper.requestPermissions(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.REQUEST_CODE_PERMISSIONS) {
            if (PermissionHelper.allGranted(grantResults)) {
                if (pendingRole != null) {
                    launchWithRole(pendingRole);
                }
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.permissions_title)
                        .setMessage(R.string.permissions_message)
                        .setPositiveButton(R.string.grant_permissions, (d, w) -> {
                            PermissionHelper.requestPermissions(this);
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        }
    }

    private void launchWithRole(DeviceRole role) {
        // P2P sync is already running from initDatabaseAndStartSync().
        // Just register the device document with the selected role.
        try {
            String deviceName = Build.MODEL + " - " + role.getDisplayName();
            CouchbaseManager.getInstance().registerDeviceRole(deviceName, role);
        } catch (Exception e) {
            Log.e(TAG, "Failed to register device role", e);
            Toast.makeText(this, "Role registration error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(Constants.EXTRA_ROLE, role.name());
        startActivity(intent);
        finish();
    }
}
