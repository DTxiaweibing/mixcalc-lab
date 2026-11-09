package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * 回归：空行自动回收（行级销毁）。
 *
 * <p>规则：结算时刻，"有效棚数为 0 但有内容（填过棚号或数据）"的行整行移除，下面的行上移；
 * 纯空行（录入容量）绝不能删。</p>
 *
 * <p>有效棚数一律按<b>申明文本</b>重新算（后行优先），不看 FeedData 里的缓存值——缓存可能
 * 还没刷新，用旧值会出现"几行同时被覆盖，这一轮只认出一行，下次操作才删下一行"，
 * 用户看到的就是"一行一行往上进"。{@link #threeCoveredRowsRemovedInOnePass()} 锁住这一点。</p>
 */
public class RowSettlementRecycleTest {

    private static FeedData row(String shedNumber, int shedCount, double average) {
        FeedData d = new FeedData();
        d.setShedNumber(shedNumber);
        d.setShedCountDirectly(shedCount, false);
        d.setAverage(average);
        return d;
    }

    @Test
    public void removesDeadRowAndShiftsBelow() {
        // 第 2 行的 1-40 被第 4 行整个压掉：结算时第 2 行回收，第 3、4 行上移
        List<FeedData> rows = new ArrayList<>();
        rows.add(row("41-60", 20, 10));
        rows.add(row("1-40", 0, 0));   // 死行：申明还在，号全被下面那行占走
        rows.add(row("", 0, 0));       // 纯空行：保留
        rows.add(row("1-40", 40, 5));

        List<Integer> removed = RowSettlement.recycleDeadRows(rows);

        assertEquals(1, removed.size());
        assertEquals(1, removed.get(0).intValue());
        assertEquals(3, rows.size());
        assertEquals("41-60", rows.get(0).getShedNumber());
        assertEquals("", rows.get(1).getShedNumber());
        assertEquals("1-40", rows.get(2).getShedNumber());
    }

    @Test
    public void keepsAliveRowsAndPureEmptyRows() {
        List<FeedData> rows = new ArrayList<>();
        rows.add(row("1-40", 40, 5));  // 活行
        rows.add(row("", 0, 0));       // 纯空行：没填过任何东西，是录入容量
        rows.add(row("", 0, 12));      // 只有数据没棚号的脏行：也是死行
        rows.add(row("41-60", 20, 0));

        List<Integer> removed = RowSettlement.recycleDeadRows(rows);

        assertEquals(1, removed.size());
        assertEquals(2, removed.get(0).intValue());
        assertEquals(3, rows.size());
        assertEquals("1-40", rows.get(0).getShedNumber());
        assertEquals("", rows.get(1).getShedNumber());
        assertEquals("41-60", rows.get(2).getShedNumber());
    }

    @Test
    public void multipleDeadRowsRemovedTogether() {
        // 两处覆盖各留下一个死行，一次全删
        List<FeedData> rows = new ArrayList<>();
        rows.add(row("1-10", 10, 5));
        rows.add(row("1-10", 10, 0));    // 死行：被下面同样写 1-10 的行压掉
        rows.add(row("20-30", 11, 5));
        rows.add(row("20-30", 11, 0));   // 死行
        rows.add(row("", 0, 0));         // 纯空行

        List<Integer> removed = RowSettlement.recycleDeadRows(rows);

        assertEquals(2, removed.size());
        assertEquals(3, rows.size());
        assertEquals("1-10", rows.get(0).getShedNumber());
        assertEquals("20-30", rows.get(1).getShedNumber());
        assertEquals("", rows.get(2).getShedNumber());
    }

    @Test
    public void threeCoveredRowsRemovedInOnePass() {
        // 核心回归：下面一行写了 1-40，把上面三行全盖掉。
        // 这三行的缓存棚数故意留着 40（模拟"失焦那一刻还没刷新"），
        // 结算必须按文本重算成 0，一次把三行全删——不能这次删一行、下次操作再删一行。
        List<FeedData> rows = new ArrayList<>();
        rows.add(row("1-40", 40, 5));
        rows.add(row("1-40", 40, 5));
        rows.add(row("1-40", 40, 5));
        rows.add(row("1-40", 40, 5));

        List<Integer> removed = RowSettlement.recycleDeadRows(rows);

        assertEquals(3, removed.size());
        assertEquals(1, rows.size());
        assertEquals("1-40", rows.get(0).getShedNumber());
    }

    @Test
    public void allEmptyListUntouched() {
        // 全空表（刚清空）：一行都不许删，否则表没了
        List<FeedData> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(row("", 0, 0));
        }
        assertEquals(0, RowSettlement.recycleDeadRows(rows).size());
        assertEquals(5, rows.size());
    }

    @Test
    public void rowEmptiedByDeletingAllShedsIsRecycledAndBelowShiftsUp() {
        // 用户在第 2 行气泡里把仅有的 3 个棚号全删了：这行既没棚号也没数据，
        // 但它是刚被掏空的，必须回收，不能让表中间留个洞
        List<FeedData> rows = new ArrayList<>();
        rows.add(row("41-60", 20, 10));
        rows.add(row("", 0, 0));        // 刚被删空的行
        rows.add(row("61-80", 20, 8));

        java.util.Set<Integer> emptied = new java.util.HashSet<>();
        emptied.add(1);
        List<Integer> removed = RowSettlement.recycleDeadRows(rows, emptied);

        assertEquals(1, removed.size());
        assertEquals(1, removed.get(0).intValue());
        assertEquals(2, rows.size());
        assertEquals("41-60", rows.get(0).getShedNumber());
        assertEquals("61-80", rows.get(1).getShedNumber());
    }

    @Test
    public void pureEmptyRowStaysWhenNothingWasEmptied() {
        // 反向保护：同一种“纯空行”，不是这次删空的就还是录入容量，不许回收
        List<FeedData> rows = new ArrayList<>();
        rows.add(row("41-60", 20, 10));
        rows.add(row("", 0, 0));
        rows.add(row("61-80", 20, 8));

        assertEquals(0, RowSettlement.recycleDeadRows(rows, java.util.Collections.emptySet()).size());
        assertEquals(3, rows.size());
    }

    @Test
    public void emptiedSetIgnoredWhenRowStillHasSheds() {
        // 防御：调用方把下标塞错了（这行其实还有棚数），不能真的删掉活行
        List<FeedData> rows = new ArrayList<>();
        rows.add(row("41-60", 20, 10));
        rows.add(row("1-3", 3, 5));

        java.util.Set<Integer> emptied = new java.util.HashSet<>();
        emptied.add(1);
        assertEquals(0, RowSettlement.recycleDeadRows(rows, emptied).size());
        assertEquals(2, rows.size());
    }
}
