package cn.weli.analytics;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class AnalyticsData3DDetector {
    private SensorManager sensorManager;
    private SensorEvent sensorEvent;
    private boolean mEnabled = false;

    public AnalyticsData3DDetector(Context context) {
        // 获取系统的传感器管理服务
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }

    public void enable(){
        if (sensorManager == null){
            return;
        }
        // 为系统的加速度传感器注册监听器
        if (!mEnabled){
            sensorManager.registerListener(mSensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL);
            mEnabled = true;
        }
    }

    public void disable(){
        if (sensorManager == null) {
            return;
        }
        if (mEnabled) {
            sensorManager.unregisterListener(mSensorListener);
            mEnabled = false;
        }
    }

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // 获得x,y,z坐标
            sensorEvent = event;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    public SensorEvent getSensorEvent() {
        return sensorEvent;
    }

}
