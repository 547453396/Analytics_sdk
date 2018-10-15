package cn.weli.analytics.aop;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.widget.Spinner;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.json.JSONObject;

import cn.weli.analytics.R;
import cn.weli.analytics.AnalyticsDataAPI;
import cn.weli.analytics.utils.LogUtils;

/**
 * spinner.setOnItemSelectedListener 事件
 */

@Aspect
public class SpinnerOnItemSelectedAspectj {
    private final static String TAG = SpinnerOnItemSelectedAspectj.class.getCanonicalName();

    @After("execution(* android.widget.AdapterView.OnItemSelectedListener.onItemSelected(android.widget.AdapterView,android.view.View,int,long))")
    public void onItemSelectedAOP(final JoinPoint joinPoint) throws Throwable {
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
                    if (joinPoint == null || joinPoint.getArgs() == null || joinPoint.getArgs().length != 4) {
                        return;
                    }

                    //获取被点击的 View
                    android.widget.AdapterView adapterView = (android.widget.AdapterView) joinPoint.getArgs()[0];
                    if (adapterView == null) {
                        return;
                    }

                    //获取所在的 Context
                    Context context = adapterView.getContext();
                    if (context == null) {
                        return;
                    }

                    //将 Context 转成 Activity
                    Activity activity = AopUtil.getActivityFromContext(context, adapterView);

                    //Activity 被忽略
                    if (activity != null) {
                        if (AnalyticsDataAPI.sharedInstance().isActivityAutoTrackIgnored(activity.getClass())) {
                            return;
                        }
                    }

                    //View 被忽略
                    if (AopUtil.isViewIgnored(adapterView)) {
                        return;
                    }

                    //position
                    int position = (int) joinPoint.getArgs()[2];

                    JSONObject properties = new JSONObject();

                    //ViewId
                    String idString = AopUtil.getViewId(adapterView);
                    if (!TextUtils.isEmpty(idString)) {
                        properties.put(AopConstants.ELEMENT_ID, idString);
                    }

                    //Action
//                    properties.put(AopConstants.ELEMENT_ACTION, "onItemSelected");

                    //$screen_name & $title
                    if (activity != null) {
                        properties.put(AopConstants.SCREEN_NAME, activity.getClass().getCanonicalName());
                        String activityTitle = AopUtil.getActivityTitle(activity);
                        if (!TextUtils.isEmpty(activityTitle)) {
                            properties.put(AopConstants.TITLE, activityTitle);
                        }
                    }

                    if (adapterView instanceof Spinner) { // Spinner
                        properties.put(AopConstants.ELEMENT_TYPE, "Spinner");
                        Object item = adapterView.getItemAtPosition(position);
                        properties.put(AopConstants.ELEMENT_POSITION, String.valueOf(position));
                        if (item != null) {
                            if (item instanceof String) {
                                properties.put(AopConstants.ELEMENT_CONTENT, item);
                            }
                        }
                    } else {
                        properties.put(AopConstants.ELEMENT_TYPE, adapterView.getClass().getCanonicalName());
                    }

                    //fragmentName
                    AopUtil.getFragmentNameFromView(adapterView, properties);

                    //获取 View 自定义属性
                    JSONObject p = (JSONObject) adapterView.getTag(R.id.analytics_tag_view_properties);
                    if (p != null) {
                        AopUtil.mergeJSONObject(p, properties);
                    }

                    AnalyticsDataAPI.sharedInstance().track(AopConstants.APP_CLICK_EVENT_NAME, properties);
                } catch (Exception e) {
                    e.printStackTrace();
                    LogUtils.i(TAG, " AdapterView.OnItemSelectedListener.onItemSelected AOP ERROR: " + e.getMessage());
                }
            }
        });
    }
}
