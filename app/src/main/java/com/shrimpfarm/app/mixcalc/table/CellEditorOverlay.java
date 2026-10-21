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

// 单元格编辑浮层。整张表就一个 EditText，哪个格要编辑就贴到它上面。
// 输入法、焦点、光标都是原生的，手感跟以前每个格各带一个 EditText 时一样。
// begin 打开并弹键盘；文本一变就回调 onCellCommitted（保持边输边算）；
// onScrolled 跟随滚动，滚出可见区就收起；键盘点"下一个"回调 onEditNext。
class CellEditorOverlay {

    private final MixCalcTableView table;
    private final Context context;
    private final EditText editText;

    private int row = -1;
    private TableColumn column;
    private boolean editing;
    // 程序化 setText 时压住回调，不然一打开就当提交了
    private boolean suppress;
    // 用户是否真的动过文本。只是点一下拿到焦点又走开，不该提交
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

    void attach() {
        table.addView(editText);
    }

    // 打开浮层。要是正在编辑另一格，先把旧的提交掉，再切到新格，键盘不闪。
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

    // 提交并收起
    void end() {
        if (!editing) {
            return;
        }
        editing = false;
        commitInternal();
        editText.setVisibility(View.GONE);
    }

    // 表格滚过之后跟着挪，滚出可见区就结束
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

    // 列配置或尺寸变了之后重定位，实在留不住才收起
    void onMetricsChanged() {
        if (!editing || column == null) {
            return;
        }
        // 尺寸变化多半是键盘弹出把表挤矮了。先把这行滚回可视区，
        // 留不住再收，免得"点低位格，键盘刚弹就自己没了"。
        if (!table.keepEditingCellVisible(row, column.id)) {
            end();
            return;
        }
        layoutChild();
    }

    // 表格靠它判断要不要做冻结列裁剪
    boolean isEditing() {
        return editing && column != null;
    }

    // 正在编辑的格是不是固定列，决定浮层往哪边裁
    boolean isEditingFixedColumn() {
        if (!editing || column == null) {
            return false;
        }
        int vi = table.metrics().visualIndex(column.id);
        return vi >= 0 && vi < table.metrics().fixedCount;
    }

    // 按命中格的矩形摆浮层，onLayout 和滚动时都会调
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
