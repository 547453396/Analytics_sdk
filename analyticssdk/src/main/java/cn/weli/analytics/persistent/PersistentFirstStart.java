package cn.weli.analytics.persistent;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class PersistentFirstStart extends PersistentIdentity<Boolean> {
    public PersistentFirstStart(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences, "first_start", new PersistentSerializer<Boolean>() {
            @Override
            public Boolean load(String value) {
                return false;
            }

            @Override
            public String save(Boolean item) {
                return String.valueOf(true);
            }

            @Override
            public Boolean create() {
                return true;
            }
        });
    }
}
