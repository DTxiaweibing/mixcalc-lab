package com.shrimpfarm.app.mixcalc.table;

import android.graphics.Rect;

/**
 * 命中回调：单击 / 长按 表头、正文、表尾。
 *
 * <p>矩形参数用于给弹窗做锚点（列操作菜单、换料比例抽屉），坐标为表格视图内坐标。</p>
 */
public interface OnCellTapListener {

    /** 正文单元格单击（可编辑格会先进入编辑，随后回调此方法）。 */
    void onCellClick(int row, String columnId);

    /** 正文单元格长按。 */
    void onCellLongPress(int row, String columnId, Rect cellRect);

    /** 表头单击（水列 → 水百分比弹窗；发酵料列 → 计算方式弹窗）。 */
    void onHeaderClick(String columnId);

    /** 表头长按（列操作菜单：固定 / 隐藏 / 全部显示 / 取消全部固定）。 */
    void onHeaderLongPress(String columnId, Rect cellRect);

    /** 表尾单击（序号列 / 棚号列 / 末列的按钮）。 */
    void onFooterClick(String columnId);
}
