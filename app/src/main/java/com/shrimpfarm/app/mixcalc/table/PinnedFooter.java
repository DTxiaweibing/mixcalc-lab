package com.shrimpfarm.app.mixcalc.table;

/**
 * 表尾「重置」格的钉住位置（对齐鸿蒙）：贴右边缘，横向滚动不跟着跑，
 * 任何时候都点得到；视口太窄时也不许压到冻结列上。
 */
public final class PinnedFooter {

    private PinnedFooter() {
    }

    /** @return 钉住格的左边界 */
    public static int left(int viewWidth, int cellWidth, int fixedWidth) {
        int left = viewWidth - cellWidth;
        return left < fixedWidth ? fixedWidth : left;
    }

    /** @return 触摸点是否落在钉住格里 */
    public static boolean hit(float x, int left, int cellWidth) {
        return x >= left && x < left + cellWidth;
    }
}
