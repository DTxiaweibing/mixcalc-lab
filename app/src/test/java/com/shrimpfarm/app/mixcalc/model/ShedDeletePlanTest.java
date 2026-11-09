package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * 删除去向判定：哪些号上面还有来路（会退回），哪些删了就真没了。
 *
 * <p>删除只动本行申明，本行让出来的号如果上面某行还申明着，会被打点覆盖退回去继续算料；
 * 上面没人申明过的号则是彻底消失，确认框需要把它们点名。</p>
 */
public class ShedDeletePlanTest {

    @Test
    public void allNumbersHaveSourceAbove() {
        List<String> declared = Arrays.asList("1-40", "1-15");
        ShedDeletePlan plan = ShedDeletePlan.of(declared, 1,
                Arrays.asList(1, 2, 3, 15));

        assertTrue(plan.getPermanent().isEmpty());
        assertEquals(Arrays.asList(1, 2, 3, 15), plan.getFallback());
    }

    @Test
    public void topRowHasNoSourceSoEverythingIsPermanent() {
        List<String> declared = Arrays.asList("1-40", "1-15");
        ShedDeletePlan plan = ShedDeletePlan.of(declared, 0, Arrays.asList(5, 6));

        assertTrue(plan.getFallback().isEmpty());
        assertEquals(Arrays.asList(5, 6), plan.getPermanent());
    }

    @Test
    public void mixedSelectionSplitsIntoTwoBuckets() {
        // 第 2 行申明 "1-9 30"：5 在第 1 行的 1-20 里（有来路），30 上面没人填过（真删）
        List<String> declared = Arrays.asList("1-20", "1-9 30");
        ShedDeletePlan plan = ShedDeletePlan.of(declared, 1, Arrays.asList(30, 5));

        assertEquals(Arrays.asList(5), plan.getFallback());
        assertEquals(Arrays.asList(30), plan.getPermanent());
        assertTrue(plan.hasFallback());
        assertTrue(plan.hasPermanent());
    }

    @Test
    public void rangeInsideUpperRowCountsAsSource() {
        // 来路判定要看区间展开，不是字符串包含
        List<String> declared = Arrays.asList("1-40", "20-30");
        ShedDeletePlan plan = ShedDeletePlan.of(declared, 1, Arrays.asList(25));

        assertEquals(Arrays.asList(25), plan.getFallback());
    }

    @Test
    public void lowerRowDeclarationIsNotASource() {
        // 只在下面几行出现过的号，对本行来说没有来路（实际也不会出现在本行气泡里）
        List<String> declared = Arrays.asList("", "", "5");
        ShedDeletePlan plan = ShedDeletePlan.of(declared, 1, Arrays.asList(5));
        assertTrue(plan.hasPermanent());
        assertEquals(Arrays.asList(5), plan.getPermanent());
    }

    @Test
    public void emptyInputsAreSafe() {
        assertFalse(ShedDeletePlan.of(null, 0, Arrays.asList(1)).getFallback().iterator().hasNext());
        ShedDeletePlan plan = ShedDeletePlan.of(Arrays.asList("", ""), 1, Arrays.asList());
        assertTrue(plan.getFallback().isEmpty());
        assertTrue(plan.getPermanent().isEmpty());
    }
}
