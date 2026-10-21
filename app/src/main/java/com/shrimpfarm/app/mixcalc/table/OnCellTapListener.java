package com.shrimpfarm.app.mixcalc.table;

import android.graphics.Rect;

// 表头、正文、表尾的单击和长按回调。
// cellRect 是表格视图内的坐标，给弹窗当锚点用（列操作菜单、换料比例抽屉）。
public interface OnCellTapListener {

    // 正文格单击。可编辑的格会先进编辑，再回调这里
    void onCellClick(int row, String columnId);

    void onCellLongPress(int row, String columnId, Rect cellRect);

    // 表头单击：水列弹水百分比，发酵料列弹计算方式
    void onHeaderClick(String columnId, Rect cellRect);

    // 表头长按：列操作菜单（固定、隐藏、全部显示、取消全部固定）
    void onHeaderLongPress(String columnId, Rect cellRect);

    // 表尾单击：序号列、棚号列、末列的按钮
    void onFooterClick(String columnId);
}
