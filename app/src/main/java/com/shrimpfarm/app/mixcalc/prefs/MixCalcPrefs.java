package com.shrimpfarm.app.mixcalc.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.shrimpfarm.app.mixcalc.model.Batch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地持久化：所有 SharedPreferences 读写集中在这里。
 * 批次数据结构（v2）：
 *   batches_v2 = [ {water, fermentedWater, fermentedAverage, rows:[...]}, ... ]
 *   active_batch = 当前批次下标
 * 旧版（单批次）的 saved_data 会在首次读取时迁移到批次 0。
 */
public final class MixCalcPrefs {

    private static final String TAG = "MixCalcPrefs";
    private static final String PREFS_NAME = "MixCalcPrefs";

    /** 固定两个批次。 */
    public static final int BATCH_COUNT = 2;

    private static final String KEY_BATCHES = "batches_v2";
    private static final String KEY_ACTIVE_BATCH = "active_batch";

    // 旧版单批次的键，仅用于迁移
    private static final String KEY_LEGACY_SAVED_DATA = "saved_data";
    private static final String KEY_LEGACY_WATER = "global_water";
    private static final String KEY_LEGACY_FERMENTED_WATER = "fermented_included_in_water";
    private static final String KEY_LEGACY_FERMENTED_AVERAGE = "fermented_included_in_average";

    private static final String KEY_COLUMN_VISIBILITY = "column_visibility_";
    private static final String KEY_FIXED_COLUMN = "fixed_column_";
    private static final String KEY_PRIVACY_AGREED = "privacy_agreed_version";

    private MixCalcPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 读取两个批次；数据缺失或旧格式时自动迁移/补默认值。 */
    public static List<Batch> loadBatches(Context context, int rowCount) {
        List<Batch> batches = new ArrayList<>();
        String json = prefs(context).getString(KEY_BATCHES, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    batches.add(Batch.fromJson(array.optJSONObject(i), rowCount));
                }
            } catch (Exception e) {
                Log.e(TAG, "读取批次数据异常，回退默认值", e);
                batches.clear();
            }
        }
        boolean migrated = false;
        if (batches.isEmpty()) {
            batches = migrateLegacy(context, rowCount);
            migrated = true;
        }
        while (batches.size() < BATCH_COUNT) {
            batches.add(new Batch(rowCount));
        }
        if (migrated) {
            saveBatches(context, batches, 0);
        }
        return batches;
    }

    /** 旧版只有一个数组 + 三个全局设置，整体迁到批次 0。 */
    private static List<Batch> migrateLegacy(Context context, int rowCount) {
        SharedPreferences p = prefs(context);
        Batch first = new Batch(rowCount);
        String json = p.getString(KEY_LEGACY_SAVED_DATA, null);
        if (json != null && !json.isEmpty()) {
            try {
                JSONArray array = new JSONArray(json);
                int count = Math.min(array.length(), rowCount);
                for (int i = 0; i < count; i++) {
                    JSONObject row = array.optJSONObject(i);
                    if (row != null) {
                        first.getRows().get(i).fromJson(row);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "迁移旧数据异常", e);
            }
        }
        first.setWaterPercentage(p.getInt(KEY_LEGACY_WATER, 20));
        first.setFermentedInWater(p.getBoolean(KEY_LEGACY_FERMENTED_WATER, false));
        first.setFermentedInAverage(p.getBoolean(KEY_LEGACY_FERMENTED_AVERAGE, false));

        List<Batch> batches = new ArrayList<>();
        batches.add(first);
        for (int i = 1; i < BATCH_COUNT; i++) {
            batches.add(new Batch(rowCount));
        }
        Log.i(TAG, "旧版数据已迁移到批次 1");
        return batches;
    }

    public static void saveBatches(Context context, List<Batch> batches, int activeIndex) {
        try {
            JSONArray array = new JSONArray();
            for (Batch batch : batches) {
                array.put(batch.toJson());
            }
            prefs(context).edit()
                    .putString(KEY_BATCHES, array.toString())
                    .putInt(KEY_ACTIVE_BATCH, activeIndex)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "保存批次数据异常", e);
        }
    }

    public static int loadActiveIndex(Context context) {
        return prefs(context).getInt(KEY_ACTIVE_BATCH, 0);
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