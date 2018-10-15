package cn.weli.analytics;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import cn.weli.analytics.exceptions.InvalidDataException;
import cn.weli.analytics.persistent.PersistentFirstDay;
import cn.weli.analytics.persistent.PersistentFirstStart;
import cn.weli.analytics.persistent.PersistentLoginId;
import cn.weli.analytics.persistent.PersistentSessionId;
import cn.weli.analytics.persistent.PersistentSuperProperties;
import cn.weli.analytics.utils.AnalyticsDataUtils;
import cn.weli.analytics.utils.ChannelUtil;
import cn.weli.analytics.utils.DeviceHelper;
import cn.weli.analytics.utils.JSONUtils;
import cn.weli.analytics.utils.LogUtils;

/**
 * Analytics SDK
 */
public class AnalyticsDataAPI implements IAnalyticsDataAPI {

    /**
     * Debug 模式，用于检验数据导入是否正确。该模式下，事件会逐条实时发送到 Analytics，并根据返回值检查
     * 数据导入是否正确。
     *
     * Debug 模式的具体使用方式，请参考:
     * http://www.sensorsdata.cn/manual/debug_mode.html
     *
     * Debug 模式有三种：
     * DEBUG_OFF - 关闭DEBUG模式
     * DEBUG_ONLY - 打开DEBUG模式，但该模式下发送的数据仅用于调试，不进行数据导入
     * DEBUG_AND_TRACK - 打开DEBUG模式，并将数据导入到Analytics中
     */
    public enum DebugMode {
        DEBUG_OFF(false, false),
        DEBUG_ONLY(true, false),
        DEBUG_AND_TRACK(true, true);

        private final boolean debugMode;
        private final boolean debugWriteData;

        DebugMode(boolean debugMode, boolean debugWriteData) {
            this.debugMode = debugMode;
            this.debugWriteData = debugWriteData;
        }

        boolean isDebugMode() {
            return debugMode;
        }

        boolean isDebugWriteData() {
            return debugWriteData;
        }
    }

    /**
     * 网络类型
     */
    public final class NetworkType {
        public static final int TYPE_NONE = 0;//NULL
        public static final int TYPE_2G = 1;//2G
        public static final int TYPE_3G = 1 << 1;//3G
        public static final int TYPE_4G = 1 << 2;//4G
        public static final int TYPE_WIFI = 1 << 3;//WIFI
        public static final int TYPE_ALL = 0xFF;//ALL
    }

    protected boolean isShouldFlush(String networkType) {
        return (toNetworkType(networkType) & mFlushNetworkPolicy) != 0;
    }

    private int toNetworkType(String networkType) {
        if ("NULL".equals(networkType)) {
            return NetworkType.TYPE_ALL;
        } else if ("WIFI".equals(networkType)) {
            return NetworkType.TYPE_WIFI;
        } else if ("2G".equals(networkType)) {
            return NetworkType.TYPE_2G;
        } else if ("3G".equals(networkType)) {
            return NetworkType.TYPE_3G;
        } else if ("4G".equals(networkType)) {
            return NetworkType.TYPE_4G;
        }
        return NetworkType.TYPE_ALL;
    }

    /**
     * AutoTrack 默认采集的事件类型
     */
    public enum AutoTrackEventType {
        APP_START("$AppStart", 1 << 0),
        APP_END("$AppEnd", 1 << 1),
        APP_CLICK("$AppClick", 1 << 2),
        APP_VIEW_SCREEN("$AppViewScreen", 1 << 3);
        private final String eventName;
        private final int eventValue;

        public static AutoTrackEventType autoTrackEventTypeFromEventName(String eventName) {
            if (TextUtils.isEmpty(eventName)) {
                return null;
            }

            if ("$AppStart".equals(eventName)) {
                return APP_START;
            } else if ("$AppEnd".equals(eventName)) {
                return APP_END;
            } else if ("$AppClick".equals(eventName)) {
                return APP_CLICK;
            } else if ("$AppViewScreen".equals(eventName)) {
                return APP_VIEW_SCREEN;
            }

            return null;
        }

        AutoTrackEventType(String eventName, int eventValue) {
            this.eventName = eventName;
            this.eventValue = eventValue;
        }

        String getEventName() {
            return eventName;
        }

        int getEventValue() {
            return eventValue;
        }
    }

    //private
    AnalyticsDataAPI() {
        mContext = null;
        mMessages = null;
        mLoginId = null;
        mSuperProperties = null;
        mFirstStart = null;
        mFirstDay = null;
        mSessionId = null;
//        mPersistentRemoteSDKConfig = null;
        mDeviceInfo = null;
        mTrackTimer = null;
        mMainProcessName = null;
    }

    AnalyticsDataAPI(Context context, String serverURL,DebugMode debugMode) {
        mContext = context;
        mDebugMode = debugMode;

        final String packageName = context.getApplicationContext().getPackageName();

        mAutoTrackIgnoredActivities = new ArrayList<>();
        mHeatMapActivities = new ArrayList<>();
        mAutoTrackEventTypeList = new ArrayList<>();

        try {
            AnalyticsDataUtils.cleanUserAgent(mContext);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            LogUtils.init(this);
            final ApplicationInfo appInfo = context.getApplicationContext().getPackageManager()
                    .getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            Bundle configBundle = appInfo.metaData;
            if (null == configBundle) {
                configBundle = new Bundle();
            }

            setServerUrl(serverURL);

            if (debugMode == DebugMode.DEBUG_OFF) {
                ENABLE_LOG = configBundle.getBoolean("cn.weli.analytics.android.EnableLogging",
                        false);
            } else {
                ENABLE_LOG = configBundle.getBoolean("cn.weli.analytics.android.EnableLogging",
                        true);
            }

            mFlushInterval = configBundle.getInt("cn.weli.analytics.android.FlushInterval",
                    30000);
            mFlushBulkSize = configBundle.getInt("cn.weli.analytics.android.FlushBulkSize",
                    30);
            mAutoTrack = configBundle.getBoolean("cn.weli.analytics.android.AutoTrack",
                    false);
            mDisableDefaultRemoteConfig = configBundle.getBoolean("cn.weli.analytics.android.DisableDefaultRemoteConfig",
                    false);
            mEnableButterknifeOnClick = configBundle.getBoolean("cn.weli.analytics.android.ButterknifeOnClick",
                    false);
            mMainProcessName = configBundle.getString("cn.weli.analytics.android.MainProcessName");
            mInstallAppInterval = configBundle.getLong("cn.weli.analytics.android.InstallAppInterval",
                    6 * 60 * 60 * 1000);
        } catch (final PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Can't configure AnalyticsDataAPI with package name " + packageName,
                    e);
        }

        mMessages = AnalyticsMessages.getInstance(mContext, packageName);

        final SharedPreferencesLoader.OnPrefsLoadedListener listener =
                new SharedPreferencesLoader.OnPrefsLoadedListener() {
                    @Override
                    public void onPrefsLoaded(SharedPreferences preferences) {
                    }
                };

        final String prefsName = this.getClass().getName();
        final Future<SharedPreferences> storedPreferences =
                sPrefsLoader.loadPreferences(context, prefsName, listener);

        mLoginId = new PersistentLoginId(storedPreferences);
        mSuperProperties = new PersistentSuperProperties(storedPreferences);
        mFirstStart = new PersistentFirstStart(storedPreferences);
//        mPersistentRemoteSDKConfig = new PersistentRemoteSDKConfig(storedPreferences);

        mAnalyticsData3DDetector = new AnalyticsData3DDetector(context);

//        //先从缓存中读取 SDKConfig
//        applySDKConfigFromCache();

