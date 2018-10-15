package cn.weli.analytics.persistent;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@SuppressLint("CommitPrefEdits")
/* package */ public abstract class PersistentIdentity<T> {

    public interface PersistentSerializer<T> {
        T load(final String value);

        String save(T item);

        T create();
    }

    public PersistentIdentity(final Future<SharedPreferences> loadStoredPreferences, final String
            persistentKey, final PersistentSerializer<T> serializer) {
        this.loadStoredPreferences = loadStoredPreferences;
        this.serializer = serializer;
        this.persistentKey = persistentKey;
    }

    public T get() {
        if (this.item == null) {
            String data = null;
            synchronized (loadStoredPreferences) {
                try {
                    SharedPreferences sharedPreferences = loadStoredPreferences.get();
                    if (sharedPreferences != null) {
                        data = sharedPreferences.getString(persistentKey, null);
                    }
                } catch (final ExecutionException e) {
                    Log.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e.getCause());
                } catch (final InterruptedException e) {
                    Log.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e);
                }

                T item = null;
                if (data == null) {
                    item = (T) serializer.create();
                } else {
                    item = (T) serializer.load(data);
                }

                if (item != null) {
                    commit(item);
                }
            }
        }
        return this.item;
    }

    public void commit(T item) {
        this.item = item;

        synchronized (loadStoredPreferences) {
            SharedPreferences sharedPreferences = null;
            try {
                sharedPreferences = loadStoredPreferences.get();
            } catch (final ExecutionException e) {
                Log.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e.getCause());
            } catch (final InterruptedException e) {
                Log.e(LOGTAG, "Cannot read distinct ids from sharedPreferences.", e);
            }

            if (sharedPreferences == null) {
                return;
            }

            final SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(persistentKey, serializer.save(this.item));

            editor.apply();
        }
    }

    private static final String LOGTAG = "WELI.PersistentIdentity";

    private final Future<SharedPreferences> loadStoredPreferences;
    private final PersistentSerializer serializer;
    private final String persistentKey;

    private T item;
}
