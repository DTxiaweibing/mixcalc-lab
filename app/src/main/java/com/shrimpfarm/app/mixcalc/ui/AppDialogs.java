package com.shrimpfarm.app.mixcalc.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.shrimpfarm.app.R;

/**
 * 通用弹窗构件：70% 宽的圆角确认框、统一列表弹窗（圆角 + 分隔线 + 40dp 行）。
 *
 * <p>从 MixCalcActivity 抽出来，那文件已经超 500 行有效代码，按 R3 不得再变长。</p>
 */
public final class AppDialogs {

    private AppDialogs() {
    }

    /** 圆角确认框，宽度占屏幕 70%，最多三个按钮平分且带竖分隔线。 */
    public static void showConfirm(Activity activity, String title, String message,
                                   String[] buttonTexts, int[] buttonColors,
                                   DialogInterface.OnClickListener[] listeners) {
        Dialog dialog = new Dialog(activity);
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_simple_confirm, null);
        dialog.setContentView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        tvMessage.setText(message);

        LinearLayout buttonLayout = dialogView.findViewById(R.id.layout_buttons);
        for (int i = 0; i < buttonTexts.length; i++) {
            Button btn = new Button(activity);
            btn.setText(buttonTexts[i]);
            btn.setTextSize(15);
            btn.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            btn.setTextColor(buttonColors != null && i < buttonColors.length ? buttonColors[i] : 0xFF333333);
            btn.setBackgroundResource(android.R.color.transparent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i < buttonTexts.length - 1) {
                View divider = new View(activity);
                divider.setLayoutParams(new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
                divider.setBackgroundColor(0xFFE0E0E0);
                buttonLayout.addView(divider);
            }
            final int index = i;
            btn.setOnClickListener(v -> {
                if (listeners != null && index < listeners.length && listeners[index] != null) {
                    listeners[index].onClick(dialog, index);
                }
                dialog.dismiss();
            });
            buttonLayout.addView(btn, lp);
        }

        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout((int) (screenWidth * 0.7), ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    /**
     * 统一列表弹窗（圆角 + 分隔线 + 40dp 行），返回未显示的实例。
     *
     * <p>用 PopupWindow 而不是 Dialog：Dialog 窗口默认 gravity 是 CENTER，
     * 改成 NO_GRAVITY 还是会被系统重新居中，贴不稳格子。</p>
     */
    public static PopupWindow createListPopup(Activity activity, String title, String[] items,
                                              final DialogInterface.OnClickListener listener) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundResource(R.drawable.bg_dialog);

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        tvTitle.setTextColor(0xFF000000);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 12));
        container.addView(tvTitle);
        container.addView(divider(activity));

        final PopupWindow popup = new PopupWindow(container);
        popup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(10);

        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView itemTv = new TextView(activity);
            itemTv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 40)));
            itemTv.setText(items[i]);
            itemTv.setTextSize(15);
            itemTv.setTextColor(0xFF333333);
            itemTv.setGravity(Gravity.CENTER);
            itemTv.setClickable(true);
            itemTv.setFocusable(true);
            itemTv.setOnClickListener(v -> {
                popup.dismiss();
                if (listener != null) {
                    listener.onClick(null, index);
                }
            });
            container.addView(itemTv);
            if (i < items.length - 1) {
                container.addView(divider(activity));
            }
        }

        popup.setWidth(listWidth(activity));
        popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        return popup;
    }

    /** 列表弹窗宽度：水比 / 发酵料 / 列操作 / 批次子菜单共用，定宽才能算右边缘。 */
    public static int listWidth(Activity activity) {
        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        return Math.min(dm.widthPixels, dp(activity, 180));
    }

    /** 1px 灰色分隔线，列表弹窗和确认框按钮之间共用。 */
    public static View divider(Activity activity) {
        View divider = new View(activity);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFE0E0E0);
        return divider;
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
