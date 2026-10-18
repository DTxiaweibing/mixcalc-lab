package com.shrimpfarm.app.mixcalc.table;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

import com.shrimpfarm.app.mixcalc.model.FeedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 自绘表格：整张表只有一个坐标系、一个 View。
 *
 * <p>横滚 / 纵滚 / 冻结列 / 吸顶表头 / 吸底表尾 全部由两个偏移量与统一几何决定：</p>
 * <ul>
 *   <li>横向只有一个 {@code offsetX}，表头与表尾复用同一偏移 → 横滚时一起动、天然对齐</li>
 *   <li>表头 y 钉在 0、表尾 y 钉在底部 → 纵滚时都不动</li>
 *   <li>固定列绘制时不加横向偏移 → 冻结</li>
 * </ul>
 *
 * <p>不存在"多个滚动容器互相同步"，因此不存在拖尾、回弹与边界错位。</p>
 */
public class MixCalcTableView extends ViewGroup {

    private final List<FeedData> rows = new ArrayList<>();
    private TableColumnConfig config;
    private CellTextProvider textProvider;
    private OnCellTapListener tapListener;
    private OnCellEditListener editListener;

    private final TableMetrics metrics = new TableMetrics();
    private final CellStyle style;
    private final CellEditorOverlay editor;

    private final OverScroller scroller;
    private final int rowHeight;
    private final int headerHeight;
    private final int footerHeight;
    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;
    private final int longPressTimeout;

