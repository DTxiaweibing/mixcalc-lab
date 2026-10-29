package com.shrimpfarm.app.mixcalc.table;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

// 自绘胶囊按钮：圆角绿底 + 白字。
// 为什么不用 TextView：TextView 的文字位置受字体 ascent/descent 影响，
// 混上「▾」这种符号后视觉上会偏上；这里直接用 fontMetrics 算基线，保证上下真正居中。
class PillButtonView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private String text = "";

    PillButtonView(Context context, int bgColor, int textColor, float textSizePx) {
        super(context);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(bgColor);
        textPaint.setColor(textColor);
        textPaint.setTextSize(textSizePx);
        textPaint.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
        setFocusable(false);
    }

    void setLabel(String label) {
        this.text = label == null ? "" : label;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = getHeight() / 2f;
        rect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, radius, radius, bgPaint);

        // 文字垂直居中：把文本的 (ascent..descent) 这段放到控件高度正中
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(text, getWidth() / 2f, baseline, textPaint);
    }
}