        mFirstDay = new PersistentFirstDay(storedPreferences);
        mSessionId = new PersistentSessionId(storedPreferences);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            final Application app = (Application) context.getApplicationContext();
            app.registerActivityLifecycleCallbacks(new AnalyticsDataActivityLifecycleCallbacks(this, mFirstStart, mFirstDay,mSessionId, mMainProcessName));
        }

        if (debugMode != DebugMode.DEBUG_OFF) {
            Log.i(TAG, String.format(Locale.CHINA, "Initialized the instance of Analytics SDK with server"
                            + " url '%s', flush interval %d ms, debugMode: %s", mServerUrl,
                     mFlushInterval, debugMode));
        }

        mDeviceInfo = Collections.unmodifiableMap(initDeviceInfo());
        mTrackTimer = new HashMap<>();

    }

    private Map<String, Object> initDeviceInfo(){
        final Map<String, Object> deviceInfo = new HashMap<>();

        {
            // APP_KEY、PUBLISH
            JSONObject app = new JSONObject();
            try {
                ApplicationInfo ai = mContext.getPackageManager().getApplicationInfo(
                        mContext.getPackageName(), PackageManager.GET_META_DATA);
                String appkey = String.valueOf(ai.metaData.get("APPID"));
                if (!TextUtils.isEmpty(appkey)){
                    app.put(FieldConstant.APP_KEY, appkey);
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            } catch (JSONException e){
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }

            // 应用信息
            try {
                final PackageManager manager = mContext.getPackageManager();
                final PackageInfo info = manager.getPackageInfo(mContext.getPackageName(), 0);
                app.put(FieldConstant.APP_VERSION, info.versionName);
                app.put(FieldConstant.APP_VERSION_CODE, info.versionCode);
                app.put(FieldConstant.CHANNEL, ChannelUtil.getChannel(mContext));
                app.put(FieldConstant.PKG, mContext.getPackageName());
                app.put(FieldConstant.SDK_VERSION, VERSION);
            } catch (final Exception e) {
                LogUtils.i(TAG, "Exception getting app version name", e);
            }
            deviceInfo.put(FieldConstant.APP,app);

            // 设备imei等信息
            deviceInfo.put(FieldConstant.IMEI, DeviceHelper.getImei(mContext));
            deviceInfo.put(FieldConstant.IMSI, DeviceHelper.getImsi(mContext));
            deviceInfo.put(FieldConstant.MAC, DeviceHelper.getMac(mContext));
            deviceInfo.put(FieldConstant.DEVICE_ID, DeviceHelper.getDeviceId(mContext));
            String androidID = AnalyticsDataUtils.getAndroidID(mContext);
            if (!TextUtils.isEmpty(androidID)) {
                deviceInfo.put(FieldConstant.ANDROID_ID, androidID);
            }
            deviceInfo.put(FieldConstant.OS, "Android");
            deviceInfo.put(FieldConstant.OS_VERSION,AnalyticsDataUtils.getSDKVersionName());
            deviceInfo.put(FieldConstant.MODEL, AnalyticsDataUtils.getModel());
            deviceInfo.put(FieldConstant.BRAND, Build.BRAND.trim());
            final DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
            int w , h;
            h = displayMetrics.heightPixels;
            w = displayMetrics.widthPixels;
            //主题不同，高度可能会变，使用getRealSize能够获取到正确的高度
            try {
                WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
                if (Build.VERSION.SDK_INT >= 17) {
                    Point point = new Point();
                    if (windowManager != null) {
                        windowManager.getDefaultDisplay().getRealSize(point);
                        h = point.y;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            deviceInfo.put(FieldConstant.SCREEN_HEIGHT,h);
            deviceInfo.put(FieldConstant.SCREEN_WIDTH,w);
            deviceInfo.put(FieldConstant.LANG, Locale.getDefault().getLanguage() + "-" + Locale.getDefault().getCountry());
            deviceInfo.put(FieldConstant.TIME_ZONE, TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT));
            deviceInfo.put(FieldConstant.PRODUCT,Build.PRODUCT);
            deviceInfo.put(FieldConstant.BOARD,Build.BOARD);
            deviceInfo.put(FieldConstant.HARDWARE,Build.HARDWARE);
            deviceInfo.put(FieldConstant.QEMU,AnalyticsDataUtils.getProperty("ro.kernel.qemu","unknown"));
            BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
            if (bluetooth != null) {
                deviceInfo.put(FieldConstant.HAS_BLUETOOTH,true);
                deviceInfo.put(FieldConstant.DISABLE_BLUETOOTH,AnalyticsDataUtils.isEnabledBluetooth(bluetooth));
            }
            deviceInfo.put(FieldConstant.IS_ROOT,AnalyticsDataUtils.isDeviceRooted());
            deviceInfo.put(FieldConstant.HAS_TEMPERATURE, AnalyticsDataUtils.hasSensorByType(mContext, Sensor.TYPE_AMBIENT_TEMPERATURE));
            deviceInfo.put(FieldConstant.HAS_GPS,AnalyticsDataUtils.isGPSEnabled(mContext));
            Intent battery = mContext.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null){
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);  //当前电量
                int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, 1);      //总电量
                int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                deviceInfo.put(FieldConstant.BATTERY_STATUS,status);
                deviceInfo.put(FieldConstant.BATTERY_LEVEL,level);
                deviceInfo.put(FieldConstant.BATTERY_TEMPERATURE,temp*0.1f);
            }
            enableTrackScreenOrientation(true);
        }
        return deviceInfo;
    }

    /**
     * 获取AnalyticsDataAPI单例
     *
     * @param context App的Context
     * @return AnalyticsDataAPI单例
     */
    public static AnalyticsDataAPI sharedInstance(Context context) {
        if (isSDKDisabled()) {
            return new AnalyticsDataAPIEmptyImplementation();
        }

        if (null == context) {
            return new AnalyticsDataAPIEmptyImplementation();
        }

        synchronized (sInstanceMap) {
            final Context appContext = context.getApplicationContext();
            AnalyticsDataAPI instance = sInstanceMap.get(appContext);

            if (null == instance) {
                Log.i(TAG, "The static method sharedInstance(context, serverURL, configureURL, "
                        + "vtrackServerURL, debugMode) should be called before calling sharedInstance()");
                return new AnalyticsDataAPIEmptyImplementation();
            }
            return instance;
        }
    }

    /**
     * 初始化并获取AnalyticsDataAPI单例（打开可视化埋点功能）
     *
     * @param context         App的Context
     * @param serverURL       用于收集事件的服务地址
     * @param debugMode       Debug模式,
     * @return AnalyticsDataAPI单例
     */
    public static AnalyticsDataAPI sharedInstance(Context context, String serverURL, DebugMode debugMode) {
        if (null == context) {
            return new AnalyticsDataAPIEmptyImplementation();
        }

        synchronized (sInstanceMap) {
            final Context appContext = context.getApplicationContext();

            AnalyticsDataAPI instance = sInstanceMap.get(appContext);
            if (null == instance && ConfigurationChecker.checkBasicConfiguration(appContext)) {
                instance = new AnalyticsDataAPI(appContext, serverURL,
                        debugMode);
                sInstanceMap.put(appContext, instance);
            }

            if (instance != null) {
                return instance;
            } else {
                return new AnalyticsDataAPIEmptyImplementation();
            }
        }
    }

    public static AnalyticsDataAPI sharedInstance() {
        if (isSDKDisabled()) {
            return new AnalyticsDataAPIEmptyImplementation();
        }

        synchronized (sInstanceMap) {
            if (sInstanceMap.size() > 0) {
                Iterator<AnalyticsDataAPI> iterator = sInstanceMap.values().iterator();
                if (iterator.hasNext()) {
                    return iterator.next();
                }
            }
            return new AnalyticsDataAPIEmptyImplementation();
        }
    }

    /**
     * 返回是否关闭了 SDK
     * @return true：关闭；false：没有关闭
     */
    public static boolean isSDKDisabled() {
        if (mSDKRemoteConfig == null) {
            return false;
        }

        return mSDKRemoteConfig.isDisableSDK();
    }

    /**
     * 更新 AnalyticsDataSDKRemoteConfig
     * @param sdkRemoteConfig AnalyticsDataSDKRemoteConfig 在线控制 SDK 的配置
     * @param effectImmediately 是否立即生效
     */
//    private void setSDKRemoteConfig(AnalyticsDataSDKRemoteConfig sdkRemoteConfig, boolean effectImmediately) {
//        try {
//            if (sdkRemoteConfig.isDisableSDK()) {
//                AnalyticsDataSDKRemoteConfig cachedConfig = AnalyticsDataUtils.toSDKRemoteConfig(mPersistentRemoteSDKConfig.get());
//                if (!cachedConfig.isDisableSDK()) {
//                    track("DisableAnalyticsDataSDK");
//                }
//            }
//            mPersistentRemoteSDKConfig.commit(sdkRemoteConfig.toJson().toString());
//            if (effectImmediately) {
//                mSDKRemoteConfig = sdkRemoteConfig;
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

