package cn.weli.analytics;

import android.content.Context;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;

import org.json.JSONException;
import org.json.JSONObject;

import cn.weli.analytics.utils.LogUtils;

public class AppWebViewInterface {
    private static final String TAG = "WELI.AppWebViewInterface";
    private Context mContext;
    private JSONObject properties;
    private boolean enableVerify;

    AppWebViewInterface(Context c, JSONObject p, boolean b) {
        this.mContext = c;
        this.properties = p;
        this.enableVerify = b;
    }

    @JavascriptInterface
    public String sensorsdata_call_app() {
        try {
            if (properties == null) {
                properties = new JSONObject();
            }
            String loginId = AnalyticsDataAPI.sharedInstance(mContext).getLoginId();
            if (!TextUtils.isEmpty(loginId)) {
                properties.put("uid", loginId);
                properties.put("is_login", true);
            } else {
                properties.put("is_login", false);
            }
            return properties.toString();
        } catch (JSONException e) {
            LogUtils.i(TAG, e.getMessage());
        }
        return null;
    }

    @JavascriptInterface
    public void sensorsdata_track(String event) {
        try {
            AnalyticsDataAPI.sharedInstance(mContext).trackEventFromH5(event, enableVerify);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @JavascriptInterface
    public boolean sensorsdata_verify(String event){
        try {
            if(!enableVerify) {
                sensorsdata_track(event);
                return true;
            }
            return AnalyticsDataAPI.sharedInstance(mContext)._trackEventFromH5(event);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
