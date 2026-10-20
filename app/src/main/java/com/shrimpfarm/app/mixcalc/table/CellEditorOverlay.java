package com.shrimpfarm.app.mixcalc.table;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.shrimpfarm.app.R;

/**
 * 单元格编辑浮层：整张表只有一个 {@link EditText}，需要编辑时才显示在命中格上头。
 *
 * <p>它把编辑入口从"每个可编辑格常驻一个 EditText"改成"整表一个浮层"，但用户看到的仍是原生
 * 输入法 / 焦点 / 光标，输入体验不变。</p>
 *
 * <p>职责：</p>
 * <ul>
 *   <li>{@link #begin}：把浮层精确贴到命中格上并唤起输入法</li>
 *   <li>文本变化即实时回调 {@link OnCellEditListener#onCellCommitted}（保留原有"边输边算"）</li>
 *   <li>{@link #onScrolled}：滚动时跟随移动；滚出可见区则提交并收起</li>
 *   <li>输入法"下一个" → 回调 {@link OnCellEditListener#onEditNext}</li>
 * </ul>
 */
class CellEditorOverlay {

    private final MixCalcTableView table;
    private final Context context;
    private final EditText editText;

    private int row = -1;
    private TableColumn column;
    private boolean editing;
    /** 程序化设置文本期间抑制回调，避免"打开即提交"。 */
    private boolean suppress;
    /** 用户是否真正改过文本：只点一下获得焦点就离开时不应提交。 */
    private boolean dirty;

    CellEditorOverlay(MixCalcTableView table) {
        this.table = table;
        this.context = table.getContext();

        editText = new EditText(context);
        editText.setVisibility(View.GONE);
        editText.setBackgroundResource(R.drawable.cell_border);
        editText.setGravity(Gravity.CENTER);
        editText.setTextSize(12);
        editText.setTypeface(editText.getTypeface(), Typeface.BOLD);
        editText.setPadding(0, 0, 0, 0);
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppress || !editing || column == null) {
                    return;
                }
                dirty = true;
                OnCellEditListener listener = table.editListener();
                if (listener != null) {
                    listener.onCellCommitted(row, column.id, s.toString());
                }
            }
        });

        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT && editing && column != null) {
                OnCellEditListener listener = table.editListener();
                if (listener != null) {
                    listener.onEditNext(row, column.id);
                }
                return true;
            }
            return false;
        });
    }

    /** 把浮层挂到表格里（唯一子 View）。 */
    void attach() {
        table.addView(editText);
    }

    /**
     * 打开编辑浮层。
     *
     * <p>若当前正在编辑另一格，先把旧格提交（不隐藏浮层），再平滑切到新格，输入法不闪断。</p>
     */
    void begin(int row, TableColumn column) {
        if (column == null || !column.editable) {
            return;
        }
        if (editing && (this.row != row || this.column == null || !this.column.id.equals(column.id))) {
            commitInternal();
        }

        this.row = row;
        this.column = column;
        editing = true;

        suppress = true;
        try {
            editText.setInputType(column.inputType);
            editText.setTextColor(column.textColor);
            String text = table.textProvider() == null
                    ? ""
                    : table.textProvider().getCellText(row, column.id);
            editText.setText(text == null ? "" : text);
            editText.setSelection(editText.getText().length());
        } finally {
            suppress = false;
        }
        dirty = false;

        if (editText.getVisibility() != View.VISIBLE) {
            editText.setVisibility(View.VISIBLE);
        }
        layoutChild();
        if (!editText.hasFocus()) {
            editText.requestFocus();
        }
        OnCellEditListener listener = table.editListener();
        if (listener != null) {
            listener.onCellEditBegin(row, column.id);
        }
        showIme();
    }

    /** 提交并收起浮层。 */
    void end() {
        if (!editing) {
            return;
        }
        editing = false;
        commitInternal();
        editText.setVisibility(View.GONE);
    }

    /** 表格滚动后调用：跟随移动，滚出可见区则结束。 */
    void onScrolled() {
        if (!editing || column == null) {
            return;
        }
        if (!table.isCellVisible(row, column.id)) {
            end();
            return;
        }
        layoutChild();
    }

    /** 列配置/尺寸变化后调用：重定位，必要时结束。 */
    void onMetricsChanged() {
        if (!editing || column == null) {
            return;
        }
        // 尺寸变化（典型是输入法弹出把表挤矮）时先尝试把该行滚回可视区，
        // 实在留不住才收起 —— 避免"点低位格 → 键盘刚弹就自己收掉"。
        if (!table.keepEditingCellVisible(row, column.id)) {
            end();
            return;
        }
        layoutChild();
    }

    /** 是否有浮层正在编辑（供表格决定要不要做冻结列裁剪）。 */
    boolean isEditing() {
        return editing && column != null;
    }

    /** 正在编辑的格是否落在固定列中（决定浮层裁剪到哪一侧）。 */
    boolean isEditingFixedColumn() {
        if (!editing || column == null) {
            return false;
        }
        int vi = table.metrics().visualIndex(column.id);
        return vi >= 0 && vi < table.metrics().fixedCount;
    }

    /** 依据命中格矩形摆放浮层（表格 onLayout 与滚动时都会调用）。 */
    void layoutChild() {
        if (!editing || column == null) {
            return;
        }
        Rect r = table.getCellRect(row, column.id);
        if (r == null) {
            return;
        }
        int w = Math.max(0, r.width());
        int h = Math.max(0, r.height());
        editText.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        editText.layout(r.left, r.top, r.right, r.bottom);
    }

    private void commitInternal() {
        if (!dirty) {
            return;
        }
        OnCellEditListener listener = table.editListener();
        if (listener == null || column == null || row < 0) {
            return;
        }
        dirty = false;
        listener.onCellCommitted(row, column.id, editText.getText().toString());
    }

    private void showIme() {
        editText.post(() -> {
            if (!editText.isAttachedToWindow()) {
                return;
            }
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }
}
