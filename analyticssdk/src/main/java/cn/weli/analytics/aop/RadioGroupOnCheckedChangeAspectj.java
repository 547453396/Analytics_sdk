package cn.weli.analytics.aop;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.json.JSONObject;

import cn.weli.analytics.R;
import cn.weli.analytics.AnalyticsDataAPI;
import cn.weli.analytics.utils.LogUtils;

/**
 * RadioGroup 的 onCheckedChanged 事件
 */

@Aspect
public class RadioGroupOnCheckedChangeAspectj {
    private final static String TAG = RadioGroupOnCheckedChangeAspectj.class.getCanonicalName();

    @After("execution(* android.widget.RadioGroup.OnCheckedChangeListener.onCheckedChanged(android.widget.RadioGroup,int))")
    public void onCheckedChangedAOP(final JoinPoint joinPoint) throws Throwable {
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
                    if (joinPoint == null || joinPoint.getArgs() == null || joinPoint.getArgs().length != 2) {
                        return;
                    }

                    //获取被点击的 View
                    View view = (View) joinPoint.getArgs()[0];
                    if (view == null) {
                        return;
                    }

                    //获取所在的 Context
                    Context context = view.getContext();
                    if (context == null) {
                        return;
                    }

                    //将 Context 转成 Activity
                    Activity activity = AopUtil.getActivityFromContext(context, view);

                    //Activity 被忽略
                    if (activity != null) {
                        if (AnalyticsDataAPI.sharedInstance().isActivityAutoTrackIgnored(activity.getClass())) {
                            return;
                        }
                    }

                    //View 被忽略
                    if (AopUtil.isViewIgnored(view)) {
                        return;
                    }

                    JSONObject properties = new JSONObject();

                    //ViewId
                    String idString = AopUtil.getViewId(view);
                    if (!TextUtils.isEmpty(idString)) {
                        properties.put(AopConstants.ELEMENT_ID, idString);
                    }

                    //Action
//                    properties.put(AopConstants.ELEMENT_ACTION, "onCheckedChanged");

                    //$screen_name & $title
                    if (activity != null) {
                        properties.put(AopConstants.SCREEN_NAME, activity.getClass().getCanonicalName());
                        String activityTitle = AopUtil.getActivityTitle(activity);
                        if (!TextUtils.isEmpty(activityTitle)) {
                            properties.put(AopConstants.TITLE, activityTitle);
                        }
                    }

                    if (view instanceof RadioGroup) { // RadioGroup
                        properties.put(AopConstants.ELEMENT_TYPE, "RadioGroup");
                        RadioGroup radioGroup = (RadioGroup) view;

                        //获取变更后的选中项的ID
                        int checkedRadioButtonId = radioGroup.getCheckedRadioButtonId();
                        if (activity != null) {
                            try {
                                RadioButton radioButton = (RadioButton) activity.findViewById(checkedRadioButtonId);
                                if (radioButton != null) {
                                    if (!TextUtils.isEmpty(radioButton.getText())) {
                                        String viewText = radioButton.getText().toString();
                                        if (!TextUtils.isEmpty(viewText)) {
                                            properties.put(AopConstants.ELEMENT_CONTENT, viewText);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else {
                        properties.put(AopConstants.ELEMENT_TYPE, view.getClass().getCanonicalName());
                    }

                    //fragmentName
                    AopUtil.getFragmentNameFromView(view, properties);

                    //获取 View 自定义属性
                    JSONObject p = (JSONObject) view.getTag(R.id.analytics_tag_view_properties);
                    if (p != null) {
                        AopUtil.mergeJSONObject(p, properties);
                    }

                    AnalyticsDataAPI.sharedInstance().track(AopConstants.APP_CLICK_EVENT_NAME, properties);
                } catch (Exception e) {
                    e.printStackTrace();
                    LogUtils.i(TAG, "RadioGroup.OnCheckedChangeListener.onCheckedChanged AOP ERROR: " + e.getMessage());
                }
            }
        });
    }
}
