package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 回归：重算派生值时不能把打点覆盖后的权威棚数改回“本行棚号自己数的个数”。
 *
 * 改之前 updateAllCalculations 里会调用 calculateShedCount()，重叠棚的行
 * （例如本行写 1-50，但 1-49 已被下面的行占走，实际只剩 1 个棚）每输入一格料
 * 都会把棚数瞬时改回 50，打点覆盖再改回 1，并按错误棚数把料量重算一遍，
 * 表现为手动填的料被自动改写。这里锁住棚数与平均的换算结果。
 */
public class FeedDataUpdateTest {

    @Test
    public void updateAllCalculationsKeepsCoveredShedCount() {
        FeedData data = new FeedData();
        data.setShedNumber("1-50");
        // 打点覆盖后的权威棚数：只有 50 号棚归本行
        data.setShedCountDirectly(1, false);
        data.setFeed05(5);

        data.updateAllCalculations(20, true, true);

        assertEquals(1, data.getShedCount());
        assertEquals(5, data.getAverage(true), 1e-9);
    }

    @Test
    public void manualFeedValueSurvivesRecalculation() {
        FeedData data = new FeedData();
        data.setShedNumber("1-40");
        data.setShedCountDirectly(8, false);
        data.setFeed05(100);

        data.updateAllCalculations(20, true, true);

        assertEquals(100, data.getFeed05(), 1e-9);
        assertEquals(8, data.getShedCount());
        assertEquals(12.5, data.getAverage(true), 1e-9);
    }
}
