package com.shrimpfarm.app.mixcalc.table;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;

// 画笔和颜色都放这，构造时建好反复用。颜色照旧 drawable 抄的，
// 自绘出来跟改造前一样：普通格白底黑边，禁用格灰，换料提示浅绿，超限格黄底红边，表尾蓝/洋红按钮。
public class CellStyle {

    public static final int STATE_NORMAL = 0;
    public static final int STATE_DISABLED = 1;
    public static final int STATE_HIGHLIGHT = 2;
    public static final int STATE_WARNING = 3;

    public static final int HEADER_NORMAL = 0;
    public static final int HEADER_FERMENTED_GREEN_BROWN = 1;
    public static final int HEADER_FERMENTED_GRAY_BROWN = 2;
    public static final int HEADER_FERMENTED_GREEN = 3;
    public static final int HEADER_FERMENTED_GRAY = 4;

    public static final int FOOTER_NORMAL = 0;
    public static final int FOOTER_SEQ = 1;
    public static final int FOOTER_BLUE = 2;
    public static final int FOOTER_CARMINE = 3;

    public final Paint fillPaint = new Paint();
    public final Paint borderPaint = new Paint();
    public final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint headerTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public final float borderWidth;
    public final float boldBorderWidth;

    public final int headerBg = 0xFFE8F5E9;
    public final int footerBg = 0xFFF0F0F0;
    public final int bodyBg = 0xFFFFFFFF;

    public final int cellFill = 0xFFFFFFFF;
    public final int cellStroke = 0xFF000000;
    public final int cellDisabledFill = 0xFFF0F0F0;
    public final int cellDisabledStroke = 0xFFCCCCCC;
    public final int cellRatioFill = 0xFFF0F8F0;
    public final int cellWarningFill = 0xFFFFC64B;
    public final int cellWarningStroke = 0xFFFF0000;

    public final int buttonBlueFill = 0xFF000042;
    public final int buttonCarmineFill = 0xFF960018;

    public final int fermentedFillGreen = 0xFFF0F8F0;
    public final int fermentedFillGray = 0xFFF0F0F0;
    public final int fermentedStrokeBrown = 0xFF6E511E;
    public final int fermentedStrokeBlack = 0xFF000000;

    public final int textWhite = 0xFFFFFFFF;
    public final int textBlack = 0xFF000000;
    public final int textValue = 0xFF0000FF;
    public final int textSeq = 0xFF666666;

    // 冻结列和滚动列的交界竖线
    public final int frozenDivider = 0x33000000;

    public final int rowHeight;
    public final int headerHeight;
    public final int footerHeight;

    public CellStyle(Context context, int rowHeightPx, int headerHeightPx, int footerHeightPx) {
        this.rowHeight = rowHeightPx;
        this.headerHeight = headerHeightPx;
        this.footerHeight = footerHeightPx;

        float density = context.getResources().getDisplayMetrics().density;
        float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12,
                context.getResources().getDisplayMetrics());

        borderWidth = Math.max(1f, 0.5f * density);
        boldBorderWidth = Math.max(2f, 1f * density);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(false);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setAntiAlias(false);
        borderPaint.setStrokeWidth(borderWidth);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        headerTextPaint.set(textPaint);
    }

    public int bodyFill(int state) {
        switch (state) {
            case STATE_DISABLED:
                return cellDisabledFill;
            case STATE_HIGHLIGHT:
                return cellRatioFill;
            case STATE_WARNING:
                return cellWarningFill;
            default:
                return cellFill;
        }
    }

    public int bodyStroke(int state) {
        switch (state) {
            case STATE_DISABLED:
                return cellDisabledStroke;
            case STATE_WARNING:
                return cellWarningStroke;
            default:
                return cellStroke;
        }
    }

    public float bodyStrokeWidth(int state) {
        return state == STATE_WARNING ? boldBorderWidth : borderWidth;
    }

    public int headerFill(int state) {
        switch (state) {
            case HEADER_FERMENTED_GREEN_BROWN:
            case HEADER_FERMENTED_GREEN:
                return fermentedFillGreen;
            case HEADER_FERMENTED_GRAY_BROWN:
            case HEADER_FERMENTED_GRAY:
                return fermentedFillGray;
            default:
                return cellFill;
        }
    }

    public int headerStroke(int state) {
        switch (state) {
            case HEADER_FERMENTED_GREEN_BROWN:
            case HEADER_FERMENTED_GRAY_BROWN:
                return fermentedStrokeBrown;
            default:
                return fermentedStrokeBlack;
        }
    }

    public float headerStrokeWidth(int state) {
        switch (state) {
            case HEADER_FERMENTED_GREEN_BROWN:
            case HEADER_FERMENTED_GRAY_BROWN:
                return boldBorderWidth * 3f;
            default:
                return borderWidth;
        }
    }

    public int footerFill(int style) {
        if (style == FOOTER_BLUE) {
            return buttonBlueFill;
        }
        if (style == FOOTER_CARMINE) {
            return buttonCarmineFill;
        }
        return cellFill;
    }

    public int footerTextColor(int style) {
        if (style == FOOTER_BLUE || style == FOOTER_CARMINE) {
            return textWhite;
        }
        return textValue;
    }
}
