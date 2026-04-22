package com.kitchensync.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe event bus for P2P replication events.
 *
 * Bridges the gap between Couchbase Lite's replicator callbacks (which fire on
 * background threads) and the UI layer. All events are dispatched to the main
 * thread via a Handler, so listeners can safely update views.
 *
 * Uses the observer pattern with {@link PeerEventListener} for loose coupling
 * between CouchbaseManager and the various UI fragments.
 */
public class PeerEventBus {

    /** Listener interface for P2P lifecycle events. All callbacks run on the main thread. */
    public interface PeerEventListener {
        default void onPeerDiscovered(String peerId, boolean isOnline) {}
        default void onReplicatorStatusChanged(boolean isActive, String error) {}
        default void onPeerReplicatorStatusChanged(String peerId, boolean isOutgoing, String activity, String error) {}
        default void onDocumentSynced(String peerId, boolean isPush, int docCount) {}
        default void onNetworkStateChanged(boolean isAvailable) {}
    }

    private static PeerEventBus instance;
    private final CopyOnWriteArrayList<PeerEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PeerEventBus() {}

    public static synchronized PeerEventBus getInstance() {
        if (instance == null) {
            instance = new PeerEventBus();
        }
        return instance;
    }

    public void register(PeerEventListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregister(PeerEventListener listener) {
        listeners.remove(listener);
    }

    public void firePeerDiscovered(String peerId, boolean isOnline) {
        mainHandler.post(() -> {
            for (PeerEventListener l : listeners) {
                l.onPeerDiscovered(peerId, isOnline);
            }
        });
    }

    public void fireReplicatorStatus(boolean isActive, String error) {
        mainHandler.post(() -> {
            for (PeerEventListener l : listeners) {
                l.onReplicatorStatusChanged(isActive, error);
            }
        });
    }

    public void firePeerReplicatorStatus(String peerId, boolean isOutgoing, String activity, String error) {
        mainHandler.post(() -> {
            for (PeerEventListener l : listeners) {
                l.onPeerReplicatorStatusChanged(peerId, isOutgoing, activity, error);
            }
        });
    }

    public void fireDocumentSynced(String peerId, boolean isPush, int docCount) {
        mainHandler.post(() -> {
            for (PeerEventListener l : listeners) {
                l.onDocumentSynced(peerId, isPush, docCount);
            }
        });
    }

    public void fireNetworkStateChanged(boolean isAvailable) {
        mainHandler.post(() -> {
            for (PeerEventListener l : listeners) {
                l.onNetworkStateChanged(isAvailable);
            }
        });
    }
}
