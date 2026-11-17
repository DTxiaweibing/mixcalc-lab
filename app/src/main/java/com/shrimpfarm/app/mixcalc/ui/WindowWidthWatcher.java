package com.shrimpfarm.app.mixcalc.ui;

import android.view.View;

/**
 * 根布局宽度监听：等根布局真的拿到新宽度，才回调一次。
 *
 * <p>窗口尺寸/形态变化（分屏、折叠展开、拖窗口）时，{@code onConfigurationChanged} 里
 * 根布局还是旧宽度，那时候算列宽会把表压窄。挂在这里等布局完成再算。
 * 第一次布局只记下基准宽度，不回调。</p>
 */
public final class WindowWidthWatcher implements View.OnLayoutChangeListener {

    private final Runnable onWidthChanged;
    private int basisWidth;

    private WindowWidthWatcher(int basisWidth, Runnable onWidthChanged) {
        this.basisWidth = basisWidth;
        this.onWidthChanged = onWidthChanged;
    }

    /**
     * 挂到根布局上；根布局还没建好（null）就什么都不做。
     *
     * @param basisWidth 建表时用的那一次宽度：首帧真正布局出来如果跟它不一样
     *                   （分屏、折叠、自由窗口），说明那次算错了，立刻重算一次。
     */
    public static void attach(View root, int basisWidth, Runnable onWidthChanged) {
        if (root == null) {
            return;
        }
        root.addOnLayoutChangeListener(new WindowWidthWatcher(basisWidth, onWidthChanged));
    }

    /**
     * 这次布局要不要通知重算。
     *
     * <p>宽度 &le; 0 是布局还没定；基准 &le; 0 表示建表时也没拿到有效宽度，
     * 先记下来别乱算；宽度跟基准一样说明只是重画，不用重算。</p>
     */
    static boolean shouldNotify(int basisWidth, int width) {
        return width > 0 && basisWidth > 0 && width != basisWidth;
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
                               int oldLeft, int oldTop, int oldRight, int oldBottom) {
        int width = right - left;
        boolean notify = shouldNotify(basisWidth, width);
        basisWidth = width;
        if (notify) {
            onWidthChanged.run();
        }
    }
}
