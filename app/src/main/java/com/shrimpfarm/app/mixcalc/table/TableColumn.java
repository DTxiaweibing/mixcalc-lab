package com.shrimpfarm.app.mixcalc.table;

import android.text.InputType;

// 一条列的静态定义。列名不在这里，水/发酵料的表头文案是动态的，
// 由 CellTextProvider.getHeaderText 出。
public class TableColumn {

    public final String id;

    public final int widthPx;

    public final boolean editable;

    public final int textColor;

    public final int inputType;

    public TableColumn(String id, int widthPx, boolean editable, int textColor) {
        this(id, widthPx, editable, textColor,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    }

    public TableColumn(String id, int widthPx, boolean editable, int textColor, int inputType) {
        this.id = id;
        this.widthPx = widthPx;
        this.editable = editable;
        this.textColor = textColor;
        this.inputType = inputType;
    }
}
