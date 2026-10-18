package com.shrimpfarm.app.mixcalc.table;

/**
 * 编辑浮层回调。
 */
public interface OnCellEditListener {

    /**
     * 编辑提交：失焦、滚动导致格子在可见区之外、或点击其他格子时触发。
     *
     * @param text 提交时的原始文本（未 trim、未解析）
     */
    void onCellCommitted(int row, String columnId, String text);

    /** 输入法"下一个"（IME_ACTION_NEXT），由宿主决定跳到哪个可编辑格。 */
    void onEditNext(int row, String columnId);
}
