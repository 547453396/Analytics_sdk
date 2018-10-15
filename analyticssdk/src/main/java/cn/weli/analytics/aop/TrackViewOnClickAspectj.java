package cn.weli.analytics.aop;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.json.JSONObject;

import cn.weli.analytics.R;
import cn.weli.analytics.AnalyticsDataAPI;
import cn.weli.analytics.utils.LogUtils;

@Aspect
public class TrackViewOnClickAspectj {
    private final static String TAG = TrackViewOnClickAspectj.class.getCanonicalName();

    @Pointcut("execution(@cn.weli.analytics.AnalyticsDataTrackViewOnClick * *(..))")
    public void methodAnnotatedWithTrackEvent() {
    }

    @After("methodAnnotatedWithTrackEvent()")
    public void trackOnClickAOP(final JoinPoint joinPoint) throws Throwable {
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
//                    properties.put(AopConstants.ELEMENT_ACTION, "onClick");

                    //$screen_name & $title
                    if (activity != null) {
                        properties.put(AopConstants.SCREEN_NAME, activity.getClass().getCanonicalName());
                        String activityTitle = AopUtil.getActivityTitle(activity);
                        if (!TextUtils.isEmpty(activityTitle)) {
                            properties.put(AopConstants.TITLE, activityTitle);
                        }
                    }

                    String viewType = view.getClass().getCanonicalName();
                    CharSequence viewText = null;
                    if (view instanceof CheckBox) { // CheckBox
                        viewType = "CheckBox";
                        CheckBox checkBox = (CheckBox) view;
                        viewText = checkBox.getText();
//                        if (checkBox.isChecked()) {
//                            properties.put(AopConstants.ELEMENT_ACTION, "checked");
//                        } else {
//                            properties.put(AopConstants.ELEMENT_ACTION, "unchecked");
//                        }
                    } else if (view instanceof RadioButton) { // RadioButton
                        viewType = "RadioButton";
                        RadioButton radioButton = (RadioButton) view;
                        viewText = radioButton.getText();
//                        if (radioButton.isChecked()) {
//                            properties.put(AopConstants.ELEMENT_ACTION, "checked");
//                        } else {
//                            properties.put(AopConstants.ELEMENT_ACTION, "unchecked");
//                        }
                    } else if (view instanceof ToggleButton) { // ToggleButton
                        viewType = "ToggleButton";
                        ToggleButton toggleButton = (ToggleButton) view;
                        boolean isChecked = toggleButton.isChecked();
                        if (isChecked) {
                            viewText = toggleButton.getTextOn();
//                            properties.put(AopConstants.ELEMENT_ACTION, "checked");
                        } else {
                            viewText = toggleButton.getTextOff();
//                            properties.put(AopConstants.ELEMENT_ACTION, "unchecked");
                        }
                    } else if (view instanceof Button) { // Button
                        viewType = "Button";
                        Button button = (Button) view;
                        viewText = button.getText();
                    } else if (view instanceof CheckedTextView) { // CheckedTextView
                        viewType = "CheckedTextView";
                        CheckedTextView textView = (CheckedTextView) view;
                        viewText = textView.getText();
//                        if (textView.isChecked()) {
//                            properties.put(AopConstants.ELEMENT_ACTION, "checked");
//                        } else {
//                            properties.put(AopConstants.ELEMENT_ACTION, "unchecked");
//                        }
                    } else if (view instanceof TextView) { // TextView
                        viewType = "TextView";
                        TextView textView = (TextView) view;
                        viewText = textView.getText();
                    } else if (view instanceof ImageButton) { // ImageButton
                        viewType = "ImageButton";
                    } else if (view instanceof ImageView) { // ImageView
                        viewType = "ImageView";
                    }

                    //$element_content
                    if (!TextUtils.isEmpty(viewText)) {
                        properties.put(AopConstants.ELEMENT_CONTENT, viewText.toString());
                    }

                    //$element_type
                    properties.put(AopConstants.ELEMENT_TYPE, viewType);

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
                    LogUtils.i(TAG, "TrackViewOnClick error: " + e.getMessage());
                }
            }
        });

    }
}
