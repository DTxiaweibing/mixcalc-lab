package com.shrimpfarm.app.mixcalc.table;

// 编辑浮层的回调。
public interface OnCellEditListener {

    // 开始编辑某格，在浮层打开、用户输入之前回调，宿主拿它记该格的原值。
    // 为什么需要原值：浮层逐字符提交，退格退到最后一位时格里的数早被改小了，
    // 只有"进入编辑那一刻"的快照才是真正的原值。
    void onCellEditBegin(int row, String columnId);

    // 提交：失焦、格滚出可见区、点了别的格，都会触发。
    // text 是提交时的原始文本，没 trim 也没解析
    void onCellCommitted(int row, String columnId, String text);

    // 输入法点"下一个"（IME_ACTION_NEXT），跳到哪个可编辑格由宿主定
    void onEditNext(int row, String columnId);
}
