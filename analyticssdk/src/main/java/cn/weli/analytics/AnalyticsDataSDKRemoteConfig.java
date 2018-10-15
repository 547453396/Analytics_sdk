package cn.weli.analytics;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AnalyticsDataSDKRemoteConfig {
    /**
     * config 版本号
     */
    private String v;

    /**
     * 是否关闭 debug 模式
     */
    private boolean disableDebugMode;

    /**
     * 是否关闭 AutoTrack
     */
    private int autoTrackMode;

    /**
     * 是否关闭 SDK
     */
    private boolean disableSDK;

    private List<AnalyticsDataAPI.AutoTrackEventType> mAutoTrackEventTypeList;

    public AnalyticsDataSDKRemoteConfig() {
        this.disableDebugMode = false;
        this.disableSDK = false;
        this.autoTrackMode = -1;
    }

    public String getV() {
        return v;
    }

    public void setV(String v) {
        this.v = v;
    }

    public boolean isDisableDebugMode() {
        return disableDebugMode;
    }

    public void setDisableDebugMode(boolean disableDebugMode) {
        this.disableDebugMode = disableDebugMode;
    }

    public boolean isDisableSDK() {
        return disableSDK;
    }

    public void setDisableSDK(boolean disableSDK) {
        this.disableSDK = disableSDK;
    }

    public int getAutoTrackMode() {
        return autoTrackMode;
    }

    protected boolean isAutoTrackEventTypeIgnored(AnalyticsDataAPI.AutoTrackEventType eventType) {
        if (autoTrackMode == -1) {
            return false;
        }

        if (autoTrackMode == 0) {
            return true;
        }

        if (this.mAutoTrackEventTypeList.contains(eventType)) {
            return false;
        }

        return true;
    }

    protected List<AnalyticsDataAPI.AutoTrackEventType> getAutoTrackEventTypeList() {
        return mAutoTrackEventTypeList;
    }

    public void setAutoTrackMode(int autoTrackMode) {
        this.autoTrackMode = autoTrackMode;

        if (this.autoTrackMode == -1) {
            mAutoTrackEventTypeList = null;
            return;
        }

        if (this.mAutoTrackEventTypeList == null) {
            this.mAutoTrackEventTypeList = new ArrayList<>();
        }

        if ((this.autoTrackMode & AnalyticsDataAPI.AutoTrackEventType.APP_START.getEventValue()) == AnalyticsDataAPI.AutoTrackEventType.APP_START.getEventValue()) {
            mAutoTrackEventTypeList.add(AnalyticsDataAPI.AutoTrackEventType.APP_START);
        }

        if ((this.autoTrackMode & AnalyticsDataAPI.AutoTrackEventType.APP_END.getEventValue()) == AnalyticsDataAPI.AutoTrackEventType.APP_END.getEventValue()) {
            mAutoTrackEventTypeList.add(AnalyticsDataAPI.AutoTrackEventType.APP_END);
        }

        if ((this.autoTrackMode & AnalyticsDataAPI.AutoTrackEventType.APP_CLICK.getEventValue()) == AnalyticsDataAPI.AutoTrackEventType.APP_CLICK.getEventValue()) {
            mAutoTrackEventTypeList.add(AnalyticsDataAPI.AutoTrackEventType.APP_CLICK);
        }

        if ((this.autoTrackMode & AnalyticsDataAPI.AutoTrackEventType.APP_VIEW_SCREEN.getEventValue()) == AnalyticsDataAPI.AutoTrackEventType.APP_VIEW_SCREEN.getEventValue()) {
            mAutoTrackEventTypeList.add(AnalyticsDataAPI.AutoTrackEventType.APP_VIEW_SCREEN);
        }

        if (this.autoTrackMode == 0) {
            mAutoTrackEventTypeList.clear();
        }
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("v", v);
            JSONObject configObject = new JSONObject();
            configObject.put("disableDebugMode", disableDebugMode);
            configObject.put("autoTrackMode", autoTrackMode);
            configObject.put("disableSDK", disableSDK);
            jsonObject.put("configs", configObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    public String toString() {
        return "{ v=" + v + ", disableDebugMode=" + disableDebugMode + ", disableSDK=" + disableSDK + ", autoTrackMode=" + autoTrackMode + "}";
    }
}
