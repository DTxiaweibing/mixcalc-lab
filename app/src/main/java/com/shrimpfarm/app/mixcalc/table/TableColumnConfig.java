package com.shrimpfarm.app.mixcalc.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 列的顺序、可见性与固定状态。
 *
 * <p>承接原有的 {@code columnIds} / {@code columnVisibility} / {@code columnFixed}。
 * {@link #visual()} 给出绘制顺序：<b>固定列在前、非固定列在后，各自保持原始顺序</b>，
 * 与原实现 {@code createFixedColumns} / {@code createScrollColumns} 的挑列方式一致。</p>
 */
public class TableColumnConfig {

    private final List<TableColumn> columns = new ArrayList<>();
    private final List<TableColumn> visual = new ArrayList<>();
    private final Map<String, Boolean> visible = new HashMap<>();
    private final Map<String, Boolean> fixed = new HashMap<>();

    public TableColumnConfig(List<TableColumn> columns) {
        if (columns != null) {
            this.columns.addAll(columns);
        }
        for (TableColumn c : this.columns) {
            visible.put(c.id, Boolean.TRUE);
            fixed.put(c.id, Boolean.FALSE);
        }
        rebuildVisualOrder();
    }

    /** 原始顺序（= 原 columnIds 顺序）。 */
    public List<TableColumn> columns() {
        return columns;
    }

    /** 绘制顺序：固定列在前 + 非固定列在后。 */
    public List<TableColumn> visual() {
        return visual;
    }

    public void setVisible(String id, boolean value) {
        visible.put(id, value);
        rebuildVisualOrder();
    }

    public void setFixed(String id, boolean value) {
        fixed.put(id, value);
        rebuildVisualOrder();
    }

    public boolean isVisible(String id) {
        Boolean b = visible.get(id);
        return b == null || b;
    }

    public boolean isFixed(String id) {
        Boolean b = fixed.get(id);
        return b != null && b;
    }

    public int indexOf(String id) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public TableColumn get(String id) {
        int i = indexOf(id);
        return i < 0 ? null : columns.get(i);
    }

    public void rebuildVisualOrder() {
        visual.clear();
        for (TableColumn c : columns) {
            if (isVisible(c.id) && isFixed(c.id)) {
                visual.add(c);
            }
        }
        for (TableColumn c : columns) {
            if (isVisible(c.id) && !isFixed(c.id)) {
                visual.add(c);
            }
        }
    }
}
