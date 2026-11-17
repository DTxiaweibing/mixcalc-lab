package com.shrimpfarm.app.mixcalc.table;

/**
 * 平板/手机的列宽计算。纯计算，不持有 Activity 状态。
 */
public final class TabletLayout {

    /**
     * 铺满档的最小窗口宽度（dp）。低于它一律按手机布局：固定列宽 + 横向滚动。
     *
     * <p>门槛卡在 600 不是随便取的：铺满档要把 11 列按比例压到窗宽，棚号列是 64dp 的基准，
     * 窗口刚好 600dp 时它只剩 63dp，15dp 字号的两行文本已经是极限。
     * 再往下压（试过 544dp），棚号列会掉到 57dp 左右，超出部分被 {@code clipRect} 直接裁掉
     * （棚号列不补省略号），看起来就是号码被切——比分屏时滑一下更难接受，所以维持 600。
     * 分屏窗口常在 560–600dp 这一档，属于「滑一下」的已知取舍。</p>
     */
    public static final int TABLET_MIN_WIDTH_DP = 600;

    private TabletLayout() {
    }

    /**
     * 按**当前窗口宽度**判定用平板布局还是手机布局。
     *
     * <p>不能用设备类型判断：折叠屏展开时是平板，折叠成外屏后只有 370dp 左右，
     * 但设备本身（对角线、smallestScreenWidthDp）没变，会把 11 列硬压进窄屏。
     * 这里只看当前窗口宽度，折叠/展开、分屏、转屏都跟着窗口走。
     */
    public static boolean isTabletWidth(int widthDp) {
        return widthDp >= TABLET_MIN_WIDTH_DP;
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
     * 表格是否已经铺满全部列：全部列可见，且按当前布局算出来的自然总宽放得下。
     * 满足时说明没必要再提供「快捷设置」（没有列要藏），折叠/展开、分屏都按实时窗口算。
     *
     * @param tabletMode 平板/宽窗口布局（整表按比例缩放到屏宽）还是手机固定列宽
     */
    public static boolean fillsAllColumns(int[] widthsDp, boolean[] visible,
                                          int screenWidthPx, float density, boolean tabletMode) {
        if (widthsDp == null || widthsDp.length == 0 || screenWidthPx <= 0) {
            return false;
        }
        for (int i = 0; i < widthsDp.length; i++) {
            boolean shown = visible == null || i >= visible.length || visible[i];
            if (!shown) {
                return false; // 还有列被藏起来，快捷设置要留着
            }
        }
        int total = 0;
        if (tabletMode) {
            int[] widths = computeWidths(widthsDp, visible, screenWidthPx, density);
            for (int width : widths) {
                total += width;
            }
        } else {
            for (int widthDp : widthsDp) {
                total += Math.round(widthDp * density);
            }
        }
        return total <= screenWidthPx;
    }

    /**
     * 平板模式：把各列的自然宽度（{@code widthsDp × density}）整体按同一比例放大/缩小，
     * 让可见列加起来**正好等于**窗口宽度。
     *
     * <p>比例保持不变（序号最窄、棚号最宽），只是整体缩放，跟手机上看到的样子一致，
     * 但平板上不会再有横向滚动、右侧也不会留白。取整误差补给最后一个可见列，
     * 保证总宽严格等于窗口宽度（差 1px 都会多出一条滚动缝）。</p>
     *
     * @param widthsDp  各列基础宽度（dp），列被隐藏时照样参与「哪些列可见」的判断
     * @param visible   与 widthsDp 等长的可见性；null 或短于列数都按可见处理
     */
    public static int[] computeWidths(int[] widthsDp, boolean[] visible,
                                      int screenWidthPx, float density) {
        int count = widthsDp.length;
        int[] widths = new int[count];
        if (count == 0 || screenWidthPx <= 0) {
            return widths;
        }

        int[] natural = new int[count];
        int naturalTotal = 0;
        int lastVisible = -1;
        for (int i = 0; i < count; i++) {
            if (visible != null && i < visible.length && !visible[i]) {
                continue;
            }
            natural[i] = Math.max(1, Math.round(widthsDp[i] * density));
            naturalTotal += natural[i];
            lastVisible = i;
        }
        if (naturalTotal <= 0 || lastVisible < 0) {
            return widths;
        }

        float scale = (float) screenWidthPx / naturalTotal;
        int scaledTotal = 0;
        for (int i = 0; i < count; i++) {
            if (natural[i] <= 0) {
                continue;
            }
            widths[i] = Math.max(1, (int) (natural[i] * scale));
            scaledTotal += widths[i];
        }
        // 取整误差补给最后一列：总宽必须正好是屏宽
        widths[lastVisible] = Math.max(1, widths[lastVisible] + (screenWidthPx - scaledTotal));
        return widths;
    }
}