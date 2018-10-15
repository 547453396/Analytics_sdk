##事件采集协议


### 事件提交的协议报文

+ url

+ method: POST

+ parameters:
 
  + x: 事件列表加密后，然后压缩的编码串
  + cps: 压缩算法

### 事件列表数据格式

因为需要支持批量提交数据，因此上传报文为一个array

```
[
    {
        "device": {
            "imei": "",
            "mac": "",
            "idfa": "",
            "imsi": "",
            "android_id": "",
            "device_id": "",
            "os": "",
            "os_version": "7.2.0",
            "lang": "zh-cn",
            "model": "iPhone",
            "brand": "Apple",
            "product": "iPhone6",
            "board": "",
            "network": "4G",
            "qemu": "1",
            "has_buletooth": true,
            "disable_bluetooth": true,
            "hardware": "goldfish, vbox86",
            "has_gps": true,
            "has_temperature": true,
            "battery_status": "2",
            "battery_level": "100",
            "battery_temperature": "20",
            "screen_height": 1920,
            "screen_width": 1080
        },
        "app": {
            "app_key": 99817749,
            "app_version": "1.0.3",
            "app_version_code": 103,
            "sdk_version": "3.0.1",
            "channel": "huawei",
            "pkg": "suishen.wlkk"
        },
        "locate": {
            "city_key": "",
            "lat": "0.0000001",
            "lon": "0.000001"
        },
        "event": {
            "session_id": "1111-2222-2333",
            "screen_orientation": "portrait",
            "x3d": 1.000,
            "y3d": 2.000,
            "z3d": 3.000,
            "touch_x": 1000.00,
            "touch_y": 200.000,
            "uid": "1232344",
            "event_type": "counter",
            "event_name": "view",
            "event_id": "11233-ddd-ffff",
            "duration": 120000,
            "element_id": "feed_main_page",
            "module": 1,
            "position": "-1.2.3",
            "content_id": 1222,
            "content_model": {
                "project": "peacock",
                "table": "wnl_life_card_item",
                "md": 1,
                "id": 1222
            },
            "args": {}
        }
    }
]

```
