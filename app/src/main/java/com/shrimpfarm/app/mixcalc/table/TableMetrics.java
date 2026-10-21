package com.shrimpfarm.app.mixcalc.table;

import java.util.ArrayList;
import java.util.List;

// 算表格几何：列宽、每列左边界的基准 x、滚动范围、坐标→行列的换算。
// 全表共用一个坐标系：
//   固定列 x 不加横向偏移，非固定列 x = 基准x - scrollX；
//   表头钉在 y=0，表尾钉在视口底部，都不随 scrollY 变；
//   正文行 y = headerHeight + row*rowHeight - scrollY。
public class TableMetrics {

    public final List<TableColumn> visual = new ArrayList<>();

    public int[] xBase = new int[0];
    public int[] width = new int[0];

    public int fixedCount;
    public int fixedWidth;
    public int scrollWidth;
    public int totalWidth;

    public int rowCount;
    public int rowHeight;
    public int headerHeight;
    public int footerHeight;

    public int viewWidth;
    public int viewHeight;
    public int bodyHeight;

    public int maxScrollX;
    public int maxScrollY;

    public void rebuild(TableColumnConfig config, int rowCount, int viewWidth, int viewHeight,
                        int rowHeight, int headerHeight, int footerHeight) {
        visual.clear();
        if (config != null) {
            visual.addAll(config.visual());
        }

        this.rowCount = rowCount;
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.rowHeight = rowHeight;
        this.headerHeight = headerHeight;
        this.footerHeight = footerHeight;

        int n = visual.size();
        xBase = new int[n];
        width = new int[n];

        int acc = 0;
        int fixedW = 0;
        int scrollW = 0;
        int fixedCnt = 0;
        for (int i = 0; i < n; i++) {
            TableColumn c = visual.get(i);
            xBase[i] = acc;
            width[i] = c.widthPx;
            acc += c.widthPx;
            if (config != null && config.isFixed(c.id)) {
                fixedW += c.widthPx;
                fixedCnt++;
            } else {
                scrollW += c.widthPx;
            }
        }
        fixedCount = fixedCnt;
        fixedWidth = fixedW;
        scrollWidth = scrollW;
        totalWidth = acc;

        bodyHeight = Math.max(0, viewHeight - headerHeight - footerHeight);
        int scrollViewport = Math.max(0, viewWidth - fixedWidth);
        maxScrollX = Math.max(0, scrollWidth - scrollViewport);
        maxScrollY = Math.max(0, rowCount * rowHeight - bodyHeight);
    }

    // 视觉列 vi 的左边界 x
    public int xOf(int vi, int scrollX) {
        return vi < fixedCount ? xBase[vi] : xBase[vi] - scrollX;
    }

    // 第 row 行正文顶部的 y
    public int yOfRow(int row, int scrollY) {
        return headerHeight + row * rowHeight - scrollY;
    }

    // y 落在哪一行，不在正文区返回 -1
    public int rowAt(float y, int scrollY) {
        float bodyY = y - headerHeight + scrollY;
        if (bodyY < 0) {
            return -1;
        }
        int r = (int) (bodyY / rowHeight);
        return (r >= 0 && r < rowCount) ? r : -1;
    }

    public int visualIndex(String id) {
        for (int i = 0; i < visual.size(); i++) {
            if (visual.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    // x 落在哪一列，返回视觉列下标，不在任何列返回 -1
    public int visualIndexAt(float x, int scrollX) {
        if (x < fixedWidth) {
            for (int i = 0; i < fixedCount; i++) {
                if (x >= xBase[i] && x < xBase[i] + width[i]) {
                    return i;
                }
            }
            return -1;
        }
        for (int i = fixedCount; i < visual.size(); i++) {
            int cx = xBase[i] - scrollX;
            if (x >= cx && x < cx + width[i]) {
                return i;
            }
        }
        return -1;
    }
}
