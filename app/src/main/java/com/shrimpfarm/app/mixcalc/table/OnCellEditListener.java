package com.shrimpfarm.app.mixcalc.table;

/**
 * 编辑浮层回调。
 */
public interface OnCellEditListener {

    /**
     * 开始编辑某格：在浮层打开、用户输入之前回调，供宿主记录该格的原值。
     *
     * <p>编辑浮层逐字符提交，退格清空到最后一位时单元格早已被改小，
     * 需要这份"进入编辑那一刻"的快照才能还原真正的原值。</p>
     */
    void onCellEditBegin(int row, String columnId);

    /**
     * 编辑提交：失焦、滚动导致格子在可见区之外、或点击其他格子时触发。
     *
     * @param text 提交时的原始文本（未 trim、未解析）
     */
    void onCellCommitted(int row, String columnId, String text);

    /** 输入法"下一个"（IME_ACTION_NEXT），由宿主决定跳到哪个可编辑格。 */
    void onEditNext(int row, String columnId);
}
