package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 失焦结算：棚数为 0 的行按新行处理，棚号保留。 */
public class RowSettlementTest {

    private static FeedData zeroRowWithData() {
        FeedData data = new FeedData();
        data.setShedNumber("1-10");
        // 模拟被后面的行覆盖：棚号还在，实际棚数为 0
        data.setShedCountDirectly(0, false);
        data.setAverage(5);
        data.setFermentedFeed(1);
        data.setPowderFeed(2);
        data.setFeed03(3);
        data.setFeed05(4);
        data.setFeed10(5);
        data.setWater(6);
        data.setWeighedFeed(7);
        data.setSwitchRatio(0.5);
        data.setAverageManuallyModified(true);
        return data;
    }

    @Test
    public void clearsFeedAndDerivedButKeepsShedNumber() {
        FeedData data = zeroRowWithData();

        assertTrue(RowSettlement.settleZeroRows(new ArrayList<>(Arrays.asList(data))));

        assertEquals("1-10", data.getShedNumber());
        assertEquals(0, data.getShedCount());
        assertEquals(0, data.getAverage(), 0.0001);
        assertEquals(0, data.getFermentedFeed(), 0.0001);
        assertEquals(0, data.getPowderFeed(), 0.0001);
        assertEquals(0, data.getFeed03(), 0.0001);
        assertEquals(0, data.getFeed05(), 0.0001);
        assertEquals(0, data.getFeed10(), 0.0001);
        assertEquals(0, data.getWater(), 0.0001);
        assertEquals(0, data.getWeighedFeed(), 0.0001);
        assertEquals(0, data.getSwitchRatio(), 0.0001);
        assertFalse(data.isAverageManuallyModified());
    }

    @Test
    public void keepsRowsThatStillOwnSheds() {
        FeedData data = zeroRowWithData();
        data.setShedCountDirectly(10, false);
        List<FeedData> rows = new ArrayList<>(Arrays.asList(data));

        assertFalse(RowSettlement.settleZeroRows(rows));

        assertEquals(10, data.getShedCount());
        assertEquals(5, data.getAverage(), 0.0001);
        assertEquals(2, data.getPowderFeed(), 0.0001);
    }

    @Test
    public void ignoresEmptyRowsAndNulls() {
        List<FeedData> rows = new ArrayList<>();
        rows.add(null);
        rows.add(new FeedData());

        assertFalse(RowSettlement.settleZeroRows(rows));
        assertFalse(RowSettlement.settleZeroRows(null));
    }

    @Test
    public void settlesOnlyTheZeroRowsInAMixedList() {
        FeedData zero = zeroRowWithData();
        FeedData filled = zeroRowWithData();
        filled.setShedCountDirectly(10, false);
        List<FeedData> rows = new ArrayList<>(Arrays.asList(zero, filled));

        assertTrue(RowSettlement.settleZeroRows(rows));

        assertEquals(0, zero.getPowderFeed(), 0.0001);
        assertEquals(2, filled.getPowderFeed(), 0.0001);
    }
}
