package cn.weli.analytics;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import cn.weli.analytics.utils.LogUtils;

public class AnalyticsDataDBHelper extends SQLiteOpenHelper {
    private static final String TAG = "WELI.AnalyticsSQLiteOpenHelper";
    private static final String KEY_DATA = "data";
    private static final String KEY_CREATED_AT = "created_at";
    private static final int DATABASE_VERSION = 4;
    private static final String CREATE_EVENTS_TABLE =
            "CREATE TABLE " + DbAdapter.Table.EVENTS.getName() + " (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    KEY_DATA + " STRING NOT NULL, " +
                    KEY_CREATED_AT + " INTEGER NOT NULL);";
    private static final String EVENTS_TIME_INDEX =
            "CREATE INDEX IF NOT EXISTS time_idx ON " + DbAdapter.Table.EVENTS.getName() +
                    " (" + KEY_CREATED_AT + ");";

    AnalyticsDataDBHelper(Context context, String dbName) {
        super(context, dbName, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        LogUtils.i(TAG, "Creating a new Analytics DB");

        db.execSQL(CREATE_EVENTS_TABLE);
        db.execSQL(EVENTS_TIME_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LogUtils.i(TAG, "Upgrading app, replacing Analytics DB");

        db.execSQL("DROP TABLE IF EXISTS " + DbAdapter.Table.EVENTS.getName());
        db.execSQL(CREATE_EVENTS_TABLE);
        db.execSQL(EVENTS_TIME_INDEX);
    }
}
