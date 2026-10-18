package com.shrimpfarm.app.mixcalc.table;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;

/**
 * 集中所有画笔与颜色，全部在构造期建好并复用。
 *
 * <p>颜色取自原有 drawable，保证自绘外观与改造前一致：
 * 普通格白底黑边 0.5dp、禁用格 #F0F0F0 灰边、换料比例提示格 #F0F8F0、
 * 棚数超限警告格 #FFC64B 红边 1dp、表尾蓝/洋红按钮。</p>
 */
public class CellStyle {

    // ---- 单元格视觉状态（由 CellTextProvider 提供）----
    public static final int STATE_NORMAL = 0;
    public static final int STATE_DISABLED = 1;
    public static final int STATE_HIGHLIGHT = 2;
    public static final int STATE_WARNING = 3;

    // ---- 表头视觉状态（由 CellTextProvider 提供）----
    public static final int HEADER_NORMAL = 0;
    public static final int HEADER_FERMENTED_GREEN_BROWN = 1;
    public static final int HEADER_FERMENTED_GRAY_BROWN = 2;
    public static final int HEADER_FERMENTED_GREEN = 3;
    public static final int HEADER_FERMENTED_GRAY = 4;

    // ---- 表尾样式（由 CellTextProvider 提供）----
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

    /** 冻结列与滚动列交界处的分隔线颜色。 */
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

    /** 正文格填充色。 */
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

    /** 正文格边框色。 */
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

    /** 正文格边框宽度。 */
    public float bodyStrokeWidth(int state) {
        return state == STATE_WARNING ? boldBorderWidth : borderWidth;
    }

    /** 表头填充色。 */
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

    /** 表头边框色。 */
    public int headerStroke(int state) {
        switch (state) {
            case HEADER_FERMENTED_GREEN_BROWN:
            case HEADER_FERMENTED_GRAY_BROWN:
                return fermentedStrokeBrown;
            default:
                return fermentedStrokeBlack;
        }
    }

    /** 表头边框宽度。 */
    public float headerStrokeWidth(int state) {
        switch (state) {
            case HEADER_FERMENTED_GREEN_BROWN:
            case HEADER_FERMENTED_GRAY_BROWN:
                return boldBorderWidth * 3f;
            default:
                return borderWidth;
        }
    }

    /** 表尾填充色。 */
    public int footerFill(int style) {
        if (style == FOOTER_BLUE) {
            return buttonBlueFill;
        }
        if (style == FOOTER_CARMINE) {
            return buttonCarmineFill;
        }
        return cellFill;
    }

    /** 表尾文本色。 */
    public int footerTextColor(int style) {
        if (style == FOOTER_BLUE || style == FOOTER_CARMINE) {
            return textWhite;
        }
        return textValue;
    }
}
