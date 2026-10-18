package com.shrimpfarm.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** 沙盒占位：MixCalcActivity 里有跳转到它，这里只用来证明跳转可达。 */
public class BasicDataActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("calclab stub: BasicDataActivity");
        tv.setPadding(48, 48, 48, 48);
        setContentView(tv);
    }
}
