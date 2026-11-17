package com.shrimpfarm.app.mixcalc.table;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 回归：把正在编辑的格子滚进可视区（对齐鸿蒙 ensureEditorVisible）。
 * 只滚最小距离，已经看得见就不动，结果永远夹在滚动范围内。
 */
public class RevealMathTest {

    private static final int ROW_H = 40;
    private static final int BODY_H = 400;

    @Test
    public void keepsScrollWhenRowAlreadyVisible() {
        assertEquals(120, RevealMath.scrollY(3, ROW_H, BODY_H, 120, 2000));
        // 行底正好贴着可视区底边也算看得见
        assertEquals(0, RevealMath.scrollY(9, ROW_H, BODY_H, 0, 2000));
    }

    @Test
    public void scrollsUpJustEnoughForRowAbove() {
        assertEquals(0, RevealMath.scrollY(0, ROW_H, BODY_H, 120, 2000));
        assertEquals(80, RevealMath.scrollY(2, ROW_H, BODY_H, 120, 2000));
    }

    @Test
    public void scrollsDownJustEnoughForRowBelow() {
        // 第 12 行底 520、可视区 0..400 → 滚到 120 正好露出来
        assertEquals(120, RevealMath.scrollY(12, ROW_H, BODY_H, 0, 2000));
        // 已经滚到 100：可视区 100..500，第 14 行底 600 → 滚到 200
        assertEquals(200, RevealMath.scrollY(14, ROW_H, BODY_H, 100, 2000));
    }

    @Test
    public void clampsVerticalScroll() {
        assertEquals(100, RevealMath.scrollY(12, ROW_H, BODY_H, 0, 100));
        assertEquals(0, RevealMath.scrollY(1, ROW_H, BODY_H, 0, 100));
    }

    @Test
    public void ignoresDegenerateMetrics() {
        assertEquals(55, RevealMath.scrollY(3, 0, BODY_H, 55, 2000));
        assertEquals(55, RevealMath.scrollY(3, ROW_H, 0, 55, 2000));
    }

    @Test
    public void keepsScrollWhenColumnVisible() {
        assertEquals(100, RevealMath.scrollX(300, 60, 40, 360, 100, 2000, false));
    }

    @Test
    public void scrollsRightToRevealColumnPastEdge() {
        assertEquals(200, RevealMath.scrollX(500, 60, 40, 360, 0, 2000, false));
        assertEquals(240, RevealMath.scrollX(540, 60, 40, 360, 40, 2000, false));
    }

    @Test
    public void scrollsLeftWhenColumnHidesUnderFrozenArea() {
        // 基准左 100、滚了 120 → 屏上 -20，冻结列占 0..40 → 往左滚 60 让左边界贴到 40
        assertEquals(60, RevealMath.scrollX(100, 60, 40, 360, 120, 2000, false));
    }

    @Test
    public void clampsHorizontalScroll() {
        assertEquals(0, RevealMath.scrollX(20, 60, 40, 360, 10, 2000, false));
        assertEquals(150, RevealMath.scrollX(500, 60, 40, 360, 0, 150, false));
    }

    @Test
    public void ignoresFixedAndDegenerateWidths() {
        assertEquals(120, RevealMath.scrollX(100, 60, 40, 360, 120, 2000, true));
        assertEquals(120, RevealMath.scrollX(100, 0, 40, 360, 120, 2000, false));
        assertEquals(120, RevealMath.scrollX(100, 60, 40, 0, 120, 2000, false));
    }
}
