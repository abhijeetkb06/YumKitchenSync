package com.yumkitchen.sync.ui.main;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.yumkitchen.sync.R;
import com.yumkitchen.sync.data.CouchbaseManager;
import com.yumkitchen.sync.data.PeerEventBus;
import com.yumkitchen.sync.data.model.DeviceRole;
import com.yumkitchen.sync.data.repository.OrderRepository;
import com.yumkitchen.sync.ui.discovery.PeerDiscoveryFragment;
import com.yumkitchen.sync.ui.kitchen.KitchenDisplayFragment;
import com.yumkitchen.sync.ui.manager.ManagerDashboardFragment;
import com.yumkitchen.sync.ui.waiter.WaiterMenuFragment;
import com.yumkitchen.sync.util.Constants;

public class MainActivity extends AppCompatActivity implements PeerEventBus.PeerEventListener {

    private DeviceRole currentRole;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNav;
    private WaiterMenuFragment waiterFragment;
    private KitchenDisplayFragment kitchenFragment;
    private ManagerDashboardFragment managerFragment;
    private PeerDiscoveryFragment peerFragment;
    private int peerCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String roleName = getIntent().getStringExtra(Constants.EXTRA_ROLE);
        currentRole = roleName != null ? DeviceRole.valueOf(roleName) : DeviceRole.WAITER;

        setupToolbar();
        setupBottomNav();

        PeerEventBus.getInstance().register(this);
    }

    @Override
    protected void onDestroy() {
        PeerEventBus.getInstance().unregister(this);
        CouchbaseManager.getInstance().close();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_reset) {
            showResetDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(R.string.app_name);
        updateSubtitle();
    }

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_waiter) {
                showFragment(getWaiterFragment());
                return true;
            } else if (id == R.id.nav_kitchen) {
                showFragment(getKitchenFragment());
                return true;
            } else if (id == R.id.nav_manager) {
                showFragment(getManagerFragment());
                return true;
            } else if (id == R.id.nav_peers) {
                showFragment(getPeerFragment());
                return true;
            }
            return false;
        });

        // Select the tab matching the initial role
        switch (currentRole) {
            case WAITER:
                bottomNav.setSelectedItemId(R.id.nav_waiter);
                break;
            case KITCHEN:
                bottomNav.setSelectedItemId(R.id.nav_kitchen);
                break;
            case MANAGER:
                bottomNav.setSelectedItemId(R.id.nav_manager);
                break;
        }
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private WaiterMenuFragment getWaiterFragment() {
        if (waiterFragment == null) waiterFragment = new WaiterMenuFragment();
        return waiterFragment;
    }

    private KitchenDisplayFragment getKitchenFragment() {
        if (kitchenFragment == null) kitchenFragment = new KitchenDisplayFragment();
        return kitchenFragment;
    }

    private ManagerDashboardFragment getManagerFragment() {
        if (managerFragment == null) managerFragment = new ManagerDashboardFragment();
        return managerFragment;
    }

    private PeerDiscoveryFragment getPeerFragment() {
        if (peerFragment == null) peerFragment = new PeerDiscoveryFragment();
        return peerFragment;
    }

    private void updateSubtitle() {
        String subtitle = currentRole.getDisplayName();
        if (peerCount > 0) {
            subtitle += " \u00B7 " + peerCount + " peer" + (peerCount != 1 ? "s" : "") + " connected";
        }
        toolbar.setSubtitle(subtitle);
    }

    private void showResetDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.reset_demo)
                .setMessage("This will delete all orders and restart. Continue?")
                .setPositiveButton("Reset", (d, w) -> {
                    try {
                        new OrderRepository().deleteAllOrders();
                        Toast.makeText(this, "Demo reset complete", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Reset failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // PeerEventBus.PeerEventListener
    @Override
    public void onPeerDiscovered(String peerId, boolean isOnline) {
        peerCount = CouchbaseManager.getInstance().getNeighborPeers().size();
        updateSubtitle();
    }
}
