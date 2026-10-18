package com.shrimpfarm.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.shrimpfarm.app.mixcalc.MixCalcActivity;

/**
 * 沙盒播种器：把批次 id 与 7 项基础数据塞进桩 DatabaseHelper，然后直接进计算器表格。
 * 这样每次冷启动都能拿到一个可编辑的 30 行表格，不用先去建批次/填基础数据。
 */
public class MainActivity extends Activity {

    public static final String BATCH_ID = "calclab-b1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences appPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        appPrefs.edit().putString("current_batch_id", BATCH_ID).apply();

        DatabaseHelper db = DatabaseHelper.getInstance(this);
        db.putBasicData(BATCH_ID, "seed_quantity", "10000");
        db.putBasicData(BATCH_ID, "pond_count", "20");
        db.putBasicData(BATCH_ID, "pond_length", "30");
        db.putBasicData(BATCH_ID, "aerator_count", "2");
        db.putBasicData(BATCH_ID, "aeration_power", "1.5");
        db.putBasicData(BATCH_ID, "stocking_date", "2026-05-01");
        db.putBasicData(BATCH_ID, "feed_brand", "calclab");

        startActivity(new Intent(this, MixCalcActivity.class));
        finish();
    }
}
