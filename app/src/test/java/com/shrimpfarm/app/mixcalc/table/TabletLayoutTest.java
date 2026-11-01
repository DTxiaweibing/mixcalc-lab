package com.shrimpfarm.app.mixcalc.table;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 「表格是否已经铺满 11 列」的判据：决定菜单里还出不出现「快捷设置」。 */
public class TabletLayoutTest {

    private static final int[] WIDTHS_DP = {40, 60, 40, 40, 60, 60, 60, 60, 60, 60, 60};
    private static final String[] COLUMN_IDS = {
            "seq", "shed_number", "shed_count", "average_feed", "fermented", "powder",
            "feed03", "feed05", "feed10", "water", "weighed_feed"
    };

    private static boolean[] allVisible() {
        boolean[] visible = new boolean[WIDTHS_DP.length];
        for (int i = 0; i < visible.length; i++) {
            visible[i] = true;
        }
        return visible;
    }

    /** 手机竖屏 360dp：11 列自然宽 600dp 放不下，要保留快捷设置。 */
    @Test
    public void phoneNarrowWindowNeedsQuickSettings() {
        boolean filled = TabletLayout.fillsAllColumns(
                WIDTHS_DP, COLUMN_IDS, allVisible(), 1080, 3f, false);
        assertFalse(filled);
    }

    /** 展开/大屏 700dp：11 列放得下，菜单不再出现快捷设置。 */
    @Test
    public void widePhoneWindowHidesQuickSettings() {
        boolean filled = TabletLayout.fillsAllColumns(
                WIDTHS_DP, COLUMN_IDS, allVisible(), 2100, 3f, false);
        assertTrue(filled);
    }

    /** 只要还有列被藏起来，快捷设置必须保留，否则用户没有入口恢复。 */
    @Test
    public void hiddenColumnKeepsQuickSettings() {
        boolean[] visible = allVisible();
        visible[3] = false;
        boolean filled = TabletLayout.fillsAllColumns(
                WIDTHS_DP, COLUMN_IDS, visible, 2100, 3f, false);
        assertFalse(filled);
    }

    /** 平板布局按可见列均分屏宽，11 列总是放得下。 */
    @Test
    public void tabletLayoutAlwaysFitsAllColumns() {
        boolean filled = TabletLayout.fillsAllColumns(
                WIDTHS_DP, COLUMN_IDS, allVisible(), 1080, 3f, true);
        assertTrue(filled);
    }

    /** 非法入参不崩，按「没铺满」处理。 */
    @Test
    public void invalidInputIsNotFilled() {
        assertFalse(TabletLayout.fillsAllColumns(null, COLUMN_IDS, null, 1080, 3f, false));
        assertFalse(TabletLayout.fillsAllColumns(WIDTHS_DP, COLUMN_IDS, allVisible(), 0, 3f, false));
    }

    /** 布局模式只看当前窗口宽度：窄于 600dp 一律手机布局。 */
    @Test
    public void narrowWindowIsPhoneLayout() {
        assertFalse(TabletLayout.isTabletWidth(360));
        assertFalse(TabletLayout.isTabletWidth(480));
        assertFalse(TabletLayout.isTabletWidth(599));
    }

    /** 600dp 起算平板布局。 */
    @Test
    public void wideWindowIsTabletLayout() {
        assertTrue(TabletLayout.isTabletWidth(600));
        assertTrue(TabletLayout.isTabletWidth(800));
        assertTrue(TabletLayout.isTabletWidth(1200));
    }

    /**
     * 折叠屏回归：X QFOLD6 折叠成外屏约 370dp。
     * 设备本身是折叠屏（对角线远超 7 英寸），但窗口只有 370dp，必须按手机布局，
     * 否则 computeWidths() 会把 11 列硬压进 370dp，出现「外屏 11 列全显示」。
     */
    @Test
    public void foldedOuterScreenUsesPhoneLayout() {
        int widthDp = 370;
        assertFalse(TabletLayout.isTabletWidth(widthDp));

        // 手机布局下 11 列合计 600dp 放不下 → 保留快捷设置（用户有藏列的入口）
        int screenWidthPx = Math.round(widthDp * 3f);
        assertFalse(TabletLayout.fillsAllColumns(
                WIDTHS_DP, COLUMN_IDS, allVisible(), screenWidthPx, 3f, false));
    }
}
