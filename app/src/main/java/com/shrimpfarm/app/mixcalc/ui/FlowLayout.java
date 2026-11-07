package com.shrimpfarm.app.mixcalc.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * 简易流式布局：子 View 从左到右排，一行放不下自动换行。
 * 间距由子 View 的 margin 控制（棚号块用 right/bottom = 2dp）。
 */
public class FlowLayout extends ViewGroup {

    public FlowLayout(Context context) {
        super(context);
    }

    public FlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int parentWidth = Math.max(0, widthSize - getPaddingLeft() - getPaddingRight());
        int lineWidth = 0;
        int lineHeight = 0;
        int totalHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            if (lineWidth > 0 && lineWidth + cw > parentWidth) {
                totalHeight += lineHeight;
                lineWidth = 0;
                lineHeight = 0;
            }
            lineWidth += cw;
            lineHeight = Math.max(lineHeight, ch);
        }
        totalHeight += lineHeight;

        int height = totalHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(widthSize, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int parentWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth();
            int ch = child.getMeasuredHeight();
            int need = cw + lp.leftMargin + lp.rightMargin;
            if (x > getPaddingLeft() && x + need > getPaddingLeft() + parentWidth) {
                x = getPaddingLeft();
                y += lineHeight;
                lineHeight = 0;
            }
            int childLeft = x + lp.leftMargin;
            int childTop = y + lp.topMargin;
            child.layout(childLeft, childTop, childLeft + cw, childTop + ch);
            x += need;
            lineHeight = Math.max(lineHeight, ch + lp.topMargin + lp.bottomMargin);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams ? p : new MarginLayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }
}
