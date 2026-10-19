package com.shrimpfarm.app;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 沙盒桩：去掉完整性校验（AppIntegrityChecker）、水印、底部导航、左右滑动切页、多语言。
 * 只保留 MixCalcActivity 实际调用到的对话框能力。
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("InflateParams")
    protected void showStyledConfirmDialog(String title, String message, String[] buttonTexts, int[] buttonColors, DialogInterface.OnClickListener[] listeners, boolean cancelable) {
        Dialog dialog = new Dialog(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_simple_confirm, null);
        dialog.setContentView(dialogView);

        dialog.setCanceledOnTouchOutside(false);
        if (!cancelable) dialog.setCancelable(false);
        dialog.setOnCancelListener(d -> {
            if (listeners != null && listeners.length > 0 && listeners[0] != null) {
                listeners[0].onClick(d, 0);
            }
        });

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        tvMessage.setText(message);

        LinearLayout buttonLayout = dialogView.findViewById(R.id.layout_buttons);
        for (int i = 0; i < buttonTexts.length; i++) {
            Button btn = new Button(this);
            btn.setText(buttonTexts[i]);
            btn.setTextSize(15);
            btn.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
            btn.setTextColor(buttonColors != null && i < buttonColors.length ? buttonColors[i] : 0xFF333333);
            btn.setBackgroundResource(android.R.color.transparent);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (i < buttonTexts.length - 1) {
                View divider = new View(this);
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

        dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }
}
