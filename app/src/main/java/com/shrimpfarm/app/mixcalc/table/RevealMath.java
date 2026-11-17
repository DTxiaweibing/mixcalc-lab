package com.shrimpfarm.app.mixcalc.table;

/**
 * 把正在编辑的格子滚进可视区所需的最小偏移（对齐鸿蒙 ensureEditorVisible）。
 *
 * <p>纯计算，不碰 View：纵向先保证整行落在表体里，横向再保证整格不钻到冻结列
 * 下面、也不越过右边缘；结果永远夹在滚动范围内。已经看得见就不动。</p>
 */
public final class RevealMath {

    private RevealMath() {
    }

    /**
     * @return 新的 scrollY
     */
    public static int scrollY(int row, int rowHeight, int bodyHeight,
                              int scrollY, int maxScrollY) {
        if (rowHeight <= 0 || bodyHeight <= 0) {
            return scrollY;
        }
        int top = row * rowHeight;
        int ny = scrollY;
        if (top < scrollY) {
            ny = top;
        } else if (top + rowHeight > scrollY + bodyHeight) {
            ny = top + rowHeight - bodyHeight;
        }
        return clamp(ny, 0, maxScrollY);
    }

    /**
     * @param cellBaseLeft 该列未滚动时的左边界（{@code TableMetrics.xBase}）
     * @param fixed        固定列不动
     * @return 新的 scrollX
     */
    public static int scrollX(int cellBaseLeft, int cellWidth, int fixedWidth, int viewWidth,
                              int scrollX, int maxScrollX, boolean fixed) {
        if (fixed || cellWidth <= 0 || viewWidth <= 0) {
            return scrollX;
        }
        int left = cellBaseLeft - scrollX;
        int nx = scrollX;
        if (left < fixedWidth) {
            nx = scrollX - (fixedWidth - left);
        } else if (left + cellWidth > viewWidth) {
            nx = scrollX + (left + cellWidth - viewWidth);
        }
        return clamp(nx, 0, maxScrollX);
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}
