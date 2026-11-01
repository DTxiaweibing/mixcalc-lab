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
}
