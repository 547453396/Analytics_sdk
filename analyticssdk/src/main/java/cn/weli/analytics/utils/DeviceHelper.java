package cn.weli.analytics.utils;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import static android.content.Context.MODE_PRIVATE;

/**
 * 将imei、imsi、mac地址存储到system目录下，在用户卸载时不清除，保证这些值不变，保证设备的唯一性
 */
public class DeviceHelper {

    private static final String TEMP_DIR = Environment.getExternalStorageDirectory().getPath()+"/system/";
    private static String temp = "info";
    public static final String IMEI = "imei";
    public static final String IMSI = "imsi";
    public static final String MAC = "mac";

    /**
     * 获取imei、imsi、mac
     *
     * @return
     */
    public static String getDeviceInfo() {
        String macImeiImsi = "";
        File file = new File(TEMP_DIR + temp);
        if (file.exists()) {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file)));
                macImeiImsi = reader.readLine();
                reader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (TextUtils.isEmpty(macImeiImsi)){
            macImeiImsi = "";
        }
        return macImeiImsi;
    }

    /**
     * 写入imei、imsi、mac
     *
     * @param content
     */
    public static void writeDeviceInfo(String content) {
        if (TextUtils.isEmpty(content)) {
            return;
        }
        File dir = new File(TEMP_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(TEMP_DIR + temp);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            BufferedOutputStream out = new BufferedOutputStream(
                    new FileOutputStream(file));
            out.write(content.getBytes());
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        } catch (OutOfMemoryError error) {
        }
    }

    /**
     * 写入相应key、value值
     *
     * @param key
     * @param value
     */
    public static void writeValue(String key, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        String content = getDeviceInfo();
        JSONObject object = null;
        try {
            if (TextUtils.isEmpty(content)){
                object = new JSONObject();
            }else {
                object = new JSONObject(content);
            }
            object.put(key, value);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (object == null) {
            return;
        }
        File dir = new File(TEMP_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(TEMP_DIR + temp);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            BufferedOutputStream out = new BufferedOutputStream(
                    new FileOutputStream(file));
            out.write(object.toString().getBytes());
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        } catch (OutOfMemoryError error) {
        }
    }

    public static String getDeviceValue(String key) {
        String value = "";
        String deviceInfo = getDeviceInfo();
        if (!TextUtils.isEmpty(deviceInfo)) {
            try {
                JSONObject jsonObject = new JSONObject(deviceInfo);
                value = jsonObject.optString(key);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return value;
    }

    /**
     * 获取imei，先从缓存获取，未获取到从sdcard中获取，再未获取到则原始获取
     */
    public static String getImei(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("imei_mac", MODE_PRIVATE);
        String imei = sp.getString("device_imei", "");
        if (TextUtils.isEmpty(imei)) {
            imei = DeviceHelper.getDeviceValue(DeviceHelper.IMEI);
            if (TextUtils.isEmpty(imei)){
                TelephonyManager tm = (TelephonyManager) ctx
                        .getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        return "";
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        imei = tm.getImei();
                    } else {
                        imei = tm.getDeviceId();
                    }
                    if (!TextUtils.isEmpty(imei)){
                        sp.edit().putString("device_imei",imei).apply();
                        DeviceHelper.writeValue(DeviceHelper.IMEI,imei);
                    }
                }
            }
        }
        return imei;
    }

    public static String getImsi(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("imei_mac", MODE_PRIVATE);
        String imsi = sp.getString("device_imsi", "");
        if (TextUtils.isEmpty(imsi)) {
            imsi = DeviceHelper.getDeviceValue(DeviceHelper.IMSI);
            if (TextUtils.isEmpty(imsi)){
                TelephonyManager tm = (TelephonyManager) ctx
                        .getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null) {
                    if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                        return "";
                    }
                    imsi = tm.getSubscriberId();
                    if (!TextUtils.isEmpty(imsi)){
                        sp.edit().putString("device_imsi",imsi).apply();
                        DeviceHelper.writeValue(DeviceHelper.IMSI,imsi);
                    }
                }
            }
        }
        return imsi;
    }

    public static String getMac(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("imei_mac", MODE_PRIVATE);
        String mac = sp.getString("device_mac", "");
        if (TextUtils.isEmpty(mac)) {
            mac = DeviceHelper.getDeviceValue(DeviceHelper.MAC);
            if (TextUtils.isEmpty(mac)){
                mac = AnalyticsDataUtils.getMacAddress(ctx);
                if (!TextUtils.isEmpty(mac)){
                    sp.edit().putString("imei_mac",mac).apply();
                    DeviceHelper.writeValue(DeviceHelper.MAC,mac);
                }
            }
        }
        return mac;
    }

    public static String getDeviceId(Context context) {
        String device_id = getImei(context) + getMac(context);
        return MD5.getMD5(device_id.getBytes());
    }

}