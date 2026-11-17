package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

/** 棚号上限 99（与鸿蒙版一致）：超过上限的号码不计入，超限时要能给提示。 */
public class ShedLimitTest {

    @Test
    public void limitIsNinetyNine() {
        assertEquals(99, ShedCoverage.MAX_SHED_NUMBER);
        assertEquals(Collections.singletonList(99), ShedCoverage.parseShedNumbers("99"));
        assertTrue(ShedCoverage.parseShedNumbers("100").isEmpty());
    }

    @Test
    public void rangePastTheLimitIsDroppedAsAWhole() {
        assertTrue(ShedCoverage.parseShedNumbers("1-100").isEmpty());
        assertEquals(99, ShedCoverage.parseShedNumbers("1-99").size());
    }

    @Test
    public void overLimitReportsTheOffendingNumber() {
        assertEquals(0, ShedCoverage.findOverLimit("1-99"));
        assertEquals(100, ShedCoverage.findOverLimit("1-100"));
        assertEquals(120, ShedCoverage.findOverLimit("5, 120"));
    }
}
