package com.weli.analytics;

import android.app.Application;

import java.util.ArrayList;
import java.util.List;

import cn.weli.analytics.AnalyticsDataAPI;
import cn.weli.analytics.AnalyticsDataAPI.AutoTrackEventType;

public class MyApplication extends Application {
    /**
     * Analytics 采集数据的地址
     */
    private final static String SA_SERVER_URL = "http://test-ckh-zyh.cloud.sensorsdata.cn:8006/sa?project=wzz&token=de28ecf691865360";

    /**
     * Analytics DEBUG 模式
     * AnalyticsDataAPI.DebugMode.DEBUG_OFF - 关闭 Debug 模式
     * AnalyticsDataAPI.DebugMode.DEBUG_ONLY - 打开 Debug 模式，校验数据，但不进行数据导入
     * AnalyticsDataAPI.DebugMode.DEBUG_AND_TRACK - 打开 Debug 模式，校验数据，并将数据导入到 Analytics 中
     * 注意！请不要在正式发布的 App 中使用 Debug 模式！
     */
    private final AnalyticsDataAPI.DebugMode SA_DEBUG_MODE = AnalyticsDataAPI.DebugMode.DEBUG_AND_TRACK;

    @Override
    public void onCreate() {
        super.onCreate();
        initAnalyticsDataAPI();
    }

    /**
     * 初始化 Analytics SDK
     */
    private void initAnalyticsDataAPI() {
        AnalyticsDataAPI.setChannel("test");
        AnalyticsDataAPI.sharedInstance(
                this,                               // 传入 Context
                SA_SERVER_URL,                      // 数据接收的 URL
                SA_DEBUG_MODE);                     // Debug 模式选项
        List<AutoTrackEventType> eventTypeList = new ArrayList<>();
        eventTypeList.add(AutoTrackEventType.APP_START);
        eventTypeList.add(AutoTrackEventType.APP_END);
        eventTypeList.add(AutoTrackEventType.APP_VIEW_SCREEN);
        eventTypeList.add(AutoTrackEventType.APP_CLICK);
        AnalyticsDataAPI.sharedInstance(this).enableAutoTrack(eventTypeList);

        AnalyticsDataAPI.sharedInstance(this).login("uid");
    }
}
