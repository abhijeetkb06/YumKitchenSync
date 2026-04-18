package com.kitchensync;

import android.app.Application;
import com.couchbase.lite.CouchbaseLite;
import com.kitchensync.data.CouchbaseManager;

/**
 * Application entry point. Initializes Couchbase Lite and pre-warms the database
 * and TLS identity on a background thread so P2P discovery can begin immediately
 * when RoleSelectionActivity launches.
 */
public class KitchenSyncApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CouchbaseLite.init(this);

        // Pre-warm the database and TLS identity on a background thread.
        // This ensures the MultipeerReplicator can start instantly when the
        // user reaches the role selection screen.
        CouchbaseManager manager = CouchbaseManager.getInstance();
        manager.init(this);
        manager.preWarm();
    }
}
