package com.yumkitchen.sync;

import android.app.Application;
import com.couchbase.lite.CouchbaseLite;

public class YumKitchenApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CouchbaseLite.init(this);
    }
}