    private VelocityTracker velocityTracker;
    private int offsetX;
    private int offsetY;

    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private boolean dragging;
    private boolean longPressFired;
    private boolean metricsReady;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (dragging || longPressFired) {
                return;
            }
            longPressFired = true;
            handleLongPress(downX, downY);
        }
    };

    public MixCalcTableView(Context context) {
        this(context, null);
    }

    public MixCalcTableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClipChildren(true);
        setClipToPadding(true);

        rowHeight = dp(context, 40);
        headerHeight = dp(context, 40);
        footerHeight = dp(context, 40);
        style = new CellStyle(context, rowHeight, headerHeight, footerHeight);

        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        longPressTimeout = ViewConfiguration.getLongPressTimeout();

        scroller = new OverScroller(context);
        editor = new CellEditorOverlay(this);
        editor.attach();
    }

    // ------------------------------------------------------------------
    // 对外接口
    // ------------------------------------------------------------------

    public void setColumnConfig(TableColumnConfig config) {
        this.config = config;
        requestRebuild();
    }

    public void setRows(List<FeedData> rows) {
        this.rows.clear();
        if (rows != null) {
            this.rows.addAll(rows);
        }
        requestRebuild();
    }

    public void setCellTextProvider(CellTextProvider provider) {
        this.textProvider = provider;
    }

    public void setOnCellTapListener(OnCellTapListener listener) {
        this.tapListener = listener;
    }

    public void setOnCellEditListener(OnCellEditListener listener) {
        this.editListener = listener;
    }

    /** 列配置或行数变化后调用：重新测量布局并重绘。 */
    public void requestRebuild() {
        requestLayout();
        invalidate();
    }

    /** 全部数据变化：重新钳制偏移并整表重绘。 */
    public void notifyAllChanged() {
        clampOffsets();
        editor.onScrolled();
        invalidate();
    }

    /** 单个单元格变化：局部重绘。 */
    public void notifyCellChanged(int row, String columnId) {
        Rect r = getCellRect(row, columnId);
        if (r == null) {
            invalidate();
            return;
        }
        invalidate(r.left - 2, r.top - 2, r.right + 2, r.bottom + 2);
    }

    public void scrollToX(int x) {
        offsetX = clamp(x, 0, metrics.maxScrollX);
        onOffsetChanged();
    }

    public int getTableScrollX() {
        return offsetX;
    }

    public boolean beginEdit(int row, String columnId) {
        if (config == null) {
            return false;
        }
        TableColumn col = config.get(columnId);
        if (col == null || !col.editable) {
            return false;
        }
        editor.begin(row, col);
        return true;
    }

    public void endEdit() {
        editor.end();
    }

    /** 单元格在表格视图内的矩形（供弹窗做锚点）。 */
    public Rect getCellRect(int row, String columnId) {
        if (!metricsReady || metrics.visual.isEmpty()) {
            return null;
        }
        int vi = metrics.visualIndex(columnId);
        if (vi < 0) {
            return null;
        }
        TableColumn col = metrics.visual.get(vi);
        int x = metrics.xOf(vi, offsetX);
        int y = metrics.yOfRow(row, offsetY);
        return new Rect(x, y, x + col.widthPx, y + metrics.rowHeight);
    }

    public Rect getHeaderRect(String columnId) {
        if (!metricsReady || metrics.visual.isEmpty()) {
            return null;
        }
        int vi = metrics.visualIndex(columnId);
        if (vi < 0) {
            return null;
        }
        TableColumn col = metrics.visual.get(vi);
        int x = metrics.xOf(vi, offsetX);
        return new Rect(x, 0, x + col.widthPx, metrics.headerHeight);
    }

    public Rect getFooterRect(String columnId) {
        if (!metricsReady || metrics.visual.isEmpty()) {
            return null;
        }
        int vi = metrics.visualIndex(columnId);
        if (vi < 0) {
            return null;
        }
        TableColumn col = metrics.visual.get(vi);
        int x = metrics.xOf(vi, offsetX);
        int top = getHeight() - metrics.footerHeight;
        return new Rect(x, top, x + col.widthPx, getHeight());
    }

    /** 该格是否处于可见区（编辑浮层据此决定是否提交并隐藏）。 */
    public boolean isCellVisible(int row, String columnId) {
        Rect r = getCellRect(row, columnId);
        if (r == null) {
            return false;
        }
        int bodyTop = metrics.headerHeight;
        int bodyBottom = getHeight() - metrics.footerHeight;
        if (r.bottom <= bodyTop + 1 || r.top >= bodyBottom - 1) {
            return false;
        }
        int vi = metrics.visualIndex(columnId);
        boolean fixed = vi < metrics.fixedCount;
        int left = fixed ? 0 : metrics.fixedWidth;
        int right = fixed ? metrics.fixedWidth : getWidth();
        return r.right > left + 1 && r.left < right - 1;
    }

    // 以下包内访问，供 CellEditorOverlay 使用
    CellStyle style() {
        return style;
    }

    TableMetrics metrics() {
        return metrics;
    }

    CellTextProvider textProvider() {
        return textProvider;
    }

    OnCellEditListener editListener() {
        return editListener;
    }

    TableColumnConfig config() {
        return config;
    }

    // ------------------------------------------------------------------
    // 测量 / 布局
    // ------------------------------------------------------------------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);

        metricsReady = true;
        metrics.rebuild(config, rows.size(), w, h, rowHeight, headerHeight, footerHeight);
        clampOffsets();
        editor.onMetricsChanged();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        editor.layoutChild();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            metrics.rebuild(config, rows.size(), w, h, rowHeight, headerHeight, footerHeight);
            clampOffsets();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(longPressRunnable);
        releaseVelocityTracker();
    }

    // ------------------------------------------------------------------
    // 滚动
    // ------------------------------------------------------------------

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.getCurrX();
            offsetY = scroller.getCurrY();
            onOffsetChanged();
            postInvalidateOnAnimation();
        }
    }

    private boolean scrollByTable(int dx, int dy) {
        int nx = clamp(offsetX + dx, 0, metrics.maxScrollX);
        int ny = clamp(offsetY + dy, 0, metrics.maxScrollY);
        if (nx == offsetX && ny == offsetY) {
            return false;
        }
        offsetX = nx;
        offsetY = ny;
        onOffsetChanged();
        return true;
    }

    private void onOffsetChanged() {
        editor.onScrolled();
        invalidate();
    }

    private void clampOffsets() {
        offsetX = clamp(offsetX, 0, metrics.maxScrollX);
        offsetY = clamp(offsetY, 0, metrics.maxScrollY);
    }

    // ------------------------------------------------------------------
    // 触摸
    // ------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (config == null || metrics.visual.isEmpty()) {
            return false;
        }

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                downX = ev.getX();
                downY = ev.getY();
                lastX = downX;
                lastY = downY;
                dragging = false;
                longPressFired = false;
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                } else {
                    velocityTracker.clear();
                }
                velocityTracker.addMovement(ev);
                removeCallbacks(longPressRunnable);
                postDelayed(longPressRunnable, longPressTimeout);
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(ev);
                }
                if (!dragging) {
                    if (Math.abs(ev.getX() - downX) > touchSlop
                            || Math.abs(ev.getY() - downY) > touchSlop) {
                        dragging = true;
                        removeCallbacks(longPressRunnable);
                        lastX = ev.getX();
                        lastY = ev.getY();
                    }
                    return true;
                }
                int dx = (int) (lastX - ev.getX());
                int dy = (int) (lastY - ev.getY());
                lastX = ev.getX();
                lastY = ev.getY();
                scrollByTable(dx, dy);
                return true;
            }

            case MotionEvent.ACTION_UP:
                removeCallbacks(longPressRunnable);
                if (dragging) {
                    if (velocityTracker != null) {
                        velocityTracker.addMovement(ev);
                        velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                        int vx = (int) velocityTracker.getXVelocity();
                        int vy = (int) velocityTracker.getYVelocity();
                        if (Math.abs(vx) > minFlingVelocity || Math.abs(vy) > minFlingVelocity) {
                            scroller.fling(offsetX, offsetY, -vx, -vy,
                                    0, metrics.maxScrollX, 0, metrics.maxScrollY);
                            postInvalidateOnAnimation();
                        }
                    }
                } else if (!longPressFired) {
                    performClick();
                    handleTap(ev.getX(), ev.getY());
                }
                dragging = false;
                longPressFired = false;
                releaseVelocityTracker();
                return true;

            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                dragging = false;
                longPressFired = false;
                releaseVelocityTracker();
                return true;

            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void handleTap(float x, float y) {
        if (y < metrics.headerHeight) {
            editor.end();
            int vi = metrics.visualIndexAt(x, offsetX);
            if (vi >= 0 && tapListener != null) {
                tapListener.onHeaderClick(metrics.visual.get(vi).id);
            }
            return;
        }
        if (y >= getHeight() - metrics.footerHeight) {
            editor.end();
            int vi = metrics.visualIndexAt(x, offsetX);
            if (vi >= 0 && tapListener != null) {
                tapListener.onFooterClick(metrics.visual.get(vi).id);
            }
            return;
        }

        int row = metrics.rowAt(y, offsetY);
        int vi = metrics.visualIndexAt(x, offsetX);
        if (row < 0 || vi < 0) {
            editor.end();
            return;
        }
        TableColumn col = metrics.visual.get(vi);

        if (col.editable
                && (textProvider == null || textProvider.isCellEditable(row, col.id))) {
            editor.begin(row, col);
        } else {
            editor.end();
        }
        if (tapListener != null) {
            tapListener.onCellClick(row, col.id);
        }
    }

    private void handleLongPress(float x, float y) {
        if (tapListener == null) {
            return;
        }
        editor.end();
        if (y < metrics.headerHeight) {
            int vi = metrics.visualIndexAt(x, offsetX);
            if (vi >= 0) {
                TableColumn col = metrics.visual.get(vi);
                tapListener.onHeaderLongPress(col.id, getHeaderRect(col.id));
            }
            return;
        }
        if (y >= getHeight() - metrics.footerHeight) {
            return;
        }
        int row = metrics.rowAt(y, offsetY);
        int vi = metrics.visualIndexAt(x, offsetX);
        if (row < 0 || vi < 0) {
            return;
        }
        TableColumn col = metrics.visual.get(vi);
        tapListener.onCellLongPress(row, col.id, getCellRect(row, col.id));
    }

    private void releaseVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (config == null || metrics.visual.isEmpty()) {
            return;
        }

        int h = getHeight();
        drawRegionBg(canvas, 0, 0, getWidth(), metrics.headerHeight, style.headerBg);
        drawRegionBg(canvas, 0, metrics.headerHeight, getWidth(), h - metrics.footerHeight, style.bodyBg);
        drawRegionBg(canvas, 0, h - metrics.footerHeight, getWidth(), h, style.footerBg);

        drawBody(canvas);
        drawHeader(canvas);
        drawFooter(canvas);
        drawFrozenDivider(canvas);
    }

    private void drawRegionBg(Canvas canvas, int left, int top, int right, int bottom, int color) {
        if (bottom <= top) {
            return;
        }
        style.fillPaint.setStyle(Paint.Style.FILL);
        style.fillPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, style.fillPaint);
    }

    private void drawBody(Canvas canvas) {
        int top = metrics.headerHeight;
        int bottom = getHeight() - metrics.footerHeight;
        if (bottom <= top || metrics.rowCount <= 0) {
            return;
        }

        int firstRow = Math.max(0, offsetY / metrics.rowHeight);
        int lastRow = Math.min(metrics.rowCount - 1,
                (offsetY + (bottom - top) - 1) / metrics.rowHeight);
        if (firstRow > lastRow) {
            return;
        }

        // 1. 非固定列（clip 到固定列右侧，滚动内容不会盖住冻结列）
        canvas.save();
        canvas.clipRect(metrics.fixedWidth, top, getWidth(), bottom);
        for (int vi = metrics.fixedCount; vi < metrics.visual.size(); vi++) {
            TableColumn col = metrics.visual.get(vi);
            int x = metrics.xOf(vi, offsetX);
            if (x >= getWidth() || x + col.widthPx <= metrics.fixedWidth) {
                continue;
            }
            for (int r = firstRow; r <= lastRow; r++) {
                drawBodyCell(canvas, col, x, metrics.yOfRow(r, offsetY), r);
            }
        }
        canvas.restore();

        // 2. 固定列
        canvas.save();
        canvas.clipRect(0, top, metrics.fixedWidth, bottom);
        for (int vi = 0; vi < metrics.fixedCount; vi++) {
            TableColumn col = metrics.visual.get(vi);
            int x = metrics.xOf(vi, offsetX);
            for (int r = firstRow; r <= lastRow; r++) {
                drawBodyCell(canvas, col, x, metrics.yOfRow(r, offsetY), r);
            }
        }
        canvas.restore();
    }

    private void drawBodyCell(Canvas canvas, TableColumn col, int x, int y, int row) {
        int state = textProvider == null
                ? CellStyle.STATE_NORMAL
                : textProvider.getCellState(row, col.id);
        int left = x;
        int top = y;
        int right = x + col.widthPx;
        int bottom = y + metrics.rowHeight;

        drawBox(canvas, left, top, right, bottom,
                style.bodyFill(state), style.bodyStroke(state), style.bodyStrokeWidth(state));

        String text = textProvider == null ? "" : textProvider.getCellText(row, col.id);
        drawCellText(canvas, text, left, top, right, bottom, col.textColor);
    }

    private void drawHeader(Canvas canvas) {
        int h = metrics.headerHeight;

        canvas.save();
        canvas.clipRect(metrics.fixedWidth, 0, getWidth(), h);
        for (int vi = metrics.fixedCount; vi < metrics.visual.size(); vi++) {
            TableColumn col = metrics.visual.get(vi);
            int x = metrics.xOf(vi, offsetX);
            if (x >= getWidth() || x + col.widthPx <= metrics.fixedWidth) {
                continue;
            }
            drawHeaderCell(canvas, col, x, h);
        }
        canvas.restore();

        canvas.save();
        canvas.clipRect(0, 0, metrics.fixedWidth, h);
        for (int vi = 0; vi < metrics.fixedCount; vi++) {
            TableColumn col = metrics.visual.get(vi);
            drawHeaderCell(canvas, col, metrics.xOf(vi, offsetX), h);
        }
        canvas.restore();
    }

    private void drawHeaderCell(Canvas canvas, TableColumn col, int x, int bottom) {
        int state = textProvider == null
                ? CellStyle.HEADER_NORMAL
                : textProvider.getHeaderState(col.id);
        int left = x;
        int top = 0;
        int right = x + col.widthPx;

        drawBox(canvas, left, top, right, bottom,
                style.headerFill(state), style.headerStroke(state), style.headerStrokeWidth(state));

        String text = textProvider == null ? "" : textProvider.getHeaderText(col.id);
        drawCellText(canvas, text, left, top, right, bottom, style.textBlack);
    }

    private void drawFooter(Canvas canvas) {
        int top = getHeight() - metrics.footerHeight;
        int bottom = getHeight();

        canvas.save();
        canvas.clipRect(metrics.fixedWidth, top, getWidth(), bottom);
        for (int vi = metrics.fixedCount; vi < metrics.visual.size(); vi++) {
            TableColumn col = metrics.visual.get(vi);
            int x = metrics.xOf(vi, offsetX);
            if (x >= getWidth() || x + col.widthPx <= metrics.fixedWidth) {
                continue;
            }
            drawFooterCell(canvas, col, x, top, bottom);
        }
        canvas.restore();

        canvas.save();
        canvas.clipRect(0, top, metrics.fixedWidth, bottom);
        for (int vi = 0; vi < metrics.fixedCount; vi++) {
            TableColumn col = metrics.visual.get(vi);
            drawFooterCell(canvas, col, metrics.xOf(vi, offsetX), top, bottom);
        }
        canvas.restore();
    }

    private void drawFooterCell(Canvas canvas, TableColumn col, int x, int top, int bottom) {
        int footerStyle = textProvider == null
                ? CellStyle.FOOTER_NORMAL
                : textProvider.getFooterStyle(col.id);
        int left = x;
        int right = x + col.widthPx;

        drawBox(canvas, left, top, right, bottom,
                style.footerFill(footerStyle), style.cellStroke, style.borderWidth);

        String text = textProvider == null ? "" : textProvider.getFooterText(col.id);
        drawCellText(canvas, text, left, top, right, bottom, style.footerTextColor(footerStyle));
    }

    private void drawFrozenDivider(Canvas canvas) {
        if (metrics.fixedCount <= 0 || metrics.fixedWidth <= 0) {
            return;
        }
        style.borderPaint.setStyle(Paint.Style.STROKE);
        style.borderPaint.setColor(style.frozenDivider);
        style.borderPaint.setStrokeWidth(1f);
        canvas.drawLine(metrics.fixedWidth, 0, metrics.fixedWidth, getHeight(), style.borderPaint);
    }

    private void drawBox(Canvas canvas, int left, int top, int right, int bottom,
                         int fill, int stroke, float strokeWidth) {
        style.fillPaint.setStyle(Paint.Style.FILL);
        style.fillPaint.setColor(fill);
        canvas.drawRect(left, top, right, bottom, style.fillPaint);

        if (strokeWidth > 0) {
            style.borderPaint.setStyle(Paint.Style.STROKE);
            style.borderPaint.setColor(stroke);
            style.borderPaint.setStrokeWidth(strokeWidth);
            float inset = strokeWidth / 2f;
            canvas.drawRect(left + inset, top + inset, right - inset, bottom - inset,
                    style.borderPaint);
        }
    }

    private void drawCellText(Canvas canvas, String text, int left, int top, int right, int bottom,
                              int color) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        Paint paint = style.textPaint;
        paint.setColor(color);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f;

        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        canvas.drawText(text, cx, cy, paint);
        canvas.restore();
    }

    // ------------------------------------------------------------------

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
