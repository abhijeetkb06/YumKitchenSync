package com.kitchensync;

import android.app.Application;
import com.couchbase.lite.CouchbaseLite;

public class KitchenSyncApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CouchbaseLite.init(this);
    }
}
