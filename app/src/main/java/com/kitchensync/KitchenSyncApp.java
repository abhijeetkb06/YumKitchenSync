package com.kitchensync;

import android.app.Application;
import com.couchbase.lite.CouchbaseLite;
import com.kitchensync.data.CouchbaseManager;

public class KitchenSyncApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CouchbaseLite.init(this);

        // Pre-warm: init context, then start DB + TLS identity creation
        // on a background thread so everything is ready before RoleSelectionActivity
        CouchbaseManager manager = CouchbaseManager.getInstance();
        manager.init(this);
        manager.preWarm();
    }
}
