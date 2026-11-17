package com.shrimpfarm.app.mixcalc.table;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 回归：表尾「重置」格钉在右边缘（对齐鸿蒙），横向滚动时不跟着跑。
 */
public class PinnedFooterTest {

    @Test
    public void pinsToRightEdge() {
        assertEquals(300, PinnedFooter.left(360, 60, 40));
        assertEquals(0, PinnedFooter.left(60, 60, 0));
    }

    @Test
    public void neverCoversFrozenColumns() {
        // 视口太窄时也压不到冻结列上
        assertEquals(40, PinnedFooter.left(80, 60, 40));
        assertEquals(40, PinnedFooter.left(50, 60, 40));
    }

    @Test
    public void hitsOnlyInsidePinnedCell() {
        assertTrue(PinnedFooter.hit(300, 300, 60));
        assertTrue(PinnedFooter.hit(359.5f, 300, 60));
        assertFalse(PinnedFooter.hit(360, 300, 60));
        assertFalse(PinnedFooter.hit(299.9f, 300, 60));
    }
}
