package cn.weli.analytics.utils;

import android.util.Log;

import cn.weli.analytics.AnalyticsDataAPI;

public class LogUtils {
    private static AnalyticsDataAPI mAnalyticsDataAPI;

    private LogUtils() {

    }

    public static void init(AnalyticsDataAPI analyticsDataAPI) {
        mAnalyticsDataAPI = analyticsDataAPI;
    }

    public static void d(String tag, String msg) {
        try {
            if (mAnalyticsDataAPI.isDebugMode()) {
                Log.i(tag, msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void d(String tag, String msg, Throwable tr) {
        try {
            if (mAnalyticsDataAPI.isDebugMode()) {
                Log.i(tag, msg, tr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void i(String tag, String msg) {
        try {
            if (AnalyticsDataAPI.ENABLE_LOG) {
                Log.i(tag, msg);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void i(String tag, Throwable tr) {
        try {
            if (AnalyticsDataAPI.ENABLE_LOG) {
                Log.i(tag, "", tr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void i(String tag, String msg, Throwable tr) {
        try {
            if (AnalyticsDataAPI.ENABLE_LOG) {
                Log.i(tag, msg, tr);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
