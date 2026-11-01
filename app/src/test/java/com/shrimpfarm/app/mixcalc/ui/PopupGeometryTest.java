package com.shrimpfarm.app.mixcalc.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 表头弹窗定位：右边缘对齐 + 屏幕内夹紧。 */
public class PopupGeometryTest {

    @Test
    public void rightEdgeAlignsToCell() {
        assertEquals(320, PopupGeometry.rightAlignedX(500, 180, 1080));
    }

    @Test
    public void leftmostCellClampsToZero() {
        assertEquals(0, PopupGeometry.rightAlignedX(100, 180, 1080));
    }

    @Test
    public void rightmostCellStaysInsideScreen() {
        // 单元格右边缘 1070，弹窗宽 180 → 左边缘 890，右边缘正好 1070，仍在屏内
        assertEquals(890, PopupGeometry.rightAlignedX(1070, 180, 1080));
        // 单元格右边缘超出屏宽时夹到屏幕右边缘
        assertEquals(900, PopupGeometry.rightAlignedX(1200, 180, 1080));
    }

    @Test
    public void popupWiderThanScreenClampsToZero() {
        assertEquals(0, PopupGeometry.rightAlignedX(300, 1200, 1080));
    }

    @Test
    public void fullWidthFollowsWindow() {
        assertEquals(1080, PopupGeometry.fullWidth(1080));
        assertEquals(2100, PopupGeometry.fullWidth(2100));
        assertEquals(1, PopupGeometry.fullWidth(0));
    }
}
