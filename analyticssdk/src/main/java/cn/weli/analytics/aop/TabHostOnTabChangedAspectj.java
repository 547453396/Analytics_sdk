package cn.weli.analytics.aop;

import android.text.TextUtils;
import android.widget.TabHost;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.json.JSONObject;

import cn.weli.analytics.AnalyticsDataAPI;
import cn.weli.analytics.utils.LogUtils;

/**
 * TabHost.OnTabChangeListener
 */

@Aspect
public class TabHostOnTabChangedAspectj {
    private final static String TAG = TabHostOnTabChangedAspectj.class.getCanonicalName();

    @After("execution(* android.widget.TabHost.OnTabChangeListener.onTabChanged(String))")
    public void onTabChangedAOP(final JoinPoint joinPoint) throws Throwable {
        AopThreadPool.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    //关闭 AutoTrack
                    if (!AnalyticsDataAPI.sharedInstance().isAutoTrackEnabled()) {
                        return;
                    }

                    //$AppClick 被过滤
                    if (AnalyticsDataAPI.sharedInstance().isAutoTrackEventTypeIgnored(AnalyticsDataAPI.AutoTrackEventType.APP_CLICK)) {
                        return;
                    }

                    //基本校验
                    if (joinPoint == null || joinPoint.getArgs() == null || joinPoint.getArgs().length != 1) {
                        return;
                    }

                    //TabHost 被忽略
                    if (AopUtil.isViewIgnored(TabHost.class)) {
                        return;
                    }

                    //获取被点击的 tabName
                    String tabName = (String) joinPoint.getArgs()[0];

                    JSONObject properties = new JSONObject();

                    //$title、$screen_name、$element_content
                    try {
                        if (!TextUtils.isEmpty(tabName)) {
                            String[] temp = tabName.split("##");

                            switch (temp.length) {
                                case 3:
                                    properties.put(AopConstants.TITLE, temp[2]);
                                case 2:
                                    properties.put(AopConstants.SCREEN_NAME, temp[1]);
                                case 1:
                                    properties.put(AopConstants.ELEMENT_CONTENT, temp[0]);
                                    break;
                            }
                        }
                    } catch (Exception e) {
                        properties.put(AopConstants.ELEMENT_CONTENT, tabName);
                        e.printStackTrace();
                    }

                    //Action
//                    properties.put(AopConstants.ELEMENT_ACTION, "onTabChanged");

                    properties.put(AopConstants.ELEMENT_TYPE, "TabHost");

                    AnalyticsDataAPI.sharedInstance().track(AopConstants.APP_CLICK_EVENT_NAME, properties);
                } catch (Exception e) {
                    e.printStackTrace();
                    LogUtils.i(TAG, " onTabChanged AOP ERROR: " + e.getMessage());
                }
            }
        });
    }
}
