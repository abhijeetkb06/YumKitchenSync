package com.kitchensync.ui.discovery;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.couchbase.lite.Collection;
import com.couchbase.lite.Document;
import com.kitchensync.R;
import com.kitchensync.data.CouchbaseManager;
import com.kitchensync.data.PeerEventBus;

/**
 * P2P mesh visualization fragment. Displays the live network topology with an
 * animated mesh view (center node = this device, orbital nodes = peers) and a
 * detailed peer list showing device names, roles, and connection status.
 *
 * Listens to {@link PeerEventBus} for real-time peer discovery, replicator status,
 * and document sync events. Connection lines flash blue on each sync.
 */
public class PeerDiscoveryFragment extends Fragment implements PeerEventBus.PeerEventListener {
    private static final String TAG = "PeerDiscovery";

    private PeerMeshView meshView;
    private PeerListAdapter listAdapter;
    private TextView textStatus;
    private TextView textPeerCount;
    private View statusIndicator;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_peer_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        meshView = view.findViewById(R.id.peer_mesh_view);
        textStatus = view.findViewById(R.id.text_status);
        textPeerCount = view.findViewById(R.id.text_peer_count);
        statusIndicator = view.findViewById(R.id.status_indicator);

        RecyclerView recyclerPeers = view.findViewById(R.id.recycler_peers);
        listAdapter = new PeerListAdapter();
        recyclerPeers.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerPeers.setAdapter(listAdapter);

        meshView.setDeviceInfo("This Device", "");

        // Load existing peers
        loadExistingPeers();
    }

    @Override
    public void onResume() {
        super.onResume();
        PeerEventBus.getInstance().register(this);
        // Refresh all peer info in case device docs changed while we were paused
        refreshAllPeers();
        // Update status based on current network state
        if (!CouchbaseManager.getInstance().isNetworkAvailable()) {
            textStatus.setText(getString(R.string.reconnecting));
            setIndicatorColor(R.color.ks_amber);
            meshView.setReconnecting(true);
        }
    }

    @Override
    public void onPause() {
        PeerEventBus.getInstance().unregister(this);
        super.onPause();
    }

    private void loadExistingPeers() {
        try {
            for (String peerId : CouchbaseManager.getInstance().getNeighborPeers()) {
                addPeerToView(peerId, true);
            }
            updateStatusDisplay();
        } catch (Exception e) {
            Log.e(TAG, "Error loading existing peers", e);
        }
    }

    /**
     * Re-reads device documents for all known peers and updates the UI.
     * Called on resume to catch role changes that happened while this
     * fragment was paused (e.g., user was on another tab).
     */
    private void refreshAllPeers() {
        try {
            for (String peerId : CouchbaseManager.getInstance().getNeighborPeers()) {
                addPeerToView(peerId, true);
            }
            updateStatusDisplay();
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing peers", e);
        }
    }

    private void addPeerToView(String peerId, boolean isOnline) {
        // Try to get device info from synced document
        String name = peerId.substring(0, Math.min(8, peerId.length()));
        String role = "Unknown";
        int color = ContextCompat.getColor(requireContext(), R.color.peer_bubble_unknown);

        try {
            Collection collection = CouchbaseManager.getInstance().getCollection();
            if (collection != null) {
                Document deviceDoc = collection.getDocument("device::" + peerId);
                if (deviceDoc != null) {
                    String deviceName = deviceDoc.getString("deviceName");
                    String deviceRole = deviceDoc.getString("role");
                    if (deviceName != null) name = deviceName;
                    if (deviceRole != null) {
                        role = deviceRole;
                        switch (deviceRole) {
                            case "KIOSK":
                                color = ContextCompat.getColor(requireContext(), R.color.ks_amber);
                                break;
                            case "KITCHEN":
                                color = ContextCompat.getColor(requireContext(), R.color.ks_red);
                                break;
                            case "STORE_MANAGER":
                                color = ContextCompat.getColor(requireContext(), R.color.role_store_mgr_start);
                                break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading device doc", e);
        }

        meshView.addPeer(peerId, name, color);
        listAdapter.addOrUpdatePeer(
                new PeerListAdapter.PeerInfo(peerId, name, role, isOnline));
    }

    private void updateStatusDisplay() {
        int count = listAdapter.getPeerCount();
        if (count > 0) {
            textStatus.setText(getString(R.string.replicator_active));
            textPeerCount.setText(count + " peer" + (count != 1 ? "s" : ""));
            setIndicatorColor(R.color.success_green);
        } else {
            textStatus.setText(getString(R.string.searching));
            textPeerCount.setText("");
            setIndicatorColor(R.color.ks_amber);
        }
    }

    private void setIndicatorColor(int colorRes) {
        android.graphics.drawable.GradientDrawable indicator =
                new android.graphics.drawable.GradientDrawable();
        indicator.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        indicator.setColor(ContextCompat.getColor(requireContext(), colorRes));
        statusIndicator.setBackground(indicator);
    }

    // PeerEventBus.PeerEventListener
    @Override
    public void onPeerDiscovered(String peerId, boolean isOnline) {
        if (!isAdded()) return;
        if (isOnline) {
            addPeerToView(peerId, true);
        } else {
            meshView.removePeer(peerId);
            listAdapter.removePeer(peerId);
        }
        updateStatusDisplay();
    }

    @Override
    public void onReplicatorStatusChanged(boolean isActive, String error) {
        if (!isAdded()) return;
        if (isActive) {
            textStatus.setText(getString(R.string.replicator_active));
            setIndicatorColor(R.color.success_green);
            meshView.setReconnecting(false);
        } else {
            // Do NOT clear peers — they stay visible with "reconnecting" status.
            // Individual peers are removed by onPeerReplicatorStatusChanged or
            // refreshed by onPeerDiscovered when the new replicator starts.
            textStatus.setText(getString(R.string.reconnecting));
            setIndicatorColor(R.color.ks_amber);
            listAdapter.updateAllPeerStatus("reconnecting");
            meshView.setReconnecting(true);
        }
    }

    @Override
    public void onPeerReplicatorStatusChanged(String peerId, boolean isOutgoing, String activity, String error) {
        if (!isAdded()) return;
        if ("stopped".equals(activity) && error != null) {
            // Peer replicator stopped with error — connection lost.
            // MultipeerReplicator may not fire onPeerDiscovered(false) on the
            // device that lost the network, so treat this as peer going offline.
            meshView.removePeer(peerId);
            listAdapter.removePeer(peerId);
            updateStatusDisplay();
        } else {
            listAdapter.updatePeerStatus(peerId, activity);
        }
    }

    @Override
    public void onDocumentSynced(String peerId, boolean isPush, int docCount) {
        if (!isAdded()) return;
        meshView.flashSyncLine(peerId);
        // Re-read device document on any sync event to pick up role changes and
        // late-arriving device info (fixes race where discovery fires
        // before the device document has synced)
        addPeerToView(peerId, true);
        updateStatusDisplay();
    }

    @Override
    public void onNetworkStateChanged(boolean isAvailable) {
        if (!isAdded()) return;
        if (!isAvailable) {
            textStatus.setText(getString(R.string.reconnecting));
            setIndicatorColor(R.color.ks_amber);
            listAdapter.updateAllPeerStatus("reconnecting");
            meshView.setReconnecting(true);
        } else {
            textStatus.setText(getString(R.string.searching));
            setIndicatorColor(R.color.ks_amber);
            meshView.setReconnecting(false);
        }
    }
}
