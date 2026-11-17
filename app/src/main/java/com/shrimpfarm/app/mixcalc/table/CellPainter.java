package com.shrimpfarm.app.mixcalc.table;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;

/**
 * 自绘基础笔刷：区域底色、格子（填充 + 描边）、居中文字、冻结列竖线。
 *
 * <p>从 {@code MixCalcTableView} 抽出来，那文件已超 500 行有效代码，按 R3 不得再变长。
 * 画笔全部来自 {@link CellStyle}，共用的 {@code textPaint} 用完必须还原字号。</p>
 */
public final class CellPainter {

    private CellPainter() {
    }

    public static void regionBg(Canvas canvas, CellStyle style, int left, int top, int right, int bottom,
                                int color) {
        if (bottom <= top) {
            return;
        }
        style.fillPaint.setStyle(Paint.Style.FILL);
        style.fillPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, style.fillPaint);
    }

    public static void box(Canvas canvas, CellStyle style, int left, int top, int right, int bottom,
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

    public static void text(Canvas canvas, String text, int left, int top, int right, int bottom,
                            int color, Paint paint) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        paint.setColor(color);

        // 文字比格子宽就缩小字号塞进去，防止超出单元格（英文表头词普遍比中文长）。
        // textPaint 是共用的，缩完必须还原，不然会影响后面的格子。
        float originalTextSize = paint.getTextSize();
        float availWidth = (right - left) - 4f;
        float textWidth = paint.measureText(text);
        if (textWidth > availWidth && textWidth > 0) {
            float scaled = originalTextSize * (availWidth / textWidth);
            paint.setTextSize(Math.max(scaled, originalTextSize * 0.5f));
        }

        Paint.FontMetrics fm = paint.getFontMetrics();
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f - (fm.ascent + fm.descent) / 2f;

        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        canvas.drawText(text, cx, cy, paint);
        canvas.restore();

        paint.setTextSize(originalTextSize);
    }

    public static void frozenDivider(Canvas canvas, CellStyle style, int fixedWidth, int height) {
        if (fixedWidth <= 0) {
            return;
        }
        style.borderPaint.setStyle(Paint.Style.STROKE);
        style.borderPaint.setColor(style.frozenDivider);
        style.borderPaint.setStrokeWidth(1f);
        canvas.drawLine(fixedWidth, 0, fixedWidth, height, style.borderPaint);
    }
}
