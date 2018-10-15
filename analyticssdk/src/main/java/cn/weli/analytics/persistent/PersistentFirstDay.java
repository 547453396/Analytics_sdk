package cn.weli.analytics.persistent;

import android.content.SharedPreferences;

import java.util.concurrent.Future;

public class PersistentFirstDay extends PersistentIdentity<String> {
    public PersistentFirstDay(Future<SharedPreferences> loadStoredPreferences) {
        super(loadStoredPreferences, "first_day", new PersistentSerializer<String>() {
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
