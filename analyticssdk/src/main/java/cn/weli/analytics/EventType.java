package cn.weli.analytics;

public enum EventType {
    COUNTER("counter", true, false),//计数类型
    TIMER("timer", true, false),//计时器类型
    CUSTOM("custom", true, false),//自定义
    SCANNER("scanner", true, false),//安装列表扫描事件
    TRACK("track", true, false),
    TRACK_SIGNUP("track_signup", true, false),
    PROFILE_SET("profile_set", false, true),
    PROFILE_SET_ONCE("profile_set_once", false, true),
    PROFILE_UNSET("profile_unset", false, true),
    PROFILE_INCREMENT("profile_increment", false, true),
    PROFILE_APPEND("profile_append", false, true),
    PROFILE_DELETE("profile_delete", false, true),
    REGISTER_SUPER_PROPERTIES("register_super_properties", false, false);

    EventType(String eventType, boolean isTrack, boolean isProfile) {
        this.eventType = eventType;
        this.track = isTrack;
        this.profile = isProfile;
    }

    public String getEventType() {
        return eventType;
    }

    public boolean isTrack() {
        return track;
    }

    public boolean isProfile() {
        return profile;
    }

    private String eventType;
    private boolean track;
    private boolean profile;
}
