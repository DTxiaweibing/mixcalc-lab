package com.shrimpfarm.app.mixcalc.table;

import android.text.InputType;

/**
 * 列元数据：一条列的静态定义。
 *
 * <p>列名（表头文案）不放在这里，因为水列表头会带上当前水百分比、发酵料表头会随两个开关变化，
 * 这类动态文案由 {@link CellTextProvider#getHeaderText(String)} 提供。</p>
 */
public class TableColumn {

    /** 列 id，与业务约定一致，例如 "shed_number"。 */
    public final String id;

    /** 列宽（px）。 */
    public final int widthPx;

    /** 是否为可编辑列；可编辑列点击后进入编辑浮层。 */
    public final boolean editable;

    /** 正文文本颜色。 */
    public final int textColor;

    /** 编辑浮层使用的输入类型。 */
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
