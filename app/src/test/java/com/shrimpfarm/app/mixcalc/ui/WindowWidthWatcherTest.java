package com.shrimpfarm.app.mixcalc.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 根布局宽度监听：只在「拿到有效新宽度」时通知重算一次。
 * 第一次布局只记基准，之后同一宽度重复布局不重复通知。
 */
public class WindowWidthWatcherTest {

    /** 建表时没拿到有效宽度（0）：先记下来，不重算。 */
    @Test
    public void unknownBasisDoesNotNotify() {
        assertFalse(WindowWidthWatcher.shouldNotify(0, 1080));
    }

    /** 首帧布局宽度跟建表时用的宽度不一样（分屏/自由窗口）：必须重算一次。 */
    @Test
    public void basisMismatchOnFirstLayoutNotifies() {
        assertTrue(WindowWidthWatcher.shouldNotify(2560, 1400));
    }

    /** 宽度没变（只是重画）不重算。 */
    @Test
    public void sameWidthDoesNotNotify() {
        assertFalse(WindowWidthWatcher.shouldNotify(1080, 1080));
    }

    /** 分屏 / 折叠展开 / 拖窗口：宽度真的变了才重算。 */
    @Test
    public void changedWidthNotifies() {
        assertTrue(WindowWidthWatcher.shouldNotify(1080, 1440));
        assertTrue(WindowWidthWatcher.shouldNotify(1440, 1080));
    }

    /** 布局还没定（宽 0 或负数）不重算，也不要把基准冲掉。 */
    @Test
    public void invalidWidthDoesNotNotify() {
        assertFalse(WindowWidthWatcher.shouldNotify(1080, 0));
        assertFalse(WindowWidthWatcher.shouldNotify(1080, -1));
    }

    /** 基准异常（负数）同样不重算，避免脏值触发一次无意义的重搭。 */
    @Test
    public void invalidBasisDoesNotNotify() {
        assertFalse(WindowWidthWatcher.shouldNotify(-5, 1080));
    }
}
