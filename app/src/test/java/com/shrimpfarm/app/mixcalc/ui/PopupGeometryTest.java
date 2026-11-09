package com.shrimpfarm.app.mixcalc.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 弹窗定位：表头右边缘对齐、棚号气泡左边缘对齐 + 屏幕内夹紧。 */
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

    @Test
    public void leftEdgeAlignsToCell() {
        // 棚数格左边 300 → 弹窗左边也是 300，不因为弹窗宽就往左挪
        assertEquals(300, PopupGeometry.leftAlignedX(300, 1080));
        assertEquals(0, PopupGeometry.leftAlignedX(0, 1080));
    }

    @Test
    public void leftAlignNeverLeavesWindow() {
        // 格子被横向滚出左边：夹回 0；滚出右边：夹到窗口内最后一个像素
        assertEquals(0, PopupGeometry.leftAlignedX(-120, 1080));
        assertEquals(1079, PopupGeometry.leftAlignedX(2000, 1080));
    }

    @Test
    public void leftAlignedWidthLeavesRightMargin() {
        // 左边 300、窗口 1080、右边留 8 → 最多 772
        assertEquals(772, PopupGeometry.leftAlignedWidth(800, 300, 1080, 8));
        // 本来就窄，不动
        assertEquals(300, PopupGeometry.leftAlignedWidth(300, 100, 1080, 8));
        // 右边只剩一点点：宽度跟着缩，至少 1
        assertEquals(2, PopupGeometry.leftAlignedWidth(800, 1070, 1080, 8));
    }
}
