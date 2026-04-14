package com.kitchensync.data;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

public class PeerEventBus {

    public interface PeerEventListener {
        default void onPeerDiscovered(String peerId, boolean isOnline) {}
        default void onReplicatorStatusChanged(boolean isActive, String error) {}
        default void onPeerReplicatorStatusChanged(String peerId, boolean isOutgoing, String activity, String error) {}
        default void onDocumentSynced(String peerId, boolean isPush, int docCount) {}
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
}
