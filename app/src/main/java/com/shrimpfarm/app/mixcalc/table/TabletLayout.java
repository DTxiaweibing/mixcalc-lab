package com.shrimpfarm.app.mixcalc.table;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

/**
 * 平板/手机的列宽计算。纯计算，不持有 Activity 状态。
 */
public final class TabletLayout {

    private static final String TAG = "TabletLayout";

    private TabletLayout() {
    }

    /** 最小宽度 ≥ 600dp，或对角线 ≥ 7 英寸，判定为平板。 */
    public static boolean isTabletDevice(Context context) {
        try {
            if (context.getResources().getConfiguration().smallestScreenWidthDp >= 600) {
                return true;
            }
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            float widthInches = metrics.widthPixels / metrics.xdpi;
            float heightInches = metrics.heightPixels / metrics.ydpi;
            double diagonal = Math.sqrt(Math.pow(widthInches, 2) + Math.pow(heightInches, 2));
            return diagonal >= 7.0;
        } catch (Exception e) {
            Log.e(TAG, "检测平板设备异常: " + e.getMessage());
            return false;
        }
    }

    /** 手机模式：dp 固定宽度转 px。 */
    public static int[] fixedWidths(int[] widthsDp, float density) {
        int[] widths = new int[widthsDp.length];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.round(widthsDp[i] * density);
        }
        return widths;
    }

    /**
     * 把可见列按比例放大到铺满屏宽。
     * 隐藏掉很多列以后，剩下列的总宽可能还不到屏宽，右侧会留一条空白（看起来像"少了一列"）。
     * 这里把差额按当前宽度比例补给可见列；已铺满或超出时不动（保持横向滚动）。
     */
    public static void fillToWidth(int[] widths, boolean[] visible, int screenWidthPx) {
        if (widths == null || widths.length == 0 || screenWidthPx <= 0) {
            return;
        }
        int total = 0;
        int lastVisible = -1;
        for (int i = 0; i < widths.length; i++) {
            boolean shown = visible == null || i >= visible.length || visible[i];
            if (shown && widths[i] > 0) {
                total += widths[i];
                lastVisible = i;
            }
        }
        if (total <= 0 || total >= screenWidthPx || lastVisible < 0) {
            return;
        }
        float scale = (float) screenWidthPx / total;
        int newTotal = 0;
        for (int i = 0; i < widths.length; i++) {
            boolean shown = visible == null || i >= visible.length || visible[i];
            if (shown && widths[i] > 0) {
                widths[i] = Math.max(1, (int) (widths[i] * scale));
                newTotal += widths[i];
            }
        }
        // 取整误差补给最后一个可见列，保证正好铺满、不留半像素缝
        int diff = screenWidthPx - newTotal;
        if (diff != 0) {
            widths[lastVisible] = Math.max(1, widths[lastVisible] + diff);
        }
    }

    /**
     * 平板模式：按可见列数均分屏宽，再按列语义微调，最后整体缩放到屏宽。
     *
     * @param widthsDp  基础宽度（dp），仅用于列数
     * @param columnIds 列标识，用于按语义加权
     * @param visible   与 columnIds 等长的可见性
     */
    public static int[] computeWidths(int[] widthsDp, String[] columnIds, boolean[] visible,
                                      int screenWidthPx, float density) {
        int count = widthsDp.length;
        int[] widths = new int[count];

        int visibleCount = 0;
        for (int i = 0; i < count; i++) {
            if (i < visible.length && visible[i]) {
                visibleCount++;
            }
        }
        if (visibleCount == 0) {
            visibleCount = 1;
        }
        int base = screenWidthPx / visibleCount;
        int minWidth = Math.round(50 * density);
        int maxWidth = Math.round(120 * density);

        for (int i = 0; i < count; i++) {
            if (i >= visible.length || !visible[i]) {
                widths[i] = 0;
                continue;
            }
            String id = columnIds[i];
            int width = base;
            if ("shed_number".equals(id) || "weighed_feed".equals(id)) {
                width = (int) (base * 1.2);
            }
            if ("seq".equals(id) || "average_feed".equals(id)) {
                width = (int) (base * 0.8);
            }
            widths[i] = Math.max(minWidth, Math.min(width, maxWidth));
        }

        int total = 0;
        for (int w : widths) {
            total += w;
        }
        if (total > 0 && Math.abs(total - screenWidthPx) > 50) {
            float scale = (float) screenWidthPx / total;
            for (int i = 0; i < count; i++) {
                widths[i] = (int) (widths[i] * scale);
            }
        }
        return widths;
    }
}