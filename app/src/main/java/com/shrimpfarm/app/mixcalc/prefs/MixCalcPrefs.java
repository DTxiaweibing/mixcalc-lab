package com.shrimpfarm.app.mixcalc.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.shrimpfarm.app.mixcalc.model.FeedData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * 本地持久化：所有 SharedPreferences 读写集中在这里，Activity 不再直接操作 prefs。
 */
public final class MixCalcPrefs {

    private static final String TAG = "MixCalcPrefs";
    private static final String PREFS_NAME = "MixCalcPrefs";

    private static final String KEY_GLOBAL_WATER = "global_water";
    private static final String KEY_FERMENTED_WATER = "fermented_included_in_water";
    private static final String KEY_FERMENTED_AVERAGE = "fermented_included_in_average";
    private static final String KEY_SAVED_DATA = "saved_data";
    private static final String KEY_COLUMN_VISIBILITY = "column_visibility_";
    private static final String KEY_FIXED_COLUMN = "fixed_column_";
    private static final String KEY_PRIVACY_AGREED = "privacy_agreed_version";

    private MixCalcPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int getWaterPercentage(Context context) {
        return prefs(context).getInt(KEY_GLOBAL_WATER, 20);
    }

    public static boolean isFermentedInWater(Context context) {
        return prefs(context).getBoolean(KEY_FERMENTED_WATER, false);
    }

    public static boolean isFermentedInAverage(Context context) {
        return prefs(context).getBoolean(KEY_FERMENTED_AVERAGE, false);
    }

    public static void saveWaterPercentage(Context context, int value) {
        prefs(context).edit().putInt(KEY_GLOBAL_WATER, value).apply();
    }

    public static void saveFermentedWater(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_FERMENTED_WATER, value).apply();
    }

    public static void saveFermentedAverage(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_FERMENTED_AVERAGE, value).apply();
    }

    /**
     * 读取已保存表格并回填到 rows。
     * skipRow 指定的行不在这里重算，交给首行锚点逻辑处理。
     */
    public static void loadData(Context context, List<FeedData> rows, int maxRows,
                                int waterPercentage, boolean fermentedInWater,
                                boolean fermentedInAverage, int skipRow) {
        try {
            String json = prefs(context).getString(KEY_SAVED_DATA, null);
            if (json == null || json.isEmpty()) {
                return;
            }
            JSONArray array = new JSONArray(json);
            int count = Math.min(array.length(), maxRows);
            for (int i = 0; i < count; i++) {
                try {
                    JSONObject object = array.getJSONObject(i);
                    FeedData data = rows.get(i);
                    if (data == null) {
                        data = new FeedData();
                        rows.set(i, data);
                    }
                    data.fromJson(object);
                    if (i != skipRow) {
                        data.updateAllCalculations(waterPercentage, fermentedInWater, fermentedInAverage);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析第" + i + "行数据异常", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "加载数据异常", e);
        }
    }

    public static void saveData(Context context, List<FeedData> rows) {
        try {
            JSONArray array = new JSONArray();
            for (FeedData data : rows) {
                array.put(data.toJson());
            }
            prefs(context).edit().putString(KEY_SAVED_DATA, array.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "保存数据异常", e);
        }
    }

    public static void loadColumnVisibility(Context context, String[] columnIds,
                                            boolean forceVisible, Map<String, Boolean> out) {
        SharedPreferences p = prefs(context);
        for (int i = 1; i < columnIds.length; i++) {
            String id = columnIds[i];
            out.put(id, forceVisible || p.getBoolean(KEY_COLUMN_VISIBILITY + id, true));
        }
    }

    public static void saveColumnVisibility(Context context, String[] columnIds,
                                            Map<String, Boolean> map) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (int i = 1; i < columnIds.length; i++) {
            String id = columnIds[i];
            editor.putBoolean(KEY_COLUMN_VISIBILITY + id, Boolean.TRUE.equals(map.get(id)));
        }
        editor.apply();
    }

    public static void loadColumnFixedState(Context context, String[] columnIds,
                                            Map<String, Boolean> out) {
        SharedPreferences p = prefs(context);
        for (int i = 1; i < columnIds.length; i++) {
            String id = columnIds[i];
            out.put(id, p.getBoolean(KEY_FIXED_COLUMN + id, false));
        }
    }

    public static void saveColumnFixedState(Context context, String[] columnIds,
                                            Map<String, Boolean> map) {
        SharedPreferences.Editor editor = prefs(context).edit();
        for (int i = 1; i < columnIds.length; i++) {
            String id = columnIds[i];
            editor.putBoolean(KEY_FIXED_COLUMN + id, Boolean.TRUE.equals(map.get(id)));
        }
        editor.apply();
    }

    public static int getPrivacyAgreedVersion(Context context) {
        return prefs(context).getInt(KEY_PRIVACY_AGREED, 0);
    }

    public static void setPrivacyAgreedVersion(Context context, int versionCode) {
        prefs(context).edit().putInt(KEY_PRIVACY_AGREED, versionCode).apply();
    }
}