package com.shrimpfarm.app.mixcalc.table;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;

import com.shrimpfarm.app.mixcalc.model.ShedDisplay;

import java.util.List;

/**
 * 棚号列的展示态画笔：ShedDisplay 两行极简折叠、省略号只来自折叠算法、
 * 放不下裁掉、系统不补任何 "…"。
 *
 * <p>字号按显示档位固定（与鸿蒙 shedFontSizeForMode 同源）：11 列铺满（窗口 ≥600dp）
 * 15dp / 每行 9 字符；手机横向滚动 14dp / 每行 7 字符。用 density 而不是 scaledDensity
 * 换算——不乘缩放系数、不按内容自适应、不跟随系统字体大小。</p>
 */
public final class ShedTextPainter {

    private static final int TEXT_SIZE_FULL_DP = 15;
    private static final int TEXT_SIZE_SCROLL_DP = 14;

    private final float density;
    private boolean fullMode;

    public ShedTextPainter(float density) {
        this.density = density;
    }

    /** true = 11 列铺满（15dp/9 字符），false = 横向滚动（14dp/7 字符）。 */
    public boolean isFullMode() {
        return fullMode;
    }

    public void setFullMode(boolean fullMode) {
        this.fullMode = fullMode;
    }

    public void draw(Canvas canvas, String text, int left, int top, int right, int bottom,
                     int color, Paint paint) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        paint.setColor(color);
        List<String> lines = ShedDisplay.renderForMode(text, fullMode);
        if (lines.isEmpty()) {
            return;
        }
        // 固定字号，不随内容、不随系统字体设置变（textPaint 是共用的，画完必须还原）
        float originalTextSize = paint.getTextSize();
        int textSizeDp = fullMode ? TEXT_SIZE_FULL_DP : TEXT_SIZE_SCROLL_DP;
        paint.setTextSize((int) (textSizeDp * density + 0.5f));
        Paint.FontMetrics fm = paint.getFontMetrics();
        float lineH = fm.descent - fm.ascent;
        float totalH = lineH * lines.size();
        float cx = (left + right) / 2f;
        float startY = (top + bottom) / 2f - totalH / 2f - fm.ascent;
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        for (int i = 0; i < lines.size(); i++) {
            // textPaint 是居中对齐（Paint.Align.CENTER），直接画在中心线
            canvas.drawText(lines.get(i), cx, startY + i * lineH, paint);
        }
        canvas.restore();

        paint.setTextSize(originalTextSize);
    }
}
