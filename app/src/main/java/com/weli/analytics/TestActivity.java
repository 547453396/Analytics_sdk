package com.weli.analytics;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

public class TestActivity extends Activity implements View.OnClickListener{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
    }

    @Override
    public void onClick(View v) {

    }
}
