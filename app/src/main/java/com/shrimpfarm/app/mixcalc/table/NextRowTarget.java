package com.shrimpfarm.app.mixcalc.table;

/**
 * 「下一行 ▾」的目标列选择：同列优先，否则按列序取第一个可编辑的。
 */
public final class NextRowTarget {

    private NextRowTarget() {
    }

    /**
     * @param currentColumnIndex 当前列下标（可能为 -1 或越界）
     * @param enabled            下一行各列是否可编辑（已按可见 / 可编辑 / 数据判过）
     * @return 目标列下标；一个可编辑格都没有返回 -1
     */
    public static int choose(int currentColumnIndex, boolean[] enabled) {
        if (enabled == null || enabled.length == 0) {
            return -1;
        }
        if (currentColumnIndex >= 0 && currentColumnIndex < enabled.length
                && enabled[currentColumnIndex]) {
            return currentColumnIndex;
        }
        for (int i = 0; i < enabled.length; i++) {
            if (enabled[i]) {
                return i;
            }
        }
        return -1;
    }
}
