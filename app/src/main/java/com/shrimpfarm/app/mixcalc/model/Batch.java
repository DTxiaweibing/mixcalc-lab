package com.shrimpfarm.app.mixcalc.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个批次：独立的 30 行记录、独立的水比与发酵设置、独立的列显示/冻结配置。
 * 固定两个批次（批次01 / 批次02），批次名由视图层按资源渲染，不落库。
 */
public class Batch {

    private final List<FeedData> rows = new ArrayList<>();
    private final Map<String, Boolean> columnVisibility = new HashMap<>();
    private final Map<String, Boolean> columnFixed = new HashMap<>();

    private int waterPercentage = 20;
    private boolean fermentedInWater = false;
    private boolean fermentedInAverage = false;

    public Batch(int rowCount) {
        for (int i = 0; i < rowCount; i++) {
            rows.add(new FeedData());
        }
    }

    public List<FeedData> getRows() {
        return rows;
    }

    public Map<String, Boolean> getColumnVisibility() {
        return columnVisibility;
    }

    public Map<String, Boolean> getColumnFixed() {
        return columnFixed;
    }

    public int getWaterPercentage() {
        return waterPercentage;
    }

    public void setWaterPercentage(int value) {
        this.waterPercentage = value;
    }

    public boolean isFermentedInWater() {
        return fermentedInWater;
    }

    public void setFermentedInWater(boolean value) {
        this.fermentedInWater = value;
    }

    public boolean isFermentedInAverage() {
        return fermentedInAverage;
    }

    public void setFermentedInAverage(boolean value) {
        this.fermentedInAverage = value;
    }

    public JSONObject toJson() throws Exception {
        JSONObject object = new JSONObject();
        object.put("water", waterPercentage);
        object.put("fermentedWater", fermentedInWater);
        object.put("fermentedAverage", fermentedInAverage);

        JSONObject columns = new JSONObject();
        for (Map.Entry<String, Boolean> entry : columnVisibility.entrySet()) {
            columns.put(entry.getKey(), entry.getValue());
        }
        object.put("columns", columns);

        JSONObject fixed = new JSONObject();
        for (Map.Entry<String, Boolean> entry : columnFixed.entrySet()) {
            fixed.put(entry.getKey(), entry.getValue());
        }
        object.put("fixed", fixed);

        JSONArray array = new JSONArray();
        for (FeedData data : rows) {
            array.put(data.toJson());
        }
        object.put("rows", array);
        return object;
    }

    public static Batch fromJson(JSONObject object, int rowCount) {
        Batch batch = new Batch(rowCount);
        if (object == null) {
            return batch;
        }
        batch.waterPercentage = object.optInt("water", 20);
        batch.fermentedInWater = object.optBoolean("fermentedWater", false);
        batch.fermentedInAverage = object.optBoolean("fermentedAverage", false);

        JSONObject columns = object.optJSONObject("columns");
        if (columns != null) {
            java.util.Iterator<String> it = columns.keys();
            while (it.hasNext()) {
                String key = it.next();
                batch.columnVisibility.put(key, columns.optBoolean(key, true));
            }
        }

        JSONObject fixed = object.optJSONObject("fixed");
        if (fixed != null) {
            java.util.Iterator<String> it = fixed.keys();
            while (it.hasNext()) {
                String key = it.next();
                batch.columnFixed.put(key, fixed.optBoolean(key, false));
            }
        }

        JSONArray array = object.optJSONArray("rows");
        if (array == null) {
            return batch;
        }
        int count = Math.min(array.length(), rowCount);
        for (int i = 0; i < count; i++) {
            JSONObject row = array.optJSONObject(i);
            if (row != null) {
                batch.rows.get(i).fromJson(row);
            }
        }
        return batch;
    }
}