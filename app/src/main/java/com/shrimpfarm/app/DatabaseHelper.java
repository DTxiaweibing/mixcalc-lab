package com.shrimpfarm.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 沙盒桩：MixCalcActivity 只调用 getInstance() 与 getBasicData(batchId, key)。
 * 用 SharedPreferences 代替 SQLite，语义保持一致：查不到返回 ""。
 */
public class DatabaseHelper {

    private static final String PREFS = "calclab_basic_data";

    private static volatile DatabaseHelper instance;
    private final Context context;

    private DatabaseHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context);
        }
        return instance;
    }

    public String getBasicData(String batchId, String key) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getString(batchId + "::" + key, "");
    }

    public void putBasicData(String batchId, String key, String value) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(batchId + "::" + key, value).apply();
    }
}
