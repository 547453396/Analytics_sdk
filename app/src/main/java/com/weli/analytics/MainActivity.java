package com.weli.analytics;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import cn.weli.analytics.AnalyticsDataAPI;

public class MainActivity extends Activity implements View.OnClickListener{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView tv = (TextView)findViewById(R.id.tv_test);
        tv.setOnClickListener(this);
        Button btn_test = (Button)findViewById(R.id.btn_test);
        btn_test.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AnalyticsDataAPI.sharedInstance(this).trackViewScreen(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AnalyticsDataAPI.sharedInstance(this).trackViewScreenEnd(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tv_test){
            Log.i("MainActivity","tv_test onClick");
        }else if (v.getId() == R.id.btn_test){
            Log.i("MainActivity","btn_test onClick");
            startActivity(new Intent(this,TestActivity.class));
        }
    }
}
