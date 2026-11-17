package com.shrimpfarm.app.mixcalc.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * 页面级胶囊提示，替掉系统 Toast（与鸿蒙版 ToastPill 对齐）。
 *
 * <p>贴在被锚定视图（表格）的顶部居中出现，满高 40dp 圆角胶囊，
 * 因此不会被表格的裁剪或列宽影响。文案过长时先缩字号（15dp → 10dp），
 * 缩到最小还放不下才省略号。</p>
 */
public final class PillToast {

    private static final int HEIGHT_DP = 40;
    private static final int PADDING_H_DP = 20;
    private static final int TOP_OFFSET_DP = 2;
    // 最宽占表格宽度 92%，与鸿蒙版 constraintSize maxWidth: '92%' 一致
    private static final float MAX_WIDTH_RATIO = 0.92f;
    private static final float MAX_TEXT_SP = 15f;
    private static final float MIN_TEXT_SP = 10f;
    private static final long DURATION_MS = 2200L;
    private static final int BG_COLOR = 0xFFFBE3E3;
    private static final int TEXT_COLOR = 0xFF7A1F1F;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static PopupWindow activePopup;
    private static Runnable pendingDismiss;

    private PillToast() {
    }

    /** 在 anchor 顶部居中弹一条提示；anchor 为 null 时退回窗口顶部。 */
    public static void show(Activity activity, View anchor, CharSequence message) {
        if (activity == null || message == null || message.length() == 0) {
            return;
        }
        if (activity.isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed())) {
            return;
        }
        dismiss();

        Context ctx = activity;
        final int maxWidth = maxWidthPx(ctx, anchor);
        final int height = dp(ctx, HEIGHT_DP);
        TextView view = buildView(ctx, message, maxWidth - dp(ctx, PADDING_H_DP) * 2);

        view.measure(
                View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));

        PopupWindow popup = new PopupWindow(view,
                ViewGroup.LayoutParams.WRAP_CONTENT, height, false);
        popup.setFocusable(false);
        popup.setTouchable(false);
        popup.setOutsideTouchable(false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setClippingEnabled(true);

        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor == null || !decor.isAttachedToWindow()) {
            return;
        }
        int[] pos = positionOf(decor, anchor, view.getMeasuredWidth());
        try {
            popup.showAtLocation(decor, Gravity.NO_GRAVITY, pos[0], pos[1]);
        } catch (Exception e) {
            // 窗口正在销毁/不可见：提示丢了也不能影响主流程
            return;
        }

        activePopup = popup;
        pendingDismiss = () -> dismiss();
        MAIN.postDelayed(pendingDismiss, DURATION_MS);
    }

    /** 立即收起当前提示（切页面、转屏、重开提示时调用）。 */
    public static void dismiss() {
        if (pendingDismiss != null) {
            MAIN.removeCallbacks(pendingDismiss);
            pendingDismiss = null;
        }
        if (activePopup != null) {
            try {
                activePopup.dismiss();
            } catch (Exception ignored) {
                // 已被窗口系统收走，忽略即可
            }
            activePopup = null;
        }
    }

    private static TextView buildView(Context ctx, CharSequence message, int availableWidth) {
        TextView view = new TextView(ctx);
        view.setText(message);
        view.setTextColor(TEXT_COLOR);
        view.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(false);
        int padH = dp(ctx, PADDING_H_DP);
        view.setPadding(padH, 0, padH, 0);
        view.setBackground(capsule(dp(ctx, HEIGHT_DP) / 2));

        float size = MAX_TEXT_SP;
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        while (size > MIN_TEXT_SP
                && view.getPaint().measureText(message, 0, message.length()) > availableWidth) {
            size -= 1f;
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        }
        return view;
    }

    private static GradientDrawable capsule(int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(BG_COLOR);
        bg.setCornerRadius(radius);
        return bg;
    }

    // 顶部居中的屏幕坐标：横向上贴表格中线、夹在屏幕内，纵向贴表格上沿
    private static int[] positionOf(View decor, View anchor, int width) {
        int screenW = decor.getWidth();
        if (screenW <= 0) {
            screenW = decor.getResources().getDisplayMetrics().widthPixels;
        }
        int anchorTop = 0;
        int anchorCenterX = screenW / 2;
        if (anchor != null && anchor.getWidth() > 0) {
            int[] loc = new int[2];
            anchor.getLocationOnScreen(loc);
            anchorCenterX = loc[0] + anchor.getWidth() / 2;
            anchorTop = loc[1];
        } else {
            int[] loc = new int[2];
            decor.getLocationOnScreen(loc);
            anchorTop = loc[1];
        }
        int margin = Math.max(0, (screenW - width) / 2);
        int x = clamp(anchorCenterX - width / 2, 0, Math.max(0, screenW - width));
        // 表格还没排版时用屏幕边距兜底，别把胶囊甩到屏幕外
        if (width <= 0) {
            x = margin;
        }
        return new int[]{x, anchorTop + TOP_OFFSET_DP};
    }

    private static int maxWidthPx(Context ctx, View anchor) {
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int base = anchor != null && anchor.getWidth() > 0 ? anchor.getWidth() : screenW;
        int limit = Math.min(base, screenW);
        return Math.max(dp(ctx, 80), Math.round(limit * MAX_WIDTH_RATIO));
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
