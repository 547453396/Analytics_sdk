package cn.weli.analytics.aop;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.json.JSONObject;

import cn.weli.analytics.R;
import cn.weli.analytics.AnalyticsDataAPI;
import cn.weli.analytics.utils.LogUtils;

/**
 * Dialog.OnClickListener.onClick 事件
 */

@Aspect
public class DialogOnClickAspectj {
    private final static String TAG = DialogOnClickAspectj.class.getCanonicalName();

    @After("execution(* android.content.DialogInterface.OnClickListener.onClick(android.content.DialogInterface, int))")
    public void onClickAOP(final JoinPoint joinPoint) throws Throwable {
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

                    //获取被点击的View
                    DialogInterface dialogInterface = (DialogInterface) joinPoint.getArgs()[0];
                    if (dialogInterface == null) {
                        return;
                    }

                    int whichButton = (int) joinPoint.getArgs()[1];

                    //获取所在的Context
                    Dialog dialog = null;
                    if (dialogInterface instanceof Dialog) {
                        dialog = (Dialog) dialogInterface;
                    }

                    if (dialog == null) {
                        return;
                    }

                    Context context = dialog.getContext();

                    //将Context转成Activity
                    Activity activity = AopUtil.getActivityFromContext(context, null);

                    if (activity == null) {
                        activity = dialog.getOwnerActivity();
                    }

                    //Activity 被忽略
                    if (activity != null) {
                        if (AnalyticsDataAPI.sharedInstance().isActivityAutoTrackIgnored(activity.getClass())) {
                            return;
                        }
                    }

                    //Dialog 被忽略
                    if (AopUtil.isViewIgnored(Dialog.class)) {
                        return;
                    }

                    JSONObject properties = new JSONObject();

                    try {
                        if (dialog.getWindow() != null) {
                            String idString = (String) dialog.getWindow().getDecorView().getTag(R.id.analytics_tag_view_id);
                            if (!TextUtils.isEmpty(idString)) {
                                properties.put(AopConstants.ELEMENT_ID, idString);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    //$screen_name & $title
                    if (activity != null) {
                        properties.put(AopConstants.SCREEN_NAME, activity.getClass().getCanonicalName());
                        String activityTitle = AopUtil.getActivityTitle(activity);
                        if (!TextUtils.isEmpty(activityTitle)) {
                            properties.put(AopConstants.TITLE, activityTitle);
                        }
                    }

                    properties.put(AopConstants.ELEMENT_TYPE, "Dialog");
//                    properties.put(AopConstants.ELEMENT_ACTION, "onClick");

                    if (dialog instanceof android.app.AlertDialog) {
                        android.app.AlertDialog alertDialog = (android.app.AlertDialog) dialog;
                        Button button = alertDialog.getButton(whichButton);
                        if (button != null) {
                            if (!TextUtils.isEmpty(button.getText())) {
                                properties.put(AopConstants.ELEMENT_CONTENT, button.getText());
                            }
                        } else {
                            ListView listView = alertDialog.getListView();
                            if (listView != null) {
                                ListAdapter listAdapter = listView.getAdapter();
                                Object object = listAdapter.getItem(whichButton);
                                if (object != null) {
                                    if (object instanceof String) {
                                        properties.put(AopConstants.ELEMENT_CONTENT, (String) object);
                                    }
                                }
                            }
                        }

                    } else if (dialog instanceof android.support.v7.app.AlertDialog) {
                        android.support.v7.app.AlertDialog alertDialog = (android.support.v7.app.AlertDialog) dialog;
                        Button button = alertDialog.getButton(whichButton);
                        if (button != null) {
                            if (!TextUtils.isEmpty(button.getText())) {
                                properties.put(AopConstants.ELEMENT_CONTENT, button.getText());
                            }
                        } else {
                            ListView listView = alertDialog.getListView();
                            if (listView != null) {
                                ListAdapter listAdapter = listView.getAdapter();
                                Object object = listAdapter.getItem(whichButton);
                                if (object != null) {
                                    if (object instanceof String) {
                                        properties.put(AopConstants.ELEMENT_CONTENT, (String) object);
                                    }
                                }
                            }
                        }
                    }

                    AnalyticsDataAPI.sharedInstance().track(AopConstants.APP_CLICK_EVENT_NAME, properties);
                } catch (Exception e) {
                    e.printStackTrace();
                    LogUtils.i(TAG, " DialogInterface.OnClickListener.onClick AOP ERROR: " + e.getMessage());
                }
            }
        });
    }

    @After("execution(* android.content.DialogInterface.OnMultiChoiceClickListener.onClick(android.content.DialogInterface, int, boolean))")
    public void onMultiChoiceClickAOP(final JoinPoint joinPoint) throws Throwable {
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
                    if (joinPoint == null || joinPoint.getArgs() == null || joinPoint.getArgs().length != 3) {
                        return;
                    }

                    //获取被点击的View
                    DialogInterface dialogInterface = (DialogInterface) joinPoint.getArgs()[0];
                    if (dialogInterface == null) {
                        return;
                    }

                    int whichButton = (int) joinPoint.getArgs()[1];

                    boolean isChecked = (boolean) joinPoint.getArgs()[2];

                    //获取所在的Context
                    Dialog dialog = null;
                    if (dialogInterface instanceof Dialog) {
                        dialog = (Dialog) dialogInterface;
                    }

                    if (dialog == null) {
                        return;
                    }

                    Context context = dialog.getContext();

                    //将Context转成Activity
                    Activity activity = null;
                    if (context instanceof Activity) {
                        activity = (Activity) context;
                    }

                    //Activity 被忽略
                    if (activity != null) {
                        if (AnalyticsDataAPI.sharedInstance().isActivityAutoTrackIgnored(activity.getClass())) {
                            return;
                        }
                    }

                    //Dialog 被忽略
                    if (AopUtil.isViewIgnored(Dialog.class)) {
                        return;
                    }

                    JSONObject properties = new JSONObject();

                    try {
                        if (dialog.getWindow() != null) {
                            String idString = (String) dialog.getWindow().getDecorView().getTag(R.id.analytics_tag_view_id);
                            if (!TextUtils.isEmpty(idString)) {
                                properties.put(AopConstants.ELEMENT_ID, idString);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    //$screen_name & $title
                    if (activity != null) {
                        properties.put(AopConstants.SCREEN_NAME, activity.getClass().getCanonicalName());
                        String activityTitle = AopUtil.getActivityTitle(activity);
                        if (!TextUtils.isEmpty(activityTitle)) {
                            properties.put(AopConstants.TITLE, activityTitle);
                        }
                    }

                    properties.put(AopConstants.ELEMENT_TYPE, "Dialog");
//                    if (isChecked) {
//                        properties.put(AopConstants.ELEMENT_ACTION, "checked");
//                    } else {
//                        properties.put(AopConstants.ELEMENT_ACTION, "unchecked");
//                    }

                    if (dialog instanceof android.app.AlertDialog) {
                        android.app.AlertDialog alertDialog = (android.app.AlertDialog) dialog;
                        Button button = alertDialog.getButton(whichButton);
                        if (button != null) {
                            if (!TextUtils.isEmpty(button.getText())) {
                                properties.put(AopConstants.ELEMENT_CONTENT, button.getText());
                            }
                        } else {
                            ListView listView = alertDialog.getListView();
                            if (listView != null) {
                                ListAdapter listAdapter = listView.getAdapter();
                                Object object = listAdapter.getItem(whichButton);
                                if (object != null) {
                                    if (object instanceof String) {
                                        properties.put(AopConstants.ELEMENT_CONTENT, (String) object);
                                    }
                                }
                            }
                        }

                    } else if (dialog instanceof android.support.v7.app.AlertDialog) {
                        android.support.v7.app.AlertDialog alertDialog = (android.support.v7.app.AlertDialog) dialog;
                        Button button = alertDialog.getButton(whichButton);
                        if (button != null) {
                            if (!TextUtils.isEmpty(button.getText())) {
                                properties.put(AopConstants.ELEMENT_CONTENT, button.getText());
                            }
                        } else {
                            ListView listView = alertDialog.getListView();
                            if (listView != null) {
                                ListAdapter listAdapter = listView.getAdapter();
                                Object object = listAdapter.getItem(whichButton);
                                if (object != null) {
                                    if (object instanceof String) {
                                        properties.put(AopConstants.ELEMENT_CONTENT, (String) object);
                                    }
                                }
                            }
                        }
                    }

                    AnalyticsDataAPI.sharedInstance().track(AopConstants.APP_CLICK_EVENT_NAME, properties);
                } catch (Exception e) {
                    e.printStackTrace();
                    LogUtils.i(TAG, " DialogInterface.OnMultiChoiceClickListener.onClick AOP ERROR: " + e.getMessage());
                }
            }
        });
    }
}
