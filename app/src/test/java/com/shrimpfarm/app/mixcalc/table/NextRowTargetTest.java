package com.shrimpfarm.app.mixcalc.table;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 回归：「下一行 ▾」的目标列选择。
 *
 * <p>鸿蒙 `nextRowDown()`：同一列能进就保持同列；下一行该列进不去
 * （空行的料量/平均格不可编辑）时退到该行第一个可编辑格，而不是原地不动。
 * 一个可编辑格都没有才返回 -1。</p>
 */
public class NextRowTargetTest {

    @Test
    public void keepsSameColumnWhenItIsEnabled() {
        // 同列能进就保持同列，哪怕它前面还有可编辑列
        assertEquals(2, NextRowTarget.choose(2, new boolean[]{true, true, true}));
        assertEquals(4, NextRowTarget.choose(4, new boolean[]{true, false, true, false, true}));
    }

    @Test
    public void fallsBackToFirstEnabledColumnInColumnOrder() {
        // 同列不可编辑：按列序取第一个可编辑格（通常是棚号列）
        assertEquals(0, NextRowTarget.choose(3, new boolean[]{true, false, false, false}));
        assertEquals(1, NextRowTarget.choose(3, new boolean[]{false, true, false, false}));
    }

    @Test
    public void returnsMinusOneWhenNothingIsEnabled() {
        assertEquals(-1, NextRowTarget.choose(1, new boolean[]{false, false, false}));
        assertEquals(-1, NextRowTarget.choose(1, new boolean[0]));
        assertEquals(-1, NextRowTarget.choose(1, null));
    }

    @Test
    public void toleratesIndexOutOfRange() {
        // 当前列号非法（列被隐藏后重算过配置）也要能落到第一个可编辑格
        assertEquals(0, NextRowTarget.choose(-1, new boolean[]{true, false}));
        assertEquals(1, NextRowTarget.choose(9, new boolean[]{false, true}));
    }
}
