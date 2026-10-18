package com.shrimpfarm.app.mixcalc.table;

/**
 * 由宿主（MixCalcActivity）实现，向自绘表格提供所有"要显示成什么样"的内容与状态。
 *
 * <p>业务规则（是否禁用、是否高亮、表头文案、表尾统计）全部留在宿主，
 * 引擎只负责几何与绘制。</p>
 */
public interface CellTextProvider {

    /** 表头文案（水列带百分比、发酵料列可带状态）。 */
    String getHeaderText(String columnId);

    /** 表头视觉状态，取值 {@link CellStyle#HEADER_NORMAL} 等。 */
    int getHeaderState(String columnId);

    /** 单元格显示文本。 */
    String getCellText(int row, String columnId);

    /** 单元格视觉状态，取值 {@link CellStyle#STATE_NORMAL} 等。 */
    int getCellState(int row, String columnId);

    /** 该单元格此刻是否允许进入编辑（可编辑列且业务上已启用）。 */
    boolean isCellEditable(int row, String columnId);

    /** 表尾文本。 */
    String getFooterText(String columnId);

    /** 表尾样式，取值 {@link CellStyle#FOOTER_NORMAL} 等。 */
    int getFooterStyle(String columnId);
}
