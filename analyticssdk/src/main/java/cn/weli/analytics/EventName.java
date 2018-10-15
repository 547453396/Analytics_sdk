package cn.weli.analytics;

public enum EventName {
    APP_START("app_start"),//应用启动
    APP_END("app_end"),//应用退出
    APP_UPGRADE("app_upgrade"),//应用升级事件
    PAGE_VIEW_START("page_view_start"),//页面pv开始
    PAGE_VIEW_END("page_view_end"),//页面pv结束
    APP_INSTALL_SCAN("app_install_scan"),//应用安装列表扫描事件
    VIEW("view"),//view
    CLICK("click"),//click
    PUSH_REGISTER("push_register"),//推送注册事件
    PUSH_MSG_CLICK("push_msg_click"),//推送消息点击事件
    PUSH_MSG_VIEW("push_msg_view"),//推送消息展示事件
    PUSH_MSG_RECEIVE("push_msg_receive");//推送收到确认事件

    EventName(String eventName) {
        this.eventName = eventName;
    }

    public String getEventName() {
        return eventName;
    }

    private String eventName;
}
