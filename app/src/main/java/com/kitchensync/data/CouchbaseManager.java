package com.kitchensync.data;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.couchbase.lite.Collection;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.ListenerToken;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.MutableDocument;
import com.couchbase.lite.MultipeerCertificateAuthenticator;
import com.couchbase.lite.MultipeerCollectionConfiguration;
import com.couchbase.lite.MultipeerReplicator;
import com.couchbase.lite.MultipeerReplicatorConfiguration;
import com.couchbase.lite.PeerInfo;
import com.couchbase.lite.TLSIdentity;
import com.couchbase.lite.KeyUsage;
import com.couchbase.lite.logging.ConsoleLogSink;
import com.couchbase.lite.logging.LogSinks;

import com.kitchensync.data.model.DeviceRole;
import com.kitchensync.util.Constants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CouchbaseManager {
    private static final String TAG = "CouchbaseManager";

    private static CouchbaseManager instance;

    private Context context;
    private Database database;
    private Collection collection;
    private MultipeerReplicator replicator;
    private final List<ListenerToken> listenerTokens = new ArrayList<>();
    private String currentPeerId;

    private CouchbaseManager() {}

    public static synchronized CouchbaseManager getInstance() {
        if (instance == null) {
            instance = new CouchbaseManager();
        }
        return instance;
    }

    public void init(Context appContext) {
        this.context = appContext.getApplicationContext();
    }

    public void openDatabase() throws CouchbaseLiteException {
        if (database != null) return;

        // Enable verbose CBL logging for P2P debugging
        LogSinks.get().setConsole(new ConsoleLogSink(
                LogLevel.VERBOSE, LogDomain.REPLICATOR, LogDomain.NETWORK));

        DatabaseConfiguration config = new DatabaseConfiguration();
        database = new Database(Constants.DATABASE_NAME, config);
        collection = database.getDefaultCollection();
        if (collection == null) {
            throw new CouchbaseLiteException("Failed to get default collection");
        }
        Log.i(TAG, "Database opened: " + Constants.DATABASE_NAME);
    }

    public void startPeerSync(String deviceName, DeviceRole role) throws Exception {
        if (replicator != null) {
            Log.w(TAG, "Peer sync already running");
            return;
        }

        // 1. Get or create TLS Identity
        TLSIdentity identity = getOrCreateIdentity();

        // 2. Collection configuration
        MultipeerCollectionConfiguration colConfig =
                new MultipeerCollectionConfiguration.Builder(collection).build();
        Set<MultipeerCollectionConfiguration> collections = new HashSet<>();
        collections.add(colConfig);

        // 3. Authenticator (accept-all for demo)
        MultipeerCertificateAuthenticator authenticator =
                new MultipeerCertificateAuthenticator((peer, certs) -> true);

        // 4. Replicator configuration
        MultipeerReplicatorConfiguration config = new MultipeerReplicatorConfiguration.Builder()
                .setPeerGroupID(Constants.PEER_GROUP_ID)
                .setIdentity(identity)
                .setAuthenticator(authenticator)
                .setCollections(collections)
                .build();

        // 5. Create and start -- auto-discovery begins immediately
        replicator = new MultipeerReplicator(config);
        registerListeners();
        replicator.start();

        PeerInfo.PeerId peerId = replicator.getPeerId();
        currentPeerId = peerId != null ? peerId.toString() : "unknown";
        Log.i(TAG, "MultipeerReplicator started. PeerId: " + currentPeerId);

        // Write device registration document
        writeDeviceDocument(deviceName, role);
    }

    public void stopPeerSync() {
        for (ListenerToken token : listenerTokens) {
            token.remove();
        }
        listenerTokens.clear();

        if (replicator != null) {
            replicator.stop();
            replicator = null;
            Log.i(TAG, "MultipeerReplicator stopped");
        }
    }

    public void close() {
        stopPeerSync();
        if (database != null) {
            try {
                database.close();
                Log.i(TAG, "Database closed");
            } catch (CouchbaseLiteException e) {
                Log.e(TAG, "Error closing database", e);
            }
            database = null;
            collection = null;
        }
    }

    public Database getDatabase() { return database; }
    public Collection getCollection() { return collection; }
    public String getCurrentPeerId() { return currentPeerId; }
    public MultipeerReplicator getReplicator() { return replicator; }

    public Set<String> getNeighborPeers() {
        Set<String> result = new HashSet<>();
        if (replicator != null) {
            Set<PeerInfo.PeerId> peers = replicator.getNeighborPeers();
            for (PeerInfo.PeerId peer : peers) {
                result.add(peer.toString());
            }
        }
        return result;
    }

    public void resetDemo() throws CouchbaseLiteException {
        if (database != null) {
            database.delete();
            database = null;
            collection = null;
        }
    }

    // --- Private helpers ---

    private TLSIdentity getOrCreateIdentity() throws CouchbaseLiteException {
        // Delete any existing identity to ensure a fresh, unique one per app launch
        // This avoids Bonjour name conflicts when multiple emulators share the same Build.MODEL
        TLSIdentity.deleteIdentity(Constants.IDENTITY_LABEL);

        Map<String, String> attrs = new HashMap<>();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        attrs.put(TLSIdentity.CERT_ATTRIBUTE_COMMON_NAME,
                "KitchenSync-" + Build.MODEL + "-" + uniqueSuffix);
        attrs.put(TLSIdentity.CERT_ATTRIBUTE_ORGANIZATION, "Kitchen Sync");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 1);

        Set<KeyUsage> keyUsages = new HashSet<>();
        keyUsages.add(KeyUsage.CLIENT_AUTH);
        keyUsages.add(KeyUsage.SERVER_AUTH);

        TLSIdentity identity = TLSIdentity.createIdentity(
                keyUsages, attrs, cal.getTime(), Constants.IDENTITY_LABEL);
        Log.i(TAG, "Created TLS identity: KitchenSync-" + Build.MODEL + "-" + uniqueSuffix);
        return identity;
    }

    private void registerListeners() {
        PeerEventBus bus = PeerEventBus.getInstance();

        // Replicator status
        ListenerToken statusToken = replicator.addStatusListener(status -> {
            boolean active = status.isActive();
            String error = status.getError() != null ? status.getError().getMessage() : null;
            Log.i(TAG, "Replicator status: " + (active ? "active" : "inactive") +
                    (error != null ? ", error: " + error : ""));
            bus.fireReplicatorStatus(active, error);
        });
        listenerTokens.add(statusToken);

        // Peer discovery
        ListenerToken discoveryToken = replicator.addPeerDiscoveryStatusListener(status -> {
            String peerId = status.getPeer().toString();
            boolean online = status.isOnline();
            Log.i(TAG, "Peer " + (online ? "discovered" : "lost") + ": " + peerId);
            bus.firePeerDiscovered(peerId, online);
        });
        listenerTokens.add(discoveryToken);

        // Peer replicator status
        ListenerToken peerReplToken = replicator.addPeerReplicatorStatusListener(status -> {
            String peerId = status.getPeerId().toString();
            boolean outgoing = status.isOutgoing();
            String activity = status.getStatus().getActivityLevel().name().toLowerCase();
            String error = status.getStatus().getError() != null
                    ? status.getStatus().getError().getMessage() : null;
            Log.i(TAG, "Peer repl status: " + peerId + " " + activity +
                    (error != null ? " error: " + error : ""));
            bus.firePeerReplicatorStatus(peerId, outgoing, activity, error);
        });
        listenerTokens.add(peerReplToken);

        // Document replication
        ListenerToken docToken = replicator.addPeerDocumentReplicationListener(status -> {
            String peerId = status.getPeer().toString();
            boolean push = status.isPush();
            int count = status.getDocuments().size();
            Log.i(TAG, "Doc sync: " + peerId + " " + (push ? "push" : "pull") + " " + count + " docs");
            bus.fireDocumentSynced(peerId, push, count);
        });
        listenerTokens.add(docToken);
    }

    private void writeDeviceDocument(String deviceName, DeviceRole role) {
        try {
            String docId = "device::" + currentPeerId;
            MutableDocument doc = new MutableDocument(docId);
            doc.setString("type", Constants.DOC_TYPE_DEVICE);
            doc.setString("peerId", currentPeerId);
            doc.setString("deviceName", deviceName);
            doc.setString("role", role.name());
            doc.setString("joinedAt", java.time.Instant.now().toString());
            collection.save(doc);
            Log.i(TAG, "Device document written: " + docId);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, "Error writing device document", e);
        }
    }
}
