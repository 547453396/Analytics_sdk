package cn.weli.analytics;

public interface FieldConstant {

    /**
     * app信息
     * 应用编号,如99817749
     * 应用版本名,如2.0.1
     * 应用版本的code，如201
     * 渠道
     * 包名
     * sdk的版本名
     */
    String APP = "app";
    String APP_KEY = "app_key";
    String APP_VERSION = "app_version";
    String APP_VERSION_CODE = "app_version_code";
    String CHANNEL = "channel";
    String PKG = "pkg";
    String SDK_VERSION = "sdk_version";

    /**
     * device 信息
     * 国际移动设备标识
     * 国际运营商标识
     * mac地址
     * 设备的唯一标识,md5(imei+mac/idfa)
     * android_id
     * 系统 Android
     * 系统版本 "5.0.1"
     * 手机采用的语言
     * 手机型号 "HUAWEI G750-T01"
     * 设备品牌 "Huawei"
     * 整个产品的名称 "G750-T01"
     * 设备基板名称 "TAG-TL00"
     * 设备的网络环境 "4G"
     * 分辨率 "1080*1920"
     * 时区
     */
    String IMEI = "imei";
    String IMSI = "imsi";
    String MAC  = "mac";
    String DEVICE_ID = "device_id";
    String ANDROID_ID = "android_id";
    String OS = "os";
    String OS_VERSION = "os_version";
    String LANG = "lang";
    String MODEL = "model";
    String BRAND = "brand";
    String PRODUCT = "product";
    String BOARD = "board";
    String NETWORK = "network";
    String SCREEN_HEIGHT = "screen_height";
    String SCREEN_WIDTH = "screen_width";
    String TIME_ZONE = "time_zone";
    String HARDWARE = "hardware";

    /**
     * qemu: Whether this build was for an emulator device.为1则表示
     * 是否有蓝牙功能
     * 蓝牙功能是否可用
     */
    String IS_ROOT = "is_root";
    String QEMU = "qemu";
    String HAS_BLUETOOTH = "has_bluetooth";
    String DISABLE_BLUETOOTH = "disable_bluetooth";
    String HAS_GPS = "has_gps";
    String HAS_TEMPERATURE = "has_temperature";
    String BATTERY_STATUS = "battery_status";
    String BATTERY_LEVEL = "battery_level";
    String BATTERY_TEMPERATURE = "battery_temperature";

    /**
     * 屏幕方向 portrait/landscape  统计时再加入
     */
    String SCREEN_ORIENTATION = "screen_orientation";

    /**
     * 纬度
     * 经度
     * 城市
     */
    String LAT = "lat";
    String LON = "lon";
    String CITY_KEY = "city_key";

    /**
     * 注册用户的uid
     * 每一次session，生成的唯一标识，推荐uuid
     */
    String UID = "uid";
    String SESSION_ID = "session_id";

    /**
     * 这些统计封装数据时再加入
     * 3d感应的x轴坐标值
     * 3d感应的y轴坐标值
     * 3d感应的z轴坐标值
     */
    String X3D = "x3d";
    String Y3D = "y3d";
    String Z3D = "z3d";

    /**
     * event_type 事件类型，计数类型="counter"、计时器类型="timer"、自定义="custom"
     * event_name 事件名称 "view"
     * event_id 事件发生的唯一标识，建议uuid
     * 事件发生的时间戳
     * 扩展参数
     */
    String EVENT_TYPE = "event_type";
    String EVENT_NAME = "event_name";
    String EVENT_ID = "event_id";
    String EVENT_TIME = "event_time";
    String ARGS = "args";
    String RESUME_FROM_BACKGROUND = "resume_from_background";
    String IS_FIRST_TIME = "is_first_time";

    /**
     * 事件发生的事件周期，单位毫秒。主要应用于统计时长方向
     * 页面元素id，主要对于一些页面或者固定的页面元素统计时，作为统计载体的唯一标识
     * 模块
     * 位置模型
     * 内容的id
     * 内容模型
     */
    String DURATION = "duration";
    String ELEMENT_ID = "element_id";
    String MODULE = "module";
    String POSITION = "position";
    String CONTENT_ID = "content_id";
    String CONTENT_MODEL = "content_model";



}
