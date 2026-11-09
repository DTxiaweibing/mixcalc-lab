package com.shrimpfarm.app.mixcalc.ui;

/**
 * 弹窗定位/尺寸的纯计算。不持有 Activity、View，便于单元验证。
 */
public final class PopupGeometry {

    private PopupGeometry() {
    }

    /** 左右铺满当前窗口：窗口变宽/变窄都跟着变，不再有 360dp 上限。 */
    public static int fullWidth(int windowWidthPx) {
        return Math.max(1, windowWidthPx);
    }

    /**
     * 表头弹窗右边缘对齐：弹窗右边缘贴住被点单元格右边缘，靠左/靠右自动夹回屏幕内。
     *
     * @param cellRightPx  被点单元格右边缘（屏幕坐标）
     * @param popupWidthPx 弹窗实际宽度
     * @param screenWidthPx 当前窗口宽度
     */
    public static int rightAlignedX(int cellRightPx, int popupWidthPx, int screenWidthPx) {
        int x = cellRightPx - popupWidthPx;
        int max = Math.max(0, screenWidthPx - popupWidthPx);
        return Math.max(0, Math.min(x, max));
    }

    /**
     * 左边缘对齐：弹窗左边缘贴住被点单元格左边缘，只夹在窗口内。
     * 右边放不下时靠缩宽度解决（见 {@link #leftAlignedWidth}），不靠挪左边，挪了就对齐不了。
     *
     * @param cellLeftPx   被点单元格左边缘（屏幕坐标）
     * @param windowWidthPx 当前窗口宽度
     */
    public static int leftAlignedX(int cellLeftPx, int windowWidthPx) {
        return Math.max(0, Math.min(cellLeftPx, Math.max(0, windowWidthPx - 1)));
    }

    /**
     * 左对齐时能用的最大宽度：desired 与「到窗口右边缘的距离」取小。
     *
     * @param desiredWidthPx 原本想要的宽度
     * @param leftPx         弹窗左边缘（屏幕坐标）
     * @param windowWidthPx  当前窗口宽度
     * @param marginPx       右侧最少留白
     */
    public static int leftAlignedWidth(int desiredWidthPx, int leftPx, int windowWidthPx,
                                       int marginPx) {
        int fit = windowWidthPx - marginPx - leftPx;
        return Math.max(1, Math.min(desiredWidthPx, fit));
    }
}
