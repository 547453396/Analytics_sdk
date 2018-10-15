package cn.weli.analytics.aop;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.MenuItem;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.json.JSONObject;

import cn.weli.analytics.AnalyticsDataAPI;
import cn.weli.analytics.utils.LogUtils;

/**
 * RatingBar.OnSeekBarChangeListener事件
 */

@Aspect
public class MenuItemSelectedAspectj {
    private final static String TAG = MenuItemSelectedAspectj.class.getCanonicalName();

    @After("execution(* android.app.Activity.onOptionsItemSelected(android.view.MenuItem))")
    public void onOptionsItemSelectedAOP(JoinPoint joinPoint) throws Throwable {
        onMenuClick(joinPoint, 0, "onOptionsItemSelected");
    }

    @After("execution(* android.app.Activity.onContextItemSelected(android.view.MenuItem))")
    public void onContextItemSelectedAOP(JoinPoint joinPoint) throws Throwable {
        onMenuClick(joinPoint, 0, "onContextItemSelected");
    }

    @After("execution(* android.app.Activity.onMenuItemSelected(int, android.view.MenuItem))")
    public void onMenuItemSelectedAOP(JoinPoint joinPoint) throws Throwable {
        onMenuClick(joinPoint, 1, "onMenuItemSelected");
    }

    private void onMenuClick(final JoinPoint joinPoint, final int menuItemIndex, final String action) {
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
                    if (joinPoint == null || joinPoint.getArgs() == null || joinPoint.getArgs().length == 0) {
                        return;
                    }

                    //获取被点击的 MenuItem
                    MenuItem menuItem = (MenuItem) joinPoint.getArgs()[menuItemIndex];
                    if (menuItem == null) {
                        return;
                    }

                    //MenuItem 被忽略
                    if (AopUtil.isViewIgnored(MenuItem.class)) {
                        return;
                    }

                    //获取所在的 Context
                    Object object = joinPoint.getTarget();
                    if (object == null) {
                        return;
                    }

                    Context context = null;
                    if (object instanceof Context) {
                        context = (Context) object;
                    }
                    if (context == null) {
                        return;
                    }

                    //将 Context 转成 Activity
                    Activity activity = AopUtil.getActivityFromContext(context, null);

                    //Activity 被忽略
                    if (activity != null) {
                        if (AnalyticsDataAPI.sharedInstance().isActivityAutoTrackIgnored(activity.getClass())) {
                            return;
                        }
                    }

                    //获取View ID
                    String idString = null;
                    try {
                        idString = context.getResources().getResourceEntryName(menuItem.getItemId());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    JSONObject properties = new JSONObject();

                    //$screen_name & $title
                    if (activity != null) {
                        properties.put(AopConstants.SCREEN_NAME, activity.getClass().getCanonicalName());
                        String activityTitle = AopUtil.getActivityTitle(activity);
                        if (!TextUtils.isEmpty(activityTitle)) {
                            properties.put(AopConstants.TITLE, activityTitle);
                        }
                    }

                    //ViewID
                    if (!TextUtils.isEmpty(idString)) {
                        properties.put(AopConstants.ELEMENT_ID, idString);
                    }

                    //Action
//                    if (!TextUtils.isEmpty(action)) {
//                        properties.put(AopConstants.ELEMENT_ACTION, action);
//                    }

                    //Content
                    if (!TextUtils.isEmpty(menuItem.getTitle())) {
                        properties.put(AopConstants.ELEMENT_CONTENT, menuItem.getTitle());
                    }

                    //Type
                    properties.put(AopConstants.ELEMENT_TYPE, "MenuItem");

                    AnalyticsDataAPI.sharedInstance().track(AopConstants.APP_CLICK_EVENT_NAME, properties);
                } catch (Exception e) {
                    e.printStackTrace();
                    LogUtils.i(TAG, action + " error: " + e.getMessage());
                }
            }
        });
    }
}
