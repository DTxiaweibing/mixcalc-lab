package com.shrimpfarm.app.mixcalc.table;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 列宽布局：手机档固定宽 + 横向滚动；平板档按原比例整体缩放、正好撑满窗口宽。
 * 另外还判定「表格是否已经铺满 11 列」，决定菜单里出不出现「快捷设置」。
 */
public class TabletLayoutTest {

    // 与 Activity 的 columnWidths 同值：序号 40、棚号 64、其余 60（合计 604dp）
    private static final int[] WIDTHS_DP = {40, 64, 40, 40, 60, 60, 60, 60, 60, 60, 60};

    private static boolean[] allVisible() {
        boolean[] visible = new boolean[WIDTHS_DP.length];
        for (int i = 0; i < visible.length; i++) {
            visible[i] = true;
        }
        return visible;
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    /** 手机竖屏 360dp：11 列自然宽 604dp 放不下，要保留快捷设置。 */
    @Test
    public void phoneNarrowWindowNeedsQuickSettings() {
        boolean filled = TabletLayout.fillsAllColumns(
                WIDTHS_DP, allVisible(), 1080, 3f, false);
        assertFalse(filled);
    }

    /** 展开/大屏 700dp：11 列放得下，菜单不再出现快捷设置。 */
    @Test
    public void widePhoneWindowHidesQuickSettings() {
        boolean filled = TabletLayout.fillsAllColumns(
                WIDTHS_DP, allVisible(), 2100, 3f, false);
        assertTrue(filled);
    }

    /** 只要还有列被藏起来，快捷设置必须保留，否则用户没有入口恢复。 */
    @Test
    public void hiddenColumnKeepsQuickSettings() {
        boolean[] visible = allVisible();
        visible[3] = false;
        boolean filled = TabletLayout.fillsAllColumns(
                WIDTHS_DP, visible, 2100, 3f, false);
        assertFalse(filled);
    }

    /** 平板布局按可见列缩放到窗宽，11 列总是放得下。 */
    @Test
    public void tabletLayoutAlwaysFitsAllColumns() {
        boolean filled = TabletLayout.fillsAllColumns(
                WIDTHS_DP, allVisible(), 1080, 3f, true);
        assertTrue(filled);
    }

    /** 非法入参不崩，按「没铺满」处理。 */
    @Test
    public void invalidInputIsNotFilled() {
        assertFalse(TabletLayout.fillsAllColumns(null, null, 1080, 3f, false));
        assertFalse(TabletLayout.fillsAllColumns(WIDTHS_DP, allVisible(), 0, 3f, false));
    }

    /** 布局模式只看当前窗口宽度：手机竖屏 320–480dp 一律手机布局。 */
    @Test
    public void narrowWindowIsPhoneLayout() {
        assertFalse(TabletLayout.isTabletWidth(360));
        assertFalse(TabletLayout.isTabletWidth(480));
        assertFalse(TabletLayout.isTabletWidth(599));
    }

    /** 600dp 起算铺满档（平板竖屏 800dp 左右也在这个档位）。 */
    @Test
    public void wideWindowIsTabletLayout() {
        assertTrue(TabletLayout.isTabletWidth(600));
        assertTrue(TabletLayout.isTabletWidth(800));
        assertTrue(TabletLayout.isTabletWidth(1200));
    }

    /**
     * 已知取舍：小米 Pad 5 分屏（设备宽约 1140dp，一半约 570dp）仍走手机档，
     * 看得到 10 列多一点，第 11 列要横向滑一下。
     *
     * <p>试过把门槛降到 544dp 让它铺满，代价是棚号列从 64dp 压到 57dp 左右，
     * 15dp 字号的两行文本被 {@code clipRect} 直接裁掉（棚号列不补省略号），
     * 号码会被切断——比多滑一下更难接受，所以门槛维持 600dp。</p>
     */
    @Test
    public void padSplitScreenStaysInPhoneLayout() {
        assertFalse(TabletLayout.isTabletWidth(570));
        // 570dp 窗口里放 604dp 的表：确实要横向滚动，这是这一档的预期行为
        int screenWidthPx = Math.round(570 * 2f);
        assertFalse(TabletLayout.fillsAllColumns(
                WIDTHS_DP, allVisible(), screenWidthPx, 2f, false));
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

        // 手机布局下 11 列合计 604dp 放不下 → 保留快捷设置（用户有藏列的入口）
        int screenWidthPx = Math.round(widthDp * 3f);
        assertFalse(TabletLayout.fillsAllColumns(
                WIDTHS_DP, allVisible(), screenWidthPx, 3f, false));
    }

    /**
     * 平板横屏的核心诉求：11 列按比例缩放后，总宽**正好**等于窗口宽度。
     * 少 1px 就有横向滚动缝，多 1px 又会在右侧留白。
     */
    @Test
    public void tabletWidthsFillWindowExactly() {
        int[][] cases = {
                {900, 1}, {960, 1}, {1200, 2}, {1280, 2}, {1500, 3},
                {1800, 1}, {1920, 3}, {2100, 3}, {2400, 2}, {2560, 2}, {3840, 3}
        };
        for (int[] c : cases) {
            int screenWidthPx = Math.round(c[0] * c[1]);
            int[] widths = TabletLayout.computeWidths(WIDTHS_DP, allVisible(), screenWidthPx,
                    (float) c[1]);
            assertEquals("screen=" + screenWidthPx + "px@" + c[1] + "x",
                    screenWidthPx, sum(widths));
        }
    }

    /** 缩放只乘一个系数：序号最窄、棚号最宽的比例保持不变。 */
    @Test
    public void tabletKeepsCurrentProportions() {
        int[] widths = TabletLayout.computeWidths(WIDTHS_DP, allVisible(), 2400, 2f);
        // 64 : 40 = 1.6（允许 1px 取整误差）
        assertEquals(1.6f, (float) widths[1] / widths[0], 0.03f);
        assertTrue("棚号列应比普通列宽", widths[1] > widths[4]);
        assertTrue("序号列应比普通列窄", widths[0] < widths[4]);
    }

    /** 藏掉列以后剩下的列补满窗宽，被藏的那列宽度为 0。 */
    @Test
    public void tabletWithHiddenColumnStillFills() {
        boolean[] visible = allVisible();
        visible[3] = false;
        int[] widths = TabletLayout.computeWidths(WIDTHS_DP, visible, 2000, 2f);
        assertEquals(0, widths[3]);
        assertEquals(2000, sum(widths));
    }

    /** 600dp 这一档原来因为 50dp 最小宽会多出 5–11dp 横向滚动，现在必须正好放下。 */
    @Test
    public void tabletNarrowWindowHasNoHorizontalScroll() {
        int screenWidthPx = 1200;
        int[] widths = TabletLayout.computeWidths(WIDTHS_DP, allVisible(), screenWidthPx, 2f);
        assertEquals(screenWidthPx, sum(widths));
    }

    /** 退化入参不崩：宽 0 全 0、空数组返回空、可见性为 null 按全部可见。 */
    @Test
    public void tabletDegenerateInputIsSafe() {
        assertEquals(0, sum(TabletLayout.computeWidths(WIDTHS_DP, allVisible(), 0, 2f)));
        assertEquals(0, TabletLayout.computeWidths(new int[0], allVisible(), 1200, 2f).length);
        assertEquals(1200, sum(TabletLayout.computeWidths(WIDTHS_DP, null, 1200, 2f)));
    }

    /** 手机档不受影响：仍然是 dp 固定宽度（横向滚动）。 */
    @Test
    public void phoneWidthsStayFixed() {
        int[] widths = TabletLayout.fixedWidths(WIDTHS_DP, 2f);
        assertEquals(80, widths[0]);
        assertEquals(128, widths[1]);
        assertEquals(120, widths[4]);
    }
}
