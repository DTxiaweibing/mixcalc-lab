package com.shrimpfarm.app.mixcalc.table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 列的顺序、可见性、固定状态。
// visual() 是绘制顺序：固定列排前面，非固定列排后面，各自保持原顺序。
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

    public List<TableColumn> columns() {
        return columns;
    }

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
