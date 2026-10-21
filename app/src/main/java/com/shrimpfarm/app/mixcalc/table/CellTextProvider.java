package com.shrimpfarm.app.mixcalc.table;

// 宿主（MixCalcActivity）实现它，告诉表格每一格显示什么、什么状态。
// 是否禁用、是否高亮、表头文案、表尾统计这类业务规则都留在宿主，引擎只管几何和绘制。
public interface CellTextProvider {

    String getHeaderText(String columnId);

    // 取值 CellStyle.HEADER_*
    int getHeaderState(String columnId);

    String getCellText(int row, String columnId);

    // 取值 CellStyle.STATE_*
    int getCellState(int row, String columnId);

    // 这格现在能不能编辑：是可编辑列、业务上也启用了才算
    boolean isCellEditable(int row, String columnId);

    String getFooterText(String columnId);

    // 取值 CellStyle.FOOTER_*
    int getFooterStyle(String columnId);
}
