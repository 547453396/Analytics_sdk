package cn.weli.analytics;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import cn.weli.analytics.exceptions.InvalidDataException;
import cn.weli.analytics.persistent.PersistentFirstDay;
import cn.weli.analytics.persistent.PersistentFirstStart;
import cn.weli.analytics.persistent.PersistentSessionId;
import cn.weli.analytics.utils.AnalyticsDataUtils;
import cn.weli.analytics.utils.LogUtils;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class AnalyticsDataActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "WELI.AnalyticsLifecycleCallbacks";
    private static final SimpleDateFormat mIsFirstDayDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private boolean resumeFromBackground = false;
    private Integer startedActivityCount = 0;
    private final Object mActivityLifecycleCallbacksLock = new Object();
    private final AnalyticsDataAPI mAnalyticsDataInstance;
    private final PersistentFirstStart mFirstStart;
    private final PersistentFirstDay mFirstDay;
    private final PersistentSessionId mSessionId;
    private final String mMainProcessName;

    public AnalyticsDataActivityLifecycleCallbacks(AnalyticsDataAPI instance, PersistentFirstStart firstStart, PersistentFirstDay firstDay, PersistentSessionId sessionId,String mainProcessName) {
        this.mAnalyticsDataInstance = instance;
        this.mFirstStart = firstStart;
        this.mFirstDay = firstDay;
        this.mSessionId = sessionId;
        this.mMainProcessName = mainProcessName;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        try {
            synchronized (mActivityLifecycleCallbacksLock) {
                if (startedActivityCount == 0) {
                    if (mFirstDay.get() == null) {
                        mFirstDay.commit(mIsFirstDayDateFormat.format(System.currentTimeMillis()));
                    }
                    mSessionId.commit(UUID.randomUUID().toString());

                    // XXX: 注意内部执行顺序
                    boolean firstStart = mFirstStart.get();

                    try {
                        mAnalyticsDataInstance.appBecomeActive();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    //从后台恢复，从缓存中读取 SDK 控制配置信息
                    if (resumeFromBackground) {
                        //先从缓存中读取 SDKConfig
//                        mAnalyticsDataInstance.applySDKConfigFromCache();
                        mAnalyticsDataInstance.resumeTrackScreenOrientation();
                        mAnalyticsDataInstance.resume3D();
                    }
                    //每次启动 App，重新拉取最新的配置信息
//                    mAnalyticsDataInstance.pullSDKConfigFromServer();

                    if (AnalyticsDataUtils.isMainProcess(activity, mMainProcessName)) {
                        if (mAnalyticsDataInstance.isAutoTrackEnabled()) {
                            try {
                                if (!mAnalyticsDataInstance.isAutoTrackEventTypeIgnored(AnalyticsDataAPI.AutoTrackEventType.APP_START)) {
                                    if (firstStart) {
                                        mFirstStart.commit(false);
                                    }
                                    JSONObject properties = new JSONObject();
                                    JSONObject args = new JSONObject();
                                    args.put(FieldConstant.RESUME_FROM_BACKGROUND, resumeFromBackground);
                                    args.put(FieldConstant.IS_FIRST_TIME, firstStart);
                                    properties.put(FieldConstant.ARGS,args);
                                    AnalyticsDataUtils.getScreenNameAndTitleFromActivity(properties, activity);

                                    AnalyticsDataAPI.sharedInstance(activity).track(EventName.APP_START.getEventName(), properties);
                                }

                                if (!mAnalyticsDataInstance.isAutoTrackEventTypeIgnored(AnalyticsDataAPI.AutoTrackEventType.APP_END)) {
                                    AnalyticsDataAPI.sharedInstance(activity).trackTimerBegin(EventName.APP_END.getEventName());
                                }
                            } catch (Exception e) {
                                LogUtils.i(TAG, e);
                            }
                        }
                        uploadAppsEvent(activity);
                        // 下次启动时，从后台恢复
                        resumeFromBackground = true;
                    }
                    try {
                        mAnalyticsDataInstance.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                startedActivityCount = startedActivityCount + 1;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        try {
            boolean mShowAutoTrack = true;
            if (mAnalyticsDataInstance.isActivityAutoTrackAppViewScreenIgnored(activity.getClass())) {
                mShowAutoTrack = false;
            }

            if (mAnalyticsDataInstance.isAutoTrackEnabled() && mShowAutoTrack && !mAnalyticsDataInstance.isAutoTrackEventTypeIgnored(AnalyticsDataAPI.AutoTrackEventType.APP_VIEW_SCREEN)) {
                try {
                    JSONObject properties = new JSONObject();
                    AnalyticsDataUtils.getScreenNameAndTitleFromActivity(properties, activity);

                    if (activity instanceof ScreenAutoTracker) {
                        ScreenAutoTracker screenAutoTracker = (ScreenAutoTracker) activity;

                        String screenUrl = screenAutoTracker.getScreenUrl();
                        JSONObject otherProperties = screenAutoTracker.getTrackProperties();
                        if (otherProperties != null) {
                            AnalyticsDataUtils.mergeJSONObject(otherProperties, properties);
                            AnalyticsDataAPI.sharedInstance(activity).trackViewScreen(screenUrl, properties);
                        }

                    } else {
                        AnalyticsDataAutoTrackAppViewScreenUrl autoTrackAppViewScreenUrl = activity.getClass().getAnnotation(AnalyticsDataAutoTrackAppViewScreenUrl.class);
                        if (autoTrackAppViewScreenUrl != null) {
                            String screenUrl = autoTrackAppViewScreenUrl.url();
                            if (TextUtils.isEmpty(screenUrl)) {
                                screenUrl = activity.getClass().getCanonicalName();
                            }
                            AnalyticsDataAPI.sharedInstance(activity).trackViewScreen(screenUrl, properties);
                        } else {
                            AnalyticsDataAPI.sharedInstance(activity).track(EventName.PAGE_VIEW_START.getEventName(), properties);
                        }
                    }
                    AnalyticsDataAPI.sharedInstance(activity).trackTimerBegin(EventName.PAGE_VIEW_END.getEventName());
                } catch (Exception e) {
                    LogUtils.i(TAG, e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        try {
            boolean mShowAutoTrack = true;
            if (mAnalyticsDataInstance.isActivityAutoTrackAppViewScreenIgnored(activity.getClass())) {
                mShowAutoTrack = false;
            }

            if (mAnalyticsDataInstance.isAutoTrackEnabled() && mShowAutoTrack && !mAnalyticsDataInstance.isAutoTrackEventTypeIgnored(AnalyticsDataAPI.AutoTrackEventType.APP_VIEW_SCREEN)) {
                try {
                    JSONObject properties = new JSONObject();
                    AnalyticsDataUtils.getScreenNameAndTitleFromActivity(properties, activity);
                    if (activity instanceof ScreenAutoTracker) {
                        ScreenAutoTracker screenAutoTracker = (ScreenAutoTracker) activity;
                        JSONObject otherProperties = screenAutoTracker.getTrackProperties();
                        if (otherProperties != null) {
                            AnalyticsDataUtils.mergeJSONObject(otherProperties, properties);
                        }
                    }
                    AnalyticsDataAPI.sharedInstance(activity).trackTimerEnd(EventName.PAGE_VIEW_END.getEventName(), properties);
                } catch (Exception e) {
                    LogUtils.i(TAG, e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        try {
            synchronized (mActivityLifecycleCallbacksLock) {
                startedActivityCount = startedActivityCount - 1;

                if (startedActivityCount == 0) {
                    if (AnalyticsDataUtils.isMainProcess(activity, mMainProcessName)) {
                        if (mAnalyticsDataInstance.isAutoTrackEnabled()) {
                            try {
                                if (!mAnalyticsDataInstance.isAutoTrackEventTypeIgnored(AnalyticsDataAPI.AutoTrackEventType.APP_END)) {
                                    JSONObject properties = new JSONObject();
                                    AnalyticsDataUtils.getScreenNameAndTitleFromActivity(properties, activity);
                                    mAnalyticsDataInstance.clearLastScreenUrl();
                                    AnalyticsDataAPI.sharedInstance(activity).trackTimerEnd(EventName.APP_END.getEventName(), properties);
                                }
                            } catch (Exception e) {
                                LogUtils.i(TAG, e);
                            }
                        }
                    }

                    try {
                        mAnalyticsDataInstance.stopTrackScreenOrientation();
//                        mAnalyticsDataInstance.resetPullSDKConfigTimer();
                        mAnalyticsDataInstance.appEnterBackground();
                        mAnalyticsDataInstance.stop3D();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    try {
                        mAnalyticsDataInstance.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    /**
     * 安装列表采集
     * @param context
     */
    private void uploadAppsEvent(Context context) {
        try {
            if ((System.currentTimeMillis() - AnalyticsDataUtils.getInstallAppIntervalTime(context)
                    < AnalyticsDataAPI.sharedInstance(context).getInstallAppInterval())) {
                return;
            }
            //安装列表
            ArrayList<JSONArray> installedArrayList = AnalyticsDataUtils.getInstalledApps(context);
            if (installedArrayList != null && installedArrayList.size() > 0) {
                for (int i = 0; i < installedArrayList.size(); i++) {
                    JSONArray installedApps = installedArrayList.get(i);
                    if (installedApps != null && installedApps.length() > 0) {
                        JSONObject properties = new JSONObject();
                        try {
                            JSONObject installed_args = new JSONObject();
                            installed_args.put("install_pkg_list", installedApps);

                            properties.put(FieldConstant.ARGS,installed_args);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        try {
                            AnalyticsDataAPI.sharedInstance(context).trackEvent(EventType.SCANNER,EventName.APP_INSTALL_SCAN.getEventName(),properties);
                        } catch (InvalidDataException e) {
                            e.printStackTrace();
                        }
                    }
                }
                AnalyticsDataUtils.setInstallAppIntervalTime(context,System.currentTimeMillis());
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

}
