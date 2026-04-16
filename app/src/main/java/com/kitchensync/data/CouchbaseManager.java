package com.kitchensync.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class CouchbaseManager {
    private static final String TAG = "CouchbaseManager";
    private static final String PREFS_NAME = "kitchensync_prefs";
    private static final String PREF_DEVICE_UUID = "device_uuid";
    private static final long DISCOVERY_BOOST_DELAY_MS = 3000;
    private static final int MAX_BOOST_ATTEMPTS = 2;

    private static CouchbaseManager instance;

    private Context context;
    private Database database;
    private Collection collection;
    private MultipeerReplicator replicator;
    private final List<ListenerToken> listenerTokens = new ArrayList<>();
    private String currentPeerId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean peerFound = new AtomicBoolean(false);
    private int boostAttempts = 0;
    private TLSIdentity cachedIdentity;
    private String persistentDeviceUuid;

    private CouchbaseManager() {}

    public static synchronized CouchbaseManager getInstance() {
        if (instance == null) {
            instance = new CouchbaseManager();
        }
        return instance;
    }

    public void init(Context appContext) {
        this.context = appContext.getApplicationContext();
        // Load or create persistent device UUID
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        persistentDeviceUuid = prefs.getString(PREF_DEVICE_UUID, null);
        if (persistentDeviceUuid == null) {
            persistentDeviceUuid = UUID.randomUUID().toString().substring(0, 8);
            prefs.edit().putString(PREF_DEVICE_UUID, persistentDeviceUuid).apply();
        }
    }

    /**
     * Pre-warm: open DB + create TLS identity on a background thread.
     * Called from Application.onCreate() so everything is ready before
     * RoleSelectionActivity appears.
     */
    public void preWarm() {
        executor.execute(() -> {
            try {
                long start = System.currentTimeMillis();
                openDatabase();
                cachedIdentity = getOrCreateIdentity();
                long elapsed = System.currentTimeMillis() - start;
                Log.i(TAG, "Pre-warm complete in " + elapsed + "ms (DB + TLS identity ready)");
            } catch (Exception e) {
                Log.e(TAG, "Pre-warm failed", e);
            }
        });
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

    /**
     * Start P2P sync immediately (called before role selection).
     * Discovery begins right away so peers find each other while
     * the user is still on the role selection screen.
     */
    public void startPeerSyncEarly() throws Exception {
        if (replicator != null) {
            Log.w(TAG, "Peer sync already running");
            return;
        }

        // Use cached identity from pre-warm, or create one now
        TLSIdentity identity = cachedIdentity != null ? cachedIdentity : getOrCreateIdentity();

        // Collection configuration
        MultipeerCollectionConfiguration colConfig =
                new MultipeerCollectionConfiguration.Builder(collection).build();
        Set<MultipeerCollectionConfiguration> collections = new HashSet<>();
        collections.add(colConfig);

        // Authenticator (accept-all for demo)
        MultipeerCertificateAuthenticator authenticator =
                new MultipeerCertificateAuthenticator((peer, certs) -> true);

        // Replicator configuration
        MultipeerReplicatorConfiguration config = new MultipeerReplicatorConfiguration.Builder()
                .setPeerGroupID(Constants.PEER_GROUP_ID)
                .setIdentity(identity)
                .setAuthenticator(authenticator)
                .setCollections(collections)
                .build();

        // Create and start -- auto-discovery begins immediately
        replicator = new MultipeerReplicator(config);
        registerListeners();
        replicator.start();

        PeerInfo.PeerId peerId = replicator.getPeerId();
        currentPeerId = peerId != null ? peerId.toString() : "unknown";
        Log.i(TAG, "MultipeerReplicator started EARLY. PeerId: " + currentPeerId);

        // Schedule discovery boost: if no peers found in 3s, restart to force fresh mDNS
        peerFound.set(false);
        boostAttempts = 0;
        scheduleDiscoveryBoost();
    }

    /**
     * Write device document after role selection.
     * P2P sync is already running from startPeerSyncEarly().
     */
    public void registerDeviceRole(String deviceName, DeviceRole role) {
        writeDeviceDocument(deviceName, role);
    }

    /**
     * Legacy method - starts P2P sync with role (used if early start wasn't called).
     */
    public void startPeerSync(String deviceName, DeviceRole role) throws Exception {
        startPeerSyncEarly();
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

    public boolean isDatabaseReady() {
        return database != null && collection != null;
    }

    // --- Private helpers ---

    private TLSIdentity getOrCreateIdentity() throws CouchbaseLiteException {
        // Use persistent UUID so the same device keeps the same identity across restarts.
        // Each emulator/device gets a unique UUID stored in SharedPreferences,
        // avoiding DNS-SD name conflicts without the overhead of recreating certs every launch.
        String suffix = persistentDeviceUuid != null
                ? persistentDeviceUuid
                : UUID.randomUUID().toString().substring(0, 8);

        // Try to reuse existing identity first
        TLSIdentity existing = TLSIdentity.getIdentity(Constants.IDENTITY_LABEL);
        if (existing != null) {
            Log.i(TAG, "Reusing existing TLS identity");
            return existing;
        }

        // Create new identity only if none exists
        Map<String, String> attrs = new HashMap<>();
        attrs.put(TLSIdentity.CERT_ATTRIBUTE_COMMON_NAME,
                "KitchenSync-" + Build.MODEL + "-" + suffix);
        attrs.put(TLSIdentity.CERT_ATTRIBUTE_ORGANIZATION, "Kitchen Sync");

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 1);

        Set<KeyUsage> keyUsages = new HashSet<>();
        keyUsages.add(KeyUsage.CLIENT_AUTH);
        keyUsages.add(KeyUsage.SERVER_AUTH);

        TLSIdentity identity = TLSIdentity.createIdentity(
                keyUsages, attrs, cal.getTime(), Constants.IDENTITY_LABEL);
        Log.i(TAG, "Created TLS identity: KitchenSync-" + Build.MODEL + "-" + suffix);
        return identity;
    }

    /**
     * Discovery boost: if no peers are found within DISCOVERY_BOOST_DELAY_MS,
     * stop and restart the replicator to force a fresh DNS-SD announcement.
     * This handles cases where the initial mDNS advertisement was missed.
     */
    private void scheduleDiscoveryBoost() {
        mainHandler.postDelayed(() -> {
            if (peerFound.get() || replicator == null) {
                Log.i(TAG, "Discovery boost: peers already found, skipping");
                return;
            }

            if (boostAttempts >= MAX_BOOST_ATTEMPTS) {
                Log.i(TAG, "Discovery boost: max attempts reached, relying on normal discovery");
                return;
            }

            boostAttempts++;
            Log.i(TAG, "Discovery boost #" + boostAttempts
                    + ": no peers found after " + DISCOVERY_BOOST_DELAY_MS
                    + "ms, restarting replicator to force fresh mDNS announcement");

            // Stop and restart to force a new DNS-SD advertisement cycle
            executor.execute(() -> {
                try {
                    if (replicator != null) {
                        replicator.stop();
                        Log.i(TAG, "Discovery boost: replicator stopped");
                        // Brief pause to let mDNS clean up
                        Thread.sleep(500);
                        replicator.start();
                        Log.i(TAG, "Discovery boost: replicator restarted");
                        // Schedule another check
                        mainHandler.postDelayed(() -> scheduleDiscoveryBoost(),
                                DISCOVERY_BOOST_DELAY_MS);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Discovery boost failed", e);
                }
            });
        }, DISCOVERY_BOOST_DELAY_MS);
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
            if (online) {
                peerFound.set(true);  // Cancel discovery boost
            }
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
