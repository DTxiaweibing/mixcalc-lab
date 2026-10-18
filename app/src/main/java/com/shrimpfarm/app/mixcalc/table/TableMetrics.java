package com.shrimpfarm.app.mixcalc.table;

import java.util.ArrayList;
import java.util.List;

/**
 * 表格几何计算：列宽数组、前缀坐标、滚动范围、命中换算。
 *
 * <p>整张表只有一个坐标系：</p>
 * <ul>
 *   <li>固定列：x 不含横向偏移（冻结）</li>
 *   <li>非固定列：x = 前缀坐标 - scrollX</li>
 *   <li>表头/表尾：y 分别钉在 0 与视口底部（不随 scrollY 变化）</li>
 *   <li>正文行：y = headerHeight + row * rowHeight - scrollY</li>
 * </ul>
 */
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

    /** 视觉列 vi 的左边界 x（固定列冻结；非固定列已含横向偏移）。 */
    public int xOf(int vi, int scrollX) {
        return vi < fixedCount ? xBase[vi] : xBase[vi] - scrollX;
    }

    /** 行 row 的顶部 y（正文区，含纵向偏移）。 */
    public int yOfRow(int row, int scrollY) {
        return headerHeight + row * rowHeight - scrollY;
    }

    /** 正文区命中：返回行号，未命中返回 -1。 */
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

    /** 命中换算：返回视觉列下标，未命中返回 -1。 */
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
