package com.shrimpfarm.app.mixcalc.table;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 回归：「下一步 / 下一行」的点击节流。
 *
 * <p>与鸿蒙 `lastNextAt` 同值：两次导航间隔小于 120ms 的第二次直接忽略，
 * 防止输入法「下一步」键重复上报或用户连点把结算跑两遍。
 * 时钟回拨（now &lt; lastAt）按「不通过」处理，绝不能因此永远放行。</p>
 */
public class NextActionGateTest {

    @Test
    public void allowsFirstPress() {
        assertTrue(NextActionGate.allow(1000L, 0L));
    }

    @Test
    public void blocksPressesInsideInterval() {
        assertFalse(NextActionGate.allow(1000L, 1000L));
        assertFalse(NextActionGate.allow(1119L, 1000L));
        assertFalse(NextActionGate.allow(1150L, 1050L));
    }

    @Test
    public void allowsAtBoundaryAndAfter() {
        assertTrue(NextActionGate.allow(1120L, 1000L));
        assertTrue(NextActionGate.allow(5000L, 1000L));
    }

    @Test
    public void blocksWhenClockGoesBackwards() {
        // 时钟回拨不该被当成"已经过了很久"
        assertFalse(NextActionGate.allow(900L, 1000L));
    }

    @Test
    public void intervalMatchesHarmony() {
        // 鸿蒙 MixCalcTable.ets nextRowDown()/nextCell()：now - lastNextAt < 120
        assertTrue(NextActionGate.MIN_INTERVAL_MS == 120L);
    }
}