//    protected void pullSDKConfigFromServer() {
//        if (mDisableDefaultRemoteConfig) {
//            return;
//        }
//
//        if (mPullSDKConfigCountDownTimer != null) {
//            mPullSDKConfigCountDownTimer.cancel();
//            mPullSDKConfigCountDownTimer = null;
//        }
//
//        mPullSDKConfigCountDownTimer = new CountDownTimer(120 * 1000, 30 * 1000) {
//            @Override
//            public void onTick(long l) {
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        InputStreamReader in = null;
//                        HttpURLConnection urlConnection = null;
//                        try {
//                            if (TextUtils.isEmpty(mServerUrl)) {
//                                return;
//                            }
//
//                            URL url = null;
//                            String configUrl = null;
//                            int pathPrefix = mServerUrl.lastIndexOf("/");
//                            if (pathPrefix != -1) {
//                                configUrl = mServerUrl.substring(0, pathPrefix);
//                                String configVersion = null;
//                                if (mSDKRemoteConfig != null) {
//                                    configVersion = mSDKRemoteConfig.getV();
//                                }
//
//                                configUrl = configUrl + "/config/Android.conf";
//
//                                if (configVersion != null) {
//                                    configUrl = configUrl + "?v=" + configVersion;
//                                }
//                            }
//
//                            url = new URL(configUrl);
//                            urlConnection = (HttpURLConnection) url.openConnection();
//                            if (urlConnection == null) {
//                                return;
//                            }
//                            int responseCode = urlConnection.getResponseCode();
//
//                            //配置没有更新
//                            if (responseCode == 304) {
//                                resetPullSDKConfigTimer();
//                                return;
//                            }
//
//                            if (responseCode == 200) {
//                                resetPullSDKConfigTimer();
//
//                                in = new InputStreamReader(urlConnection.getInputStream());
//                                BufferedReader bufferedReader = new BufferedReader(in);
//                                StringBuilder result = new StringBuilder();
//                                String data;
//                                while ((data = bufferedReader.readLine()) != null) {
//                                    result.append(data);
//                                }
//                                AnalyticsDataSDKRemoteConfig sdkRemoteConfig = AnalyticsDataUtils.toSDKRemoteConfig(result.toString());
//                                setSDKRemoteConfig(sdkRemoteConfig, false);
//                            }
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        } finally {
//                            try {
//                                if (in != null) {
//                                    in.close();
//                                }
//
//                                if (urlConnection != null) {
//                                    urlConnection.disconnect();
//                                }
//                            } catch (Exception e) {
//                                //ignored
//                            }
//                        }
//                    }
//                }).start();
//            }
//
//            @Override
//            public void onFinish() {
//            }
//        };
//        mPullSDKConfigCountDownTimer.start();
//    }

    /**
     * 每次启动 App 时，最多尝试三次
     */
//    private CountDownTimer mPullSDKConfigCountDownTimer;
//
//    protected void resetPullSDKConfigTimer() {
//        try {
//            if (mPullSDKConfigCountDownTimer != null) {
//                mPullSDKConfigCountDownTimer.cancel();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            mPullSDKConfigCountDownTimer = null;
//        }
//    }

    /**
     * 从本地缓存中读取最新的 SDK 配置信息
     */
//    protected void applySDKConfigFromCache() {
//        try {
//            AnalyticsDataSDKRemoteConfig sdkRemoteConfig = AnalyticsDataUtils.toSDKRemoteConfig(mPersistentRemoteSDKConfig.get());
//
//            if (sdkRemoteConfig == null) {
//                sdkRemoteConfig = new AnalyticsDataSDKRemoteConfig();
//            }
//
//            //关闭 debug 模式
//            if (sdkRemoteConfig.isDisableDebugMode()) {
//                disableDebugMode();
//            }
//
//            //开启关闭 AutoTrack
//            List<AutoTrackEventType> autoTrackEventTypeList = sdkRemoteConfig.getAutoTrackEventTypeList();
//            if (autoTrackEventTypeList != null) {
//                enableAutoTrack(autoTrackEventTypeList);
//            }
//
//            if (sdkRemoteConfig.isDisableSDK()) {
//                try {
//                    flush();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//
//            mSDKRemoteConfig = sdkRemoteConfig;
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    /**
     * 返回预置属性
     * @return JSONObject 预置属性
     */
    @Override
    public JSONObject getPresetProperties() {
        JSONObject properties = new JSONObject();
//        try {
//            properties.put("$app_version", mDeviceInfo.get("$app_version"));
//            properties.put("$lib", "Android");
//            properties.put("$lib_version", VERSION);
//            properties.put("$manufacturer", mDeviceInfo.get("$manufacturer"));
//            properties.put("$model", mDeviceInfo.get("$model"));
//            properties.put("$os", "Android");
//            properties.put("$os_version", mDeviceInfo.get("$os_version"));
//            properties.put("$screen_height", mDeviceInfo.get("$screen_height"));
//            properties.put("$screen_width", mDeviceInfo.get("$screen_width"));
//            String networkType = AnalyticsDataUtils.networkType(mContext);
//            properties.put("$wifi", networkType.equals("WIFI"));
//            properties.put("$network_type", networkType);
//            properties.put("$carrier", mDeviceInfo.get("$carrier"));
//            properties.put("$is_first_day", isFirstDay());
//            properties.put("$device_id", mDeviceInfo.get("$device_id"));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return properties;
    }

    /**
     * 设置当前 serverUrl
     * @param serverUrl 当前 serverUrl
     */
    @Override
    public void setServerUrl(String serverUrl) {
        this.mServerUrl = serverUrl;
//        try {
//            if (TextUtils.isEmpty(serverUrl) || mDebugMode == DebugMode.DEBUG_OFF) {
//                mServerUrl = serverUrl;
//                disableDebugMode();
//            } else {
//                Uri serverURI = Uri.parse(serverUrl);
//
//                int pathPrefix = serverURI.getPath().lastIndexOf('/');
//                if (pathPrefix != -1) {
//                    String newPath = serverURI.getPath().substring(0, pathPrefix) + "/debug";
//
//                    // 将 URI Path 中末尾的部分替换成 '/debug'
//                    mServerUrl = serverURI.buildUpon().path(newPath).build().toString();
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    /**
     * 设置是否开启 log
     * @param enable boolean
     */
    @Override
    public void enableLog(boolean enable) {
        this.ENABLE_LOG = enable;
    }

    @Override
    public long getMaxCacheSize() {
        return mMaxCacheSize;
    }

    /**
     * 设置本地缓存上限值，单位 byte，默认为 32MB：32 * 1024 * 1024
     * @param maxCacheSize 单位 byte
     */
    @Override
    public void setMaxCacheSize(long maxCacheSize) {
        if (maxCacheSize > 0) {
            //防止设置的值太小导致事件丢失
            if (maxCacheSize < 16 * 1024 * 1024) {
                maxCacheSize = 16 * 1024 * 1024;
            }
            this.mMaxCacheSize = maxCacheSize;
        }
    }

    /**
     * 设置 flush 时网络发送策略，默认 3G、4G、WI-FI 环境下都会尝试 flush
     * @param networkType int 网络类型
     */
    @Override
    public void setFlushNetworkPolicy(int networkType) {
        mFlushNetworkPolicy = networkType;
    }

    /**
     * 两次数据发送的最小时间间隔，单位毫秒
     *
     * 默认值为15 * 1000毫秒
     * 在每次调用track、signUp以及profileSet等接口的时候，都会检查如下条件，以判断是否向服务器上传数据:
     *
     * 1. 是否是WIFI/3G/4G网络条件
     * 2. 是否满足发送条件之一:
     * 1) 与上次发送的时间间隔是否大于 flushInterval
     * 2) 本地缓存日志数目是否大于 flushBulkSize
     *
     * 如果满足这两个条件，则向服务器发送一次数据；如果不满足，则把数据加入到队列中，等待下次检查时把整个队列的内
     * 容一并发送。需要注意的是，为了避免占用过多存储，队列最多只缓存20MB数据。
     *
     * @return 返回时间间隔，单位毫秒
     */
    @Override
    public int getFlushInterval() {
        return mFlushInterval;
    }

    /**
     * 设置两次数据发送的最小时间间隔
     *
     * @param flushInterval 时间间隔，单位毫秒
     */
    @Override
    public void setFlushInterval(int flushInterval) {
        if (flushInterval < 5 * 1000) {
            flushInterval = 5 * 1000;
        }
        mFlushInterval = flushInterval;
    }

    /**
     * 返回本地缓存日志的最大条目数
     *
     * 默认值为100条
     * 在每次调用track、signUp以及profileSet等接口的时候，都会检查如下条件，以判断是否向服务器上传数据:
     *
     * 1. 是否是WIFI/3G/4G网络条件
     * 2. 是否满足发送条件之一:
     * 1) 与上次发送的时间间隔是否大于 flushInterval
     * 2) 本地缓存日志数目是否大于 flushBulkSize
     *
     * 如果满足这两个条件，则向服务器发送一次数据；如果不满足，则把数据加入到队列中，等待下次检查时把整个队列的内
     * 容一并发送。需要注意的是，为了避免占用过多存储，队列最多只缓存32MB数据。
     *
     * @return 返回本地缓存日志的最大条目数
     */
    @Override
    public int getFlushBulkSize() {
        return mFlushBulkSize;
    }

    /**
     * 设置本地缓存日志的最大条目数
     *
     * @param flushBulkSize 缓存数目
     */
    @Override
    public void setFlushBulkSize(int flushBulkSize) {
        mFlushBulkSize = flushBulkSize;
    }

    public long getInstallAppInterval(){
        return mInstallAppInterval;
    }

    /**
     * 更新 GPS 位置信息
     * @param latitude 纬度
     * @param longitude 经度
     */
    @Override
    public void setGPSLocation(String city_key, String latitude, String longitude) {
        try {
            if (mGPSLocation == null) {
                mGPSLocation = new AnalyticsDataGPSLocation();
            }

            mGPSLocation.setCityKey(city_key);
            mGPSLocation.setLatitude(latitude);
            mGPSLocation.setLongitude(longitude);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 清楚 GPS 位置信息
     */
    @Override
    public void clearGPSLocation() {
        mGPSLocation = null;
    }

    @Override
    public void enableTrackScreenOrientation(boolean enable) {
        try {
            if (enable) {
                if (mOrientationDetector == null) {
                    mOrientationDetector = new AnalyticsDataScreenOrientationDetector(mContext, SensorManager.SENSOR_DELAY_NORMAL);
                }
                mOrientationDetector.enable();
            } else {
                if (mOrientationDetector != null) {
                    mOrientationDetector.disable();
                    mOrientationDetector = null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void resumeTrackScreenOrientation() {
        try {
            if (mOrientationDetector != null) {
                mOrientationDetector.enable();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopTrackScreenOrientation() {
        try {
            if (mOrientationDetector != null) {
                mOrientationDetector.disable();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getScreenOrientation() {
        try {
            if (mOrientationDetector != null) {
                return mOrientationDetector.getOrientation();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void trackEventFromH5(String eventInfo, boolean enableVerify) {
        try {
            if (TextUtils.isEmpty(eventInfo)) {
                return;
            }

            JSONObject eventObject = new JSONObject(eventInfo);
            if (enableVerify) {
                String serverUrl = eventObject.optString("server_url");
                if (!TextUtils.isEmpty(serverUrl)) {
//                    if (!(new ServerUrl(serverUrl).check(new ServerUrl(mServerUrl)))) {
//                        return;
//                    }
                } else {
                    //防止 H5 集成的 JS SDK 版本太老，没有发 server_url
                    return;
                }
            }
            trackEventFromH5(eventInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected boolean _trackEventFromH5(String eventInfo) {
        try {
            if (TextUtils.isEmpty(eventInfo)) {
                return false;
            }
            JSONObject eventObject = new JSONObject(eventInfo);

            String serverUrl = eventObject.optString("server_url");
            if (!TextUtils.isEmpty(serverUrl)) {
//                if (!(new ServerUrl(serverUrl).check(new ServerUrl(mServerUrl)))) {
//                    return false;
//                }
                trackEventFromH5(eventInfo);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;

    }

    @Override
    public void trackEventFromH5(String eventInfo) {
        try {
            if (TextUtils.isEmpty(eventInfo)) {
                return;
            }

            JSONObject eventObject = new JSONObject(eventInfo);
            String eventType = eventObject.getString(FieldConstant.EVENT_TYPE);
            String eventName = eventObject.optString(FieldConstant.EVENT_NAME);
            trackEvent(eventType,eventName,eventObject);

        } catch (Exception e) {
            //ignore
            e.printStackTrace();
        }
    }

    /**
     * 向WebView注入本地方法, 将distinctId传递给当前的WebView
     *
     * @param webView 当前WebView
     * @param isSupportJellyBean 是否支持API level 16及以下的版本。
     * 因为API level 16及以下的版本, addJavascriptInterface有安全漏洞,请谨慎使用
     */
    @SuppressLint(value = {"SetJavaScriptEnabled", "addJavascriptInterface"})
    @Override
    public void showUpWebView(WebView webView, boolean isSupportJellyBean) {
        showUpWebView(webView, isSupportJellyBean, null);
    }

    @SuppressLint(value = {"SetJavaScriptEnabled", "addJavascriptInterface"})
    @Override
    public void showUpWebView(WebView webView, boolean isSupportJellyBean, boolean enableVerify) {
        showUpWebView(webView, null, isSupportJellyBean, enableVerify);
    }

    @SuppressLint(value = {"SetJavaScriptEnabled", "addJavascriptInterface"})
    @Override
    public void showUpWebView(WebView webView, JSONObject properties, boolean isSupportJellyBean, boolean enableVerify) {
        if (Build.VERSION.SDK_INT < 17 && !isSupportJellyBean) {
            LogUtils.d(TAG, "For applications targeted to API level JELLY_BEAN or below, this feature NOT SUPPORTED");
            return;
        }

        if (webView != null) {
            webView.getSettings().setJavaScriptEnabled(true);
            webView.addJavascriptInterface(new AppWebViewInterface(mContext, properties, enableVerify), "SensorsData_APP_JS_Bridge");
        }
    }

    /**
     * 向WebView注入本地方法, 将distinctId传递给当前的WebView
     *
     * @param webView 当前WebView
     * @param isSupportJellyBean 是否支持API level 16及以下的版本。
     *                           因为API level 16及以下的版本, addJavascriptInterface有安全漏洞,请谨慎使用
     * @param properties 用户自定义属性
     */
    @SuppressLint(value = {"SetJavaScriptEnabled", "addJavascriptInterface"})
    @Override
    public void showUpWebView(WebView webView, boolean isSupportJellyBean, JSONObject properties) {
        showUpWebView(webView, properties, isSupportJellyBean, false);
    }

    @Override
    public void showUpX5WebView(Object x5WebView, boolean enableVerify) {
        try {
            if (x5WebView == null) {
                return;
            }

            Class clazz = x5WebView.getClass();
            Method addJavascriptInterface = clazz.getMethod("addJavascriptInterface", Object.class, String.class);
            if (addJavascriptInterface == null) {
                return;
            }

            addJavascriptInterface.invoke(x5WebView, new AppWebViewInterface(mContext, null, enableVerify), "SensorsData_APP_JS_Bridge");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void showUpX5WebView(Object x5WebView) {
        showUpX5WebView(x5WebView, false);
    }

    public void resume3D() {
        try {
            if (mAnalyticsData3DDetector != null) {
                mAnalyticsData3DDetector.enable();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop3D() {
        try {
            if (mAnalyticsData3DDetector != null) {
                mAnalyticsData3DDetector.disable();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 打开 SDK 自动追踪
     *
     * 该功能自动追踪 App 的一些行为，指定哪些 AutoTrack 事件被追踪，具体信息请参考文档:
     * https://sensorsdata.cn/manual/android_sdk.html
     *
     * 该功能仅在 API 14 及以上版本中生效，默认关闭
     *
     * @param eventTypeList 开启 AutoTrack 的事件列表
     */
    @Override
    public void enableAutoTrack(List<AutoTrackEventType> eventTypeList) {
        mAutoTrack = true;
        if (eventTypeList == null) {
            eventTypeList = new ArrayList<>();
        }

        mAutoTrackEventTypeList.clear();
        mAutoTrackEventTypeList.addAll(eventTypeList);
    }

    /**
     * 关闭 AutoTrack 中的部分事件
     * @param eventTypeList AutoTrackEventType 类型 List
     */
    @Override
    public void disableAutoTrack(List<AutoTrackEventType> eventTypeList) {
        if (eventTypeList == null || eventTypeList.size() == 0) {
            return;
        }

        if (mAutoTrackEventTypeList == null) {
            return;
        }

        for (AutoTrackEventType autoTrackEventType: eventTypeList) {
            if (autoTrackEventType != null) {
                if (mAutoTrackEventTypeList.contains(autoTrackEventType)) {
                    mAutoTrackEventTypeList.remove(autoTrackEventType);
                }
            }
        }

        if (mAutoTrackEventTypeList.size() == 0) {
            mAutoTrack = false;
        }
    }

    /**
     * 关闭 AutoTrack 中的某个事件
     * @param autoTrackEventType AutoTrackEventType 类型
     */
    @Override
    public void disableAutoTrack(AutoTrackEventType autoTrackEventType) {
        if (autoTrackEventType == null) {
            return;
        }

        if (mAutoTrackEventTypeList == null) {
            return;
        }

        if (mAutoTrackEventTypeList.contains(autoTrackEventType)) {
            mAutoTrackEventTypeList.remove(autoTrackEventType);
        }

        if (mAutoTrackEventTypeList.size() == 0) {
            mAutoTrack = false;
        }
    }

    // Package-level access. Used (at least) by GCMReceiver
    // when OS-level events occur.
    /* package */ interface InstanceProcessor {
        public void process(AnalyticsDataAPI m);
    }

    /* package */ static void allInstances(InstanceProcessor processor) {
        synchronized (sInstanceMap) {
            for (final AnalyticsDataAPI instance : sInstanceMap.values()) {
                processor.process(instance);
            }
        }
    }

    /**
     * 是否开启 AutoTrack
     * @return true: 开启 AutoTrack; false：没有开启 AutoTrack
     */
    @Override
    public boolean isAutoTrackEnabled() {
        if (isSDKDisabled()) {
            return false;
        }

        if (mSDKRemoteConfig != null) {
            if (mSDKRemoteConfig.getAutoTrackMode() == 0) {
                return false;
            } else if (mSDKRemoteConfig.getAutoTrackMode() > 0) {
                return true;
            }
        }

        return mAutoTrack;
    }

    @Override
    public boolean isButterknifeOnClickEnabled() {
        return mEnableButterknifeOnClick;
    }

    /**
     * 是否开启自动追踪 Fragment 的 $AppViewScreen 事件
     * 默认不开启
     */
    @Override
    public void trackFragmentAppViewScreen() {
        this.mTrackFragmentAppViewScreen = true;
    }

    @Override
    public boolean isTrackFragmentAppViewScreenEnabled() {
        return this.mTrackFragmentAppViewScreen;
    }

    /**
     * 指定哪些 activity 不被AutoTrack
     *
     * 指定activity的格式为：activity.getClass().getCanonicalName()
     *
     * @param activitiesList  activity列表
     */
    @Override
    public void ignoreAutoTrackActivities(List<Class<?>> activitiesList) {
        if (activitiesList == null || activitiesList.size() == 0) {
            return;
        }

        if (mAutoTrackIgnoredActivities == null) {
            mAutoTrackIgnoredActivities = new ArrayList<>();
        }

        for (Class<?> activity : activitiesList) {
            if (activity != null && !mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
                mAutoTrackIgnoredActivities.add(activity.hashCode());
            }
        }
    }

    /**
     * 指定某个 activity 不被 AutoTrack
     * @param activity Activity
     */
    @Override
    public void ignoreAutoTrackActivity(Class<?> activity) {
        if (activity == null) {
            return;
        }

        if (mAutoTrackIgnoredActivities == null) {
            mAutoTrackIgnoredActivities = new ArrayList<>();
        }

        if (!mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
            mAutoTrackIgnoredActivities.add(activity.hashCode());
        }
    }

    /**
     * 判断 AutoTrack 时，某个 Activity 是否被过滤
     * 如果过滤的话，会过滤掉 Activity 的 $AppViewScreen 事件及控件的 $AppClick 事件
     * @param activity Activity
     * @return Activity 是否被过滤
     */
    public boolean isActivityAutoTrackIgnored(Class<?> activity) {
        if (activity != null &&
                mAutoTrackIgnoredActivities != null &&
                mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
            return true;
        }

        return false;
    }

    /**
     * 判断 AutoTrack 时，某个 Activity 的 $AppViewScreen 是否被过滤
     * 如果过滤的话，会过滤掉 Activity 的 $AppViewScreen 事件
     * @param activity Activity
     * @return Activity 是否被过滤
     */
    @Override
    public boolean isActivityAutoTrackAppViewScreenIgnored(Class<?> activity) {
        if (activity == null) {
            return false;
        }
        if (mAutoTrackIgnoredActivities != null &&
                mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
            return true;
        }

        if (activity.getAnnotation(AnalyticsDataIgnoreTrackAppViewScreenAndAppClick.class) != null) {
            return true;
        }

        if (activity.getAnnotation(AnalyticsDataIgnoreTrackAppViewScreen.class) != null) {
            return true;
        }

        return false;
    }

    /**
     * 判断 AutoTrack 时，某个 Activity 的 $AppClick 是否被过滤
     * 如果过滤的话，会过滤掉 Activity 的 $AppClick 事件
     * @param activity Activity
     * @return Activity 是否被过滤
     */
    @Override
    public boolean isActivityAutoTrackAppClickIgnored(Class<?> activity) {
        if (activity == null) {
            return false;
        }
        if (mAutoTrackIgnoredActivities != null &&
                mAutoTrackIgnoredActivities.contains(activity.hashCode())) {
            return true;
        }

        if (activity.getAnnotation(AnalyticsDataIgnoreTrackAppViewScreenAndAppClick.class) != null) {
            return true;
        }

        if (activity.getAnnotation(AnalyticsDataIgnoreTrackAppClick.class) != null) {
            return true;
        }

        return false;
    }

    private List<AutoTrackEventType> mAutoTrackEventTypeList;

    /**
     * 判断 某个 AutoTrackEventType 是否被忽略
     * @param eventType AutoTrackEventType
     * @return true 被忽略; false 没有被忽略
     */
    @Override
    public boolean isAutoTrackEventTypeIgnored(AutoTrackEventType eventType) {
        if (mSDKRemoteConfig != null) {
            if (mSDKRemoteConfig.getAutoTrackMode() != -1) {
                if (mSDKRemoteConfig.getAutoTrackMode() == 0) {
                    return true;
                }
                return mSDKRemoteConfig.isAutoTrackEventTypeIgnored(eventType);
            }
        }
        if (eventType != null  && !mAutoTrackEventTypeList.contains(eventType)) {
            return true;
        }
        return false;
    }

    /**
     * 设置界面元素ID
     *
     * @param view   要设置的View
     * @param viewID String 给这个View的ID
     */
    @Override
    public void setViewID(View view, String viewID) {
        if (view != null && !TextUtils.isEmpty(viewID)) {
            view.setTag(R.id.analytics_tag_view_id, viewID);
        }
    }

    /**
     * 设置界面元素ID
     *
     * @param view   要设置的View
     * @param viewID String 给这个View的ID
     */
    @Override
    public void setViewID(android.app.Dialog view, String viewID) {
        try {
            if (view != null && !TextUtils.isEmpty(viewID)) {
                if (view.getWindow() != null) {
                    view.getWindow().getDecorView().setTag(R.id.analytics_tag_view_id, viewID);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置界面元素ID
     *
     * @param view   要设置的View
     * @param viewID String 给这个View的ID
     */
    @Override
    public void setViewID(android.support.v7.app.AlertDialog view, String viewID) {
        try {
            if (view != null && !TextUtils.isEmpty(viewID)) {
                if (view.getWindow() != null) {
                    view.getWindow().getDecorView().setTag(R.id.analytics_tag_view_id, viewID);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置 View 所属 Activity
     *
     * @param view   要设置的View
     * @param activity Activity View 所属 Activity
     */
    @Override
    public void setViewActivity(View view, Activity activity) {
        try {
            if (view == null || activity == null) {
                return;
            }
            view.setTag(R.id.analytics_tag_view_activity, activity);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置 View 所属 Fragment 名称
     *
     * @param view   要设置的View
     * @param fragmentName String View 所属 Fragment 名称
     */
    @Override
    public void setViewFragmentName(View view, String fragmentName) {
        try {
            if (view == null || TextUtils.isEmpty(fragmentName)) {
                return;
            }
            view.setTag(R.id.analytics_tag_view_fragment_name2, fragmentName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 忽略View
     *
     * @param view 要忽略的View
     */
    @Override
    public void ignoreView(View view) {
        if (view != null) {
            view.setTag(R.id.analytics_tag_view_ignored, "1");
        }
    }

    /**
     * 设置View属性
     *
     * @param view       要设置的View
     * @param properties 要设置的View的属性
     */
    @Override
    public void setViewProperties(View view, JSONObject properties) {
        if (view == null || properties == null) {
            return;
        }

        view.setTag(R.id.analytics_tag_view_properties, properties);
    }

    private List<Class> mIgnoredViewTypeList = new ArrayList<>();

    @Override
    public List<Class> getIgnoredViewTypeList() {
        if (mIgnoredViewTypeList == null) {
            mIgnoredViewTypeList = new ArrayList<>();
        }

        return mIgnoredViewTypeList;
    }

    /**
     * 忽略某一类型的 View
     *
     * @param viewType Class
     */
    @Override
    public void ignoreViewType(Class viewType) {
        if (viewType == null) {
            return;
        }

        if (mIgnoredViewTypeList == null) {
            mIgnoredViewTypeList = new ArrayList<>();
        }

        if (!mIgnoredViewTypeList.contains(viewType)) {
            mIgnoredViewTypeList.add(viewType);
        }
    }

    @Override
    public boolean isHeatMapActivity(Class<?> activity) {
        try {
            if (mHeatMapActivities.size() == 0) {
                return true;
            }

            if (mHeatMapActivities.contains(activity.hashCode())) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void addHeatMapActivity(Class<?> activity) {
        try {
            if (activity == null) {
                return;
            }

            mHeatMapActivities.add(activity.hashCode());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addHeatMapActivities(List<Class<?>> activitiesList) {
        try {
            if (activitiesList == null || activitiesList.size() == 0) {
                return;
            }

            for (Class<?> activity: activitiesList) {
                if (activity != null) {
                    if (!mHeatMapActivities.contains(activity.hashCode())) {
                        mHeatMapActivities.add(activity.hashCode());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isHeatMapEnabled() {
        return mHeatMapEnabled;
    }

    protected boolean isAppHeatMapConfirmDialogEnabled() {
        return mEnableAppHeatMapConfirmDialog;
    }

    @Override
    public void enableAppHeatMapConfirmDialog(boolean enable) {
        this.mEnableAppHeatMapConfirmDialog = enable;
    }

    /**
     * 开启 HeatMap，$AppClick 事件将会采集控件的 viewPath
     */
    @Override
    public void enableHeatMap() {
        mHeatMapEnabled = true;
    }

    /**
     * 获取当前用户的 loginId
     *
     * 若调用前未调用 {@link #login(String)} 设置用户的 loginId，会返回null
     *
     * @return 当前用户的 loginId
     */
    @Override
    public String getLoginId() {
        synchronized (mLoginId) {
            return mLoginId.get();
        }
    }

    /**
     * 设置当前用户的distinctId。一般情况下，如果是一个注册用户，则应该使用注册系统内
     * 的user_id，如果是个未注册用户，则可以选择一个不会重复的匿名ID，如设备ID等，如果
     * 客户没有调用identify，则使用SDK自动生成的匿名ID
     *
     * @param distinctId 当前用户的distinctId，仅接受数字、下划线和大小写字母
     */
    @Override
    public void identify(String distinctId) {
        try {
            assertDistinctId(distinctId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 登录，设置当前用户的 loginId
     *
     * @param loginId 当前用户的 loginId，不能为空，且长度不能大于255
     */
    @Override
    public void login(String loginId) {
        try {
            assertDistinctId(loginId);
            synchronized (mLoginId) {
                if (!loginId.equals(mLoginId.get())) {
                    mLoginId.commit(loginId);
//                    trackEvent(EventType.TRACK_SIGNUP, "$SignUp", null, getAnonymousId());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 注销，清空当前用户的 loginId
     */
    @Override
    public void logout() {
        synchronized (mLoginId) {
            mLoginId.commit(null);
        }
    }

    /**
     * 调用track接口，追踪一个带有属性的事件
     *
     * @param eventName  事件的名称
     * @param properties 事件的属性
     */
    @Override
    public void track(String eventName, JSONObject properties) {
        try {
            trackEvent(EventType.COUNTER, eventName, properties);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 与 {@link #track(String, JSONObject)} 类似，无事件属性
     *
     * @param eventName 事件的名称
     */
    @Override
    public void track(String eventName) {
        try {
            trackEvent(EventType.COUNTER, eventName, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化事件的计时器，默认计时单位为毫秒。
     *
     * 详细用法请参考 trackTimer(String, TimeUnit)
     *
     * @param eventName 事件的名称
     */
    private void trackTimer(final String eventName) {
        try {
            trackTimer(eventName, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化事件的计时器。
     *
     * 若需要统计某个事件的持续时间，先在事件开始时调用 trackTimer("Event") 记录事件开始时间，该方法并不会真正发
     * 送事件；随后在事件结束时，调用 track("Event", properties)，SDK 会追踪 "Event" 事件，并自动将事件持续时
     * 间记录在事件属性 "event_duration" 中。
     *
     * 多次调用 trackTimer("Event") 时，事件 "Event" 的开始时间以最后一次调用时为准。
     *
     * @param eventName 事件的名称
     * @param timeUnit  计时结果的时间单位
     */
    public void trackTimer(final String eventName, final TimeUnit timeUnit) {
        try {
            assertKey(eventName);
            synchronized (mTrackTimer) {
                mTrackTimer.put(eventName, new EventTimer(timeUnit));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 初始化事件的计时器，默认计时单位为毫秒。
     *
     * 详细用法请参考 trackTimerBegin(String, TimeUnit)
     *
     * @param eventName 事件的名称
     */
    @Override
    public void trackTimerBegin(final String eventName) {
        trackTimer(eventName);
    }

    /**
     * 初始化事件的计时器。
     *
     * 若需要统计某个事件的持续时间，先在事件开始时调用 trackTimerBegin("Event") 记录事件开始时间，该方法并不会真正发
     * 送事件；随后在事件结束时，调用 trackTimerEnd("Event", properties)，SDK 会追踪 "Event" 事件，并自动将事件持续时
     * 间记录在事件属性 "event_duration" 中。
     *
     * 多次调用 trackTimerBegin("Event") 时，事件 "Event" 的开始时间以最后一次调用时为准。
     *
     * @param eventName 事件的名称
     * @param timeUnit  计时结果的时间单位
     */
    @Override
    public void trackTimerBegin(final String eventName, final TimeUnit timeUnit) {
        trackTimer(eventName, timeUnit);
    }

    /**
     * 停止事件计时器
     * @param eventName 事件的名称
     * @param properties 事件的属性
     */
    @Override
    public void trackTimerEnd(final String eventName, JSONObject properties) {
        try {
            trackEvent(EventType.TIMER,eventName, properties);
        } catch (InvalidDataException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止事件计时器
     * @param eventName 事件的名称
     */
    @Override
    public void trackTimerEnd(final String eventName) {
        try {
            trackEvent(EventType.TIMER,eventName,null);
        } catch (InvalidDataException e) {
            e.printStackTrace();
        }
    }

    /**
     * 清除所有事件计时器
     */
    @Override
    public void clearTrackTimer() {
        synchronized (mTrackTimer) {
            mTrackTimer.clear();
        }
    }

    /**
     * 获取LastScreenUrl
     * @return String
     */
    @Override
    public String getLastScreenUrl() {
        return mLastScreenUrl;
    }

    /**
     * App 退出或进到后台时清空 referrer，默认情况下不清空
     */
    @Override
    public void clearReferrerWhenAppEnd() {
        mClearReferrerWhenAppEnd = true;
    }

    @Override
    public void clearLastScreenUrl() {
        if (mClearReferrerWhenAppEnd) {
            mLastScreenUrl = null;
        }
    }

    @Override
    public String getMainProcessName() {
        return mMainProcessName;
    }

    /**
     * 获取LastScreenTrackProperties
     * @return JSONObject
     */
    @Override
    public JSONObject getLastScreenTrackProperties() {
        return mLastScreenTrackProperties;
    }

    /**
     * Track 进入页面事件 ($AppViewScreen)
     * @param url String
     * @param properties JSONObject
     */
    @Override
    public void trackViewScreen(String url, JSONObject properties) {
        try {
            if (!TextUtils.isEmpty(url) || properties != null) {
                JSONObject trackProperties = new JSONObject();
                mLastScreenTrackProperties = properties;

                if (!TextUtils.isEmpty(mLastScreenUrl)) {
                    trackProperties.put("reference", mLastScreenUrl);
                }
//
//                trackProperties.put("$url", url);
                mLastScreenUrl = url;
                if (properties != null) {
                    AnalyticsDataUtils.mergeJSONObject(properties, trackProperties);
                }
                track(EventName.PAGE_VIEW_START.getEventName(), trackProperties);
            }
        } catch (JSONException e) {
            LogUtils.i(TAG, "trackViewScreen:" + e);
        }
    }

    /**
     * Track Activity 进入页面事件($AppViewScreen)
     * @param activity activity Activity，当前 Activity
     */
    @Override
    public void trackViewScreen(Activity activity) {
        try {
            if (activity == null) {
                return;
            }

            JSONObject properties = new JSONObject();
            AnalyticsDataUtils.getScreenNameAndTitleFromActivity(properties, activity);

            if (activity instanceof ScreenAutoTracker) {
                ScreenAutoTracker screenAutoTracker = (ScreenAutoTracker) activity;

                String screenUrl = screenAutoTracker.getScreenUrl();
                JSONObject otherProperties = screenAutoTracker.getTrackProperties();
                if (otherProperties != null) {
                    AnalyticsDataUtils.mergeJSONObject(otherProperties, properties);
                    trackViewScreen(screenUrl, properties);
                }

            } else {
                track(EventName.PAGE_VIEW_START.getEventName(), properties);
            }
            trackTimerBegin(EventName.PAGE_VIEW_END.getEventName());
        } catch (Exception e) {
            LogUtils.i(TAG, "trackViewScreen:" + e);
        }
    }

    @Override
    public void trackViewScreen(android.app.Fragment fragment) {
        try {
            if (fragment == null) {
                return;
            }

            JSONObject properties = new JSONObject();
            String fragmentName = fragment.getClass().getCanonicalName();
            String screenName = fragmentName;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                screenName = String.format(Locale.CHINA, "%s|%s", activity.getClass().getCanonicalName(), fragmentName);
            }

            properties.put(FieldConstant.ELEMENT_ID, screenName);
            track(EventName.PAGE_VIEW_START.getEventName(), properties);
            trackTimerBegin(EventName.PAGE_VIEW_END.getEventName());
        } catch (Exception e) {
            LogUtils.i(TAG, "trackViewScreen:" + e);
        }
    }

    @Override
    public void trackViewScreen(android.support.v4.app.Fragment fragment) {
        try {
            if (fragment == null) {
                return;
            }

            JSONObject properties = new JSONObject();
            String fragmentName = fragment.getClass().getCanonicalName();
            String screenName = fragmentName;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                screenName = String.format(Locale.CHINA, "%s|%s", activity.getClass().getCanonicalName(), fragmentName);
            }

            properties.put(FieldConstant.ELEMENT_ID, screenName);
            track(EventName.PAGE_VIEW_START.getEventName(), properties);
            trackTimerBegin(EventName.PAGE_VIEW_END.getEventName());
        } catch (Exception e) {
            LogUtils.i(TAG, "trackViewScreen:" + e);
        }
    }

    @Override
    public void trackViewScreenEnd(Activity activity) {
        try {
            if (activity == null) {
                return;
            }

            JSONObject properties = new JSONObject();
            AnalyticsDataUtils.getScreenNameAndTitleFromActivity(properties, activity);

            if (activity instanceof ScreenAutoTracker) {
                ScreenAutoTracker screenAutoTracker = (ScreenAutoTracker) activity;

                JSONObject otherProperties = screenAutoTracker.getTrackProperties();
                if (otherProperties != null) {
                    AnalyticsDataUtils.mergeJSONObject(otherProperties, properties);
                }
            }
            track(EventName.PAGE_VIEW_END.getEventName(), properties);
        } catch (Exception e) {
            LogUtils.i(TAG, "trackViewEndScreen:" + e);
        }
    }

    @Override
    public void trackViewScreenEnd(Fragment fragment) {
        try {
            if (fragment == null) {
                return;
            }

            JSONObject properties = new JSONObject();
            String fragmentName = fragment.getClass().getCanonicalName();
            String screenName = fragmentName;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                screenName = String.format(Locale.CHINA, "%s|%s", activity.getClass().getCanonicalName(), fragmentName);
            }

            properties.put(FieldConstant.ELEMENT_ID, screenName);
            track(EventName.PAGE_VIEW_END.getEventName(), properties);
        } catch (Exception e) {
            LogUtils.i(TAG, "trackViewEndScreen:" + e);
        }
    }

    @Override
    public void trackViewScreenEnd(android.support.v4.app.Fragment fragment) {
        try {
            if (fragment == null) {
                return;
            }

            JSONObject properties = new JSONObject();
            String fragmentName = fragment.getClass().getCanonicalName();
            String screenName = fragmentName;

            Activity activity = fragment.getActivity();
            if (activity != null) {
                screenName = String.format(Locale.CHINA, "%s|%s", activity.getClass().getCanonicalName(), fragmentName);
            }

            properties.put(FieldConstant.ELEMENT_ID, screenName);
            track(EventName.PAGE_VIEW_END.getEventName(), properties);
        } catch (Exception e) {
            LogUtils.i(TAG, "trackViewEndScreen:" + e);
        }
    }


    /**
     * app进入后台
     * 遍历mTrackTimer
     * eventAccumulatedDuration = eventAccumulatedDuration + System.currentTimeMillis() - startTime
     */
    protected void appEnterBackground() {
        synchronized (mTrackTimer) {
            try {
                Iterator iter = mTrackTimer.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry entry = (Map.Entry) iter.next();
                    if (entry != null) {
                        if ("$AppEnd".equals(entry.getKey().toString())) {
                            continue;
                        }
                        EventTimer eventTimer = (EventTimer) entry.getValue();
                        if (eventTimer != null) {
                            long eventAccumulatedDuration = eventTimer.getEventAccumulatedDuration() + SystemClock.elapsedRealtime() - eventTimer.getStartTime();
                            eventTimer.setEventAccumulatedDuration(eventAccumulatedDuration);
                            eventTimer.setStartTime(SystemClock.elapsedRealtime());
                        }
                    }
                }
            } catch (Exception e) {
                LogUtils.i(TAG, "appEnterBackground error:" + e.getMessage());
            }
        }
    }

    /**
     * app从后台恢复
     * 遍历mTrackTimer
     * startTime = System.currentTimeMillis()
     */
    protected void appBecomeActive() {
        synchronized (mTrackTimer) {
            try {
                Iterator iter = mTrackTimer.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry entry = (Map.Entry) iter.next();
                    if (entry != null) {
                        EventTimer eventTimer = (EventTimer) entry.getValue();
                        if (eventTimer != null) {
                            eventTimer.setStartTime(SystemClock.elapsedRealtime());
                        }
                    }
                }
            } catch (Exception e) {
                LogUtils.i(TAG, "appBecomeActive error:" + e.getMessage());
            }
        }
    }

    /**
     * 将所有本地缓存的日志发送到 Analytics.
     */
    @Override
    public void flush() {
        mMessages.flush();
    }

    /**
     * 以阻塞形式将所有本地缓存的日志发送到 Analytics，该方法不能在 UI 线程调用。
     */
    @Override
    public void flushSync() {
        mMessages.sendData();
    }

    /**
     * 获取事件公共属性
     *
     * @return 当前所有Super属性
     */
    @Override
    public JSONObject getSuperProperties() {
        synchronized (mSuperProperties) {
            return mSuperProperties.get();
        }
    }

    /**
     * 注册所有事件都有的公共属性
     *
     * @param superProperties 事件公共属性
     */
    @Override
    public void registerSuperProperties(JSONObject superProperties) {
        try {
            if (superProperties == null) {
                return;
            }
            assertPropertyTypes(EventType.REGISTER_SUPER_PROPERTIES, superProperties);
            synchronized (mSuperProperties) {
                JSONObject properties = mSuperProperties.get();
                AnalyticsDataUtils.mergeJSONObject(superProperties, properties);
                mSuperProperties.commit(properties);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除事件公共属性
     *
     * @param superPropertyName 事件属性名称
     */
    @Override
    public void unregisterSuperProperty(String superPropertyName) {
        try {
            synchronized (mSuperProperties) {
                JSONObject superProperties = mSuperProperties.get();
                superProperties.remove(superPropertyName);
                mSuperProperties.commit(superProperties);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除所有事件公共属性
     */
    @Override
    public void clearSuperProperties() {
        synchronized (mSuperProperties) {
            mSuperProperties.commit(new JSONObject());
        }
    }

    /**
     * 设置用户的一个或多个Profile。
     * Profile如果存在，则覆盖；否则，新创建。
     *
     * @param properties 属性列表
     */
    @Override
    public void profileSet(JSONObject properties) {
        try {
            trackEvent(EventType.PROFILE_SET, null, properties);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置用户的一个Profile，如果之前存在，则覆盖，否则，新创建
     *
     * @param property 属性名称
     * @param value    属性的值，值的类型只允许为
     *                 {@link String}, {@link Number}, {@link Date}, {@link List}
     */
    @Override
    public void profileSet(String property, Object value) {
        try {
            trackEvent(EventType.PROFILE_SET, null, new JSONObject().put(property, value));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 首次设置用户的一个或多个Profile。
     * 与profileSet接口不同的是，如果之前存在，则忽略，否则，新创建
     *
     * @param properties 属性列表
     */
    @Override
    public void profileSetOnce(JSONObject properties)  {
        try {
            trackEvent(EventType.PROFILE_SET_ONCE, null, properties);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 首次设置用户的一个Profile
     * 与profileSet接口不同的是，如果之前存在，则忽略，否则，新创建
     *
     * @param property 属性名称
     * @param value    属性的值，值的类型只允许为
     *                 {@link String}, {@link Number}, {@link Date}, {@link List}
     */
    @Override
    public void profileSetOnce(String property, Object value) {
        try {
            trackEvent(EventType.PROFILE_SET_ONCE, null, new JSONObject().put(property, value));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 给一个或多个数值类型的Profile增加一个数值。只能对数值型属性进行操作，若该属性
     * 未设置，则添加属性并设置默认值为0
     *
     * @param properties 一个或多个属性集合
     */
    @Override
    public void profileIncrement(Map<String, ? extends Number> properties) {
        try {
            trackEvent(EventType.PROFILE_INCREMENT, null, new JSONObject(properties));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 给一个数值类型的Profile增加一个数值。只能对数值型属性进行操作，若该属性
     * 未设置，则添加属性并设置默认值为0
     *
     * @param property 属性名称
     * @param value    属性的值，值的类型只允许为 {@link Number}
     */
    @Override
    public void profileIncrement(String property, Number value) {
        try {
            trackEvent(EventType.PROFILE_INCREMENT, null, new JSONObject().put(property, value));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 给一个列表类型的Profile增加一个元素
     *
     * @param property 属性名称
     * @param value    新增的元素
     */
    @Override
    public void profileAppend(String property, String value) {
        try {
            final JSONArray append_values = new JSONArray();
            append_values.put(value);
            final JSONObject properties = new JSONObject();
            properties.put(property, append_values);
            trackEvent(EventType.PROFILE_APPEND, null, properties);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 给一个列表类型的Profile增加一个或多个元素
     *
     * @param property 属性名称
     * @param values   新增的元素集合
     */
    @Override
    public void profileAppend(String property, Set<String> values) {
        try {
            final JSONArray append_values = new JSONArray();
            for (String value : values) {
                append_values.put(value);
            }
            final JSONObject properties = new JSONObject();
            properties.put(property, append_values);
            trackEvent(EventType.PROFILE_APPEND, null, properties);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除用户的一个Profile
     *
     * @param property 属性名称
     */
    @Override
    public void profileUnset(String property) {
        try {
            trackEvent(EventType.PROFILE_UNSET, null, new JSONObject().put(property, true));
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 删除用户所有Profile
     */
    @Override
    public void profileDelete() {
        try {
            trackEvent(EventType.PROFILE_DELETE, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isDebugMode() {
        return mDebugMode.isDebugMode();
    }

    boolean isDebugWriteData() {
        return mDebugMode.isDebugWriteData();
    }

    private void disableDebugMode() {
        mDebugMode = DebugMode.DEBUG_OFF;
        enableLog(false);
    }

    String getServerUrl() {
        return mServerUrl;
    }

    /**
     *
     * @param eventType
     * @param eventName
     * @param properties
     * @throws InvalidDataException
     */
    public void trackEvent(final String eventType, final String eventName, final JSONObject properties) throws InvalidDataException {
        final EventTimer eventTimer;
        if (eventName != null) {
            synchronized (mTrackTimer) {
                eventTimer = mTrackTimer.get(eventName);
                mTrackTimer.remove(eventName);
            }
        } else {
            eventTimer = null;
        }

        AnalyticsDataThreadPool.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    assertKey(eventName);
                    assertPropertyTypes(null, properties);

                    try {
                        JSONObject deviceProperties;

                        deviceProperties = new JSONObject(mDeviceInfo);

                        //之前可能会因为没有权限无法获取运营商信息，检测再次获取
                        try {
                            if (TextUtils.isEmpty(deviceProperties.optString(FieldConstant.IMEI))) {
                                String imei = DeviceHelper.getImei(mContext);
                                if (!TextUtils.isEmpty(imei)) {
                                    deviceProperties.put(FieldConstant.IMEI, imei);
                                    deviceProperties.put(FieldConstant.DEVICE_ID, DeviceHelper.getDeviceId(mContext));
                                }
                            }
                            if (TextUtils.isEmpty(deviceProperties.optString(FieldConstant.IMSI))) {
                                String imsi = DeviceHelper.getImsi(mContext);
                                if (!TextUtils.isEmpty(imsi)) {
                                    deviceProperties.put(FieldConstant.IMSI, imsi);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        synchronized (mSuperProperties) {
                            JSONObject superProperties = mSuperProperties.get();
                            AnalyticsDataUtils.mergeJSONObject(superProperties, deviceProperties);
                        }

                        // 当前网络状况
                        deviceProperties.put(FieldConstant.NETWORK,AnalyticsDataUtils.networkType(mContext));

                        JSONObject dataObj = new JSONObject();
                        if (deviceProperties.has(FieldConstant.APP)){
                            dataObj.put(FieldConstant.APP,deviceProperties.opt(FieldConstant.APP));
                            deviceProperties.remove(FieldConstant.APP);
                        }

                        //device
                        dataObj.put("device", deviceProperties);

                        try {
                            if (mGPSLocation != null) {
                                // locate GPS
                                final JSONObject locate = new JSONObject();
                                locate.put(FieldConstant.LAT, mGPSLocation.getLatitude());
                                locate.put(FieldConstant.LON, mGPSLocation.getLongitude());
                                locate.put(FieldConstant.CITY_KEY, mGPSLocation.getCityKey());
                                dataObj.put("locate", locate);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // event
                        final JSONObject eventObj = new JSONObject();
                        if (null != properties) {
                            AnalyticsDataUtils.mergeJSONObject(properties, eventObj);
                        }
                        if (null != eventTimer) {
                            try {
                                Double duration = Double.valueOf(eventTimer.duration());
                                if (duration > 0) {
                                    eventObj.put(FieldConstant.DURATION, duration);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        // 屏幕方向
                        try {
                            String screenOrientation = getScreenOrientation();
                            if (!TextUtils.isEmpty(screenOrientation)) {
                                eventObj.put(FieldConstant.SCREEN_ORIENTATION, screenOrientation);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        if (mAnalyticsData3DDetector.getSensorEvent() != null){
                            eventObj.put(FieldConstant.X3D,mAnalyticsData3DDetector.getSensorEvent().values[0]);
                            eventObj.put(FieldConstant.Y3D,mAnalyticsData3DDetector.getSensorEvent().values[1]);
                            eventObj.put(FieldConstant.Z3D,mAnalyticsData3DDetector.getSensorEvent().values[2]);
                        }

                        eventObj.put(FieldConstant.EVENT_TIME, System.currentTimeMillis());
                        eventObj.put(FieldConstant.EVENT_ID, UUID.randomUUID().toString());
                        eventObj.put(FieldConstant.EVENT_TYPE, eventType);
                        eventObj.put(FieldConstant.SESSION_ID,mSessionId.get());
                        eventObj.put(FieldConstant.EVENT_NAME, !TextUtils.isEmpty(eventName)?eventName:"");
                        String uid = getLoginId();
                        eventObj.put(FieldConstant.UID, TextUtils.isEmpty(uid) ? "" : uid);
                        //是否首日访问
                        eventObj.put("$is_first_day", isFirstDay());
                        dataObj.put("event", eventObj);

                        mMessages.enqueueEventMessage(eventType, dataObj);
                        LogUtils.i(TAG, "track event:\n" + JSONUtils.formatJson(dataObj.toString()));
                    } catch (JSONException e) {
                        throw new InvalidDataException("Unexpected property");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void trackEvent(final EventType eventType, final String eventName, final JSONObject properties) throws InvalidDataException {
        final EventTimer eventTimer;
        if (eventName != null) {
            synchronized (mTrackTimer) {
                eventTimer = mTrackTimer.get(eventName);
                mTrackTimer.remove(eventName);
            }
        } else {
            eventTimer = null;
        }

        AnalyticsDataThreadPool.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (eventType.isTrack()) {
                        assertKey(eventName);
                    }
                    assertPropertyTypes(eventType, properties);

                    try {
                        JSONObject deviceProperties;

                        if (eventType.isTrack()) {
                            deviceProperties = new JSONObject(mDeviceInfo);

                            //之前可能会因为没有权限无法获取运营商信息，检测再次获取
                            try {
                                if (TextUtils.isEmpty(deviceProperties.optString(FieldConstant.IMEI))) {
                                    String imei = DeviceHelper.getImei(mContext);
                                    if (!TextUtils.isEmpty(imei)) {
                                        deviceProperties.put(FieldConstant.IMEI, imei);
                                        deviceProperties.put(FieldConstant.DEVICE_ID, DeviceHelper.getDeviceId(mContext));
                                    }
                                }
                                if (TextUtils.isEmpty(deviceProperties.optString(FieldConstant.IMSI))) {
                                    String imsi = DeviceHelper.getImsi(mContext);
                                    if (!TextUtils.isEmpty(imsi)) {
                                        deviceProperties.put(FieldConstant.IMSI, imsi);
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            synchronized (mSuperProperties) {
                                JSONObject superProperties = mSuperProperties.get();
                                AnalyticsDataUtils.mergeJSONObject(superProperties, deviceProperties);
                            }

                            // 当前网络状况
                            deviceProperties.put(FieldConstant.NETWORK,AnalyticsDataUtils.networkType(mContext));

                        } else if (eventType.isProfile()) {
                            deviceProperties = new JSONObject();
                        } else {
                            return;
                        }
                        JSONObject dataObj = new JSONObject();
                        if (deviceProperties.has(FieldConstant.APP)){
                            dataObj.put(FieldConstant.APP,deviceProperties.opt(FieldConstant.APP));
                            deviceProperties.remove(FieldConstant.APP);
                        }

                        //device
                        dataObj.put("device", deviceProperties);

                        try {
                            if (mGPSLocation != null) {
                                // locate GPS
                                final JSONObject locate = new JSONObject();
                                locate.put(FieldConstant.LAT, mGPSLocation.getLatitude());
                                locate.put(FieldConstant.LON, mGPSLocation.getLongitude());
                                locate.put(FieldConstant.CITY_KEY, mGPSLocation.getCityKey());
                                dataObj.put("locate", locate);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        // event
                        final JSONObject eventObj = new JSONObject();
                        if (null != properties) {
                            AnalyticsDataUtils.mergeJSONObject(properties, eventObj);
                        }
                        if (null != eventTimer) {
                            try {
                                Double duration = Double.valueOf(eventTimer.duration());
                                if (duration > 0) {
                                    eventObj.put(FieldConstant.DURATION, duration);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        // 屏幕方向
                        try {
                            String screenOrientation = getScreenOrientation();
                            if (!TextUtils.isEmpty(screenOrientation)) {
                                eventObj.put(FieldConstant.SCREEN_ORIENTATION, screenOrientation);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        if (mAnalyticsData3DDetector.getSensorEvent() != null){
                            eventObj.put(FieldConstant.X3D,mAnalyticsData3DDetector.getSensorEvent().values[0]);
                            eventObj.put(FieldConstant.Y3D,mAnalyticsData3DDetector.getSensorEvent().values[1]);
                            eventObj.put(FieldConstant.Z3D,mAnalyticsData3DDetector.getSensorEvent().values[2]);
                        }

                        eventObj.put(FieldConstant.EVENT_TIME, System.currentTimeMillis());
                        eventObj.put(FieldConstant.EVENT_ID, UUID.randomUUID().toString());
                        eventObj.put(FieldConstant.EVENT_TYPE, eventType.getEventType());
                        eventObj.put(FieldConstant.SESSION_ID,mSessionId.get());
                        eventObj.put(FieldConstant.EVENT_NAME, !TextUtils.isEmpty(eventName)?eventName:"");
                        String uid = getLoginId();
                        eventObj.put(FieldConstant.UID, TextUtils.isEmpty(uid) ? "" : uid);
                        if (eventType == EventType.TRACK) {
                            //是否首日访问
                            eventObj.put("$is_first_day", isFirstDay());
                        }
                        dataObj.put("event", eventObj);

                        mMessages.enqueueEventMessage(eventType.getEventType(), dataObj);
                        LogUtils.i(TAG, "track event:\n" + JSONUtils.formatJson(dataObj.toString()));
                    } catch (JSONException e) {
                        throw new InvalidDataException("Unexpected property");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private boolean isFirstDay() {
        String firstDay = mFirstDay.get();
        if (firstDay == null) {
            return true;
        }
        String current = mIsFirstDayDateFormat.format(System.currentTimeMillis());
        return firstDay.equals(current);
    }

    private void assertPropertyTypes(EventType eventType, JSONObject properties) throws
            InvalidDataException {
        if (properties == null) {
            return;
        }

        for (Iterator iterator = properties.keys(); iterator.hasNext(); ) {
            String key = (String) iterator.next();

            // Check Keys
            assertKey(key);

            try {
                Object value = properties.get(key);

                if (!(value instanceof String || value instanceof Number || value instanceof JSONObject || value
                        instanceof JSONArray || value instanceof Boolean || value instanceof Date)) {
                    throw new InvalidDataException("The property value must be an instance of "
                            + "String/Number/Boolean/JSONArray. [key='" + key + "', value='" + value.toString()
                            + "']");
                }

                if ("app_crashed_reason".equals(key)) {
                    if (value instanceof String && !key.startsWith("$") && ((String) value).length() > 8191 * 2) {
                        LogUtils.d(TAG, "The property value is too long. [key='" + key
                                + "', value='" + value.toString() + "']");
                    }
                } else {
                    if (value instanceof String && !key.startsWith("$") && ((String) value).length() > 8191) {
                        LogUtils.d(TAG, "The property value is too long. [key='" + key
                                + "', value='" + value.toString() + "']");
                    }
                }
            } catch (JSONException e) {
                throw new InvalidDataException("Unexpected property key. [key='" + key + "']");
            }
        }
    }

    private void assertKey(String key) throws InvalidDataException {
        if (null == key || key.length() < 1) {
            throw new InvalidDataException("The key is empty.");
        }
        if (!(KEY_PATTERN.matcher(key).matches())) {
            throw new InvalidDataException("The key '" + key + "' is invalid.");
        }
    }

    private void assertDistinctId(String key) throws InvalidDataException {
        if (key == null || key.length() < 1) {
            throw new InvalidDataException("The distinct_id or original_id or login_id is empty.");
        }
        if (key.length() > 255) {
            throw new InvalidDataException("The max length of distinct_id or original_id or login_id is 255.");
        }
    }

    class BatteryReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            int currLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);  //当前电量
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 1);      //总电量
            int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        }
    }

    public static void setChannel(String channel){
        CHANNEL = channel;
    }

    // SDK版本
    static final String VERSION = "1.0.0";

    public static Boolean ENABLE_LOG = false;
    public static String CHANNEL = "";

    private static final Pattern KEY_PATTERN = Pattern.compile(
            "^((?!^distinct_id$|^original_id$|^time$|^properties$|^id$|^first_id$|^second_id$|^users$|^events$|^event$|^user_id$|^date$|^datetime$)[a-zA-Z_$][a-zA-Z\\d_$]{0,99})$",
            Pattern.CASE_INSENSITIVE);

    // Maps each token to a singleton AnalyticsDataAPI instance
    private static final Map<Context, AnalyticsDataAPI> sInstanceMap = new HashMap<>();
    private static final SharedPreferencesLoader sPrefsLoader = new SharedPreferencesLoader();
    private static AnalyticsDataSDKRemoteConfig mSDKRemoteConfig;
    private static AnalyticsDataGPSLocation mGPSLocation;

    // Configures
  /* SensorsAnalytics 地址 */
    private String mServerUrl;
    /* Debug模式选项 */
    private DebugMode mDebugMode;
    /* Flush时间间隔 */
    private int mFlushInterval;
    /* Flush数据量阈值 */
    private int mFlushBulkSize;
    /* SDK 自动采集事件 */
    private boolean mAutoTrack;
    private boolean mHeatMapEnabled;
    /* 上个页面的Url*/
    private String mLastScreenUrl;
    private JSONObject mLastScreenTrackProperties;
    private boolean mEnableButterknifeOnClick;
    /* $AppViewScreen 事件是否支持 Fragment*/
    private boolean mTrackFragmentAppViewScreen;
    private boolean mClearReferrerWhenAppEnd = false;
    private boolean mEnableAppHeatMapConfirmDialog = true;
    private boolean mDisableDefaultRemoteConfig = false;
    /* 上传安装应用的时间间隔 */
    private long mInstallAppInterval;

    private final Context mContext;
    private final AnalyticsMessages mMessages;
    private final PersistentLoginId mLoginId;
    private final PersistentSuperProperties mSuperProperties;
    private final PersistentFirstStart mFirstStart;
    private final PersistentFirstDay mFirstDay;
    private final PersistentSessionId mSessionId;
//    private final PersistentRemoteSDKConfig mPersistentRemoteSDKConfig;
    private final Map<String, Object> mDeviceInfo;
    private final Map<String, EventTimer> mTrackTimer;
    private List<Integer> mAutoTrackIgnoredActivities;
    private List<Integer> mHeatMapActivities;
    private int mFlushNetworkPolicy = NetworkType.TYPE_3G | NetworkType.TYPE_4G | NetworkType.TYPE_WIFI;
    private final String mMainProcessName;
    private long mMaxCacheSize = 32 * 1024 * 1024; //default 32MB

    private AnalyticsDataScreenOrientationDetector mOrientationDetector;

    private static final SimpleDateFormat mIsFirstDayDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private static final String TAG = "WELI.AnalyticsDataAPI";

    private AnalyticsData3DDetector mAnalyticsData3DDetector;
    private BatteryReceiver batteryReceiver;
}
