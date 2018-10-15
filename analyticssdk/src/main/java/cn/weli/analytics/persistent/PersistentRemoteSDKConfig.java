package cn.weli.analytics.persistent;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class PersistentRemoteSDKConfig extends PersistentIdentity<String> {
    public PersistentRemoteSDKConfig(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences, "analyticsdata_sdk_configuration", new PersistentSerializer<String>() {
            @Override
            public String load(String value) {
                return value;
            }

            @Override
            public String save(String item) {
                return item;
            }

            @Override
            public String create() {
                return null;
            }
        });
    }
}
