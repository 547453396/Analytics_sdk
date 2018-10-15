package cn.weli.analytics.persistent;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class PersistentLoginId extends PersistentIdentity<String> {
    public PersistentLoginId(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences, "events_login_id", new PersistentSerializer<String>() {
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
