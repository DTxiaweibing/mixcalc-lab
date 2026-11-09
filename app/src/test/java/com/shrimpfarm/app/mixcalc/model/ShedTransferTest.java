package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * 回归：转出 / 转入。
 *
 * 流程（与 MixCalcActivity.applyTransferIn 一致）：
 * <ol>
 * <li>{@link ShedText#transfer}——两边文本按"转移后的实际号码"重排重写；</li>
 * <li>打点覆盖（后行优先）算出各自实际拥有的号码与棚数；</li>
 * <li>重排：把参与行的文本重写成"实际拥有"的号码，幽灵号（文本里有、棚数里没有）清掉；</li>
 * <li>被转空的行整行回收。</li>
 * </ol>
 * 曾经的 bug：往下转不动来源行 + 目标行"范围内跳过"，导致转移空操作、来回转失败。
 */
public class ShedTransferTest {

    /** 模拟活动的转移流程：改文本 → 覆盖 → 参与行重写为实际拥有。返回最终文本列表。 */
    private static List<String> runTransfer(List<String> rows, int sourceRow, int targetRow,
                                            List<Integer> numbers) {
        String[] moved = ShedText.transfer(rows.get(sourceRow), rows.get(targetRow), numbers);
        rows.set(sourceRow, moved[0]);
        rows.set(targetRow, moved[1]);

        List<List<Integer>> owned = ShedCoverage.ownedSets(rows);
        for (int row : new int[]{sourceRow, targetRow}) {
            rows.set(row, ShedText.normalize(owned.get(row)));
        }
        return rows;
    }

    @Test
    public void transferMovesNumberInsideTargetSpanButNotInTargetSet() {
        // 真 bug 用例：37 落在第 2 行"最小~最大"（26~40）之间，但第 2 行实际没有这个号。
        // 旧逻辑按跨度判断"范围内就跳过"，整个转移成了空操作；现在必须真的搬过去
        List<String> rows = runTransfer(
                new ArrayList<>(Arrays.asList("36-40", "26-35 39-40")), 0, 1,
                Collections.singletonList(37));
        assertEquals("36 38", rows.get(0));
        assertEquals("26-35 37 39 40", rows.get(1));
        int[] counts = ShedCoverage.ownedCounts(rows);
        assertEquals(2, counts[0]);
        assertEquals(13, counts[1]);
    }

    @Test
    public void transferOfAlreadyOwnedNumberOnlyCleansSourceText() {
        // 25 本来就被第 2 行的 20-30 占着（棚数里第 1 行没有它）：
        // 转过去归属不变，但要按实际情况重排——第 1 行的文本不再挂着 20-30
        List<String> rows = runTransfer(
                new ArrayList<>(Arrays.asList("1-40", "20-30")), 0, 1,
                Collections.singletonList(25));
        assertEquals("1-19 31-40", rows.get(0));
        assertEquals("20-30", rows.get(1));
        int[] counts = ShedCoverage.ownedCounts(rows);
        assertEquals(29, counts[0]);
        assertEquals(11, counts[1]);
    }

    @Test
    public void phantomNumbersGetCleanedOnTransfer() {
        // 截图场景：第 1 行写着 1-25 36-38，但 36-38 实际被第 3 行占走（棚数只有 25）。
        // 把 36、37、38 往第 2 行转：文本层面换主人后，重排把幽灵号从两行文本里清掉
        List<String> rows = runTransfer(
                new ArrayList<>(Arrays.asList("1-25 36-38", "", "36-38")), 0, 1,
                Arrays.asList(36, 37, 38));
        assertEquals("1-25", rows.get(0));
        // 36-38 归第 3 行（后行优先），第 2 行实际拥有为空
        assertEquals("", rows.get(1));
        assertEquals("36-38", rows.get(2));
        int[] counts = ShedCoverage.ownedCounts(rows);
        assertEquals(25, counts[0]);
        assertEquals(0, counts[1]);
        assertEquals(3, counts[2]);
    }

    @Test
    public void transferBackAndForthWorks() {
        // 来回转：1→2 转走 5，再从 2→1 转回来，两边都得真的换主人
        List<String> rows = new ArrayList<>(Arrays.asList("1-40", ""));
        rows = runTransfer(rows, 0, 1, Collections.singletonList(5));
        assertEquals("1-4 6-40", rows.get(0));
        assertEquals("5", rows.get(1));

        rows = runTransfer(rows, 1, 0, Collections.singletonList(5));
        assertEquals("1-40", rows.get(0));
        assertEquals("", rows.get(1));
        int[] counts = ShedCoverage.ownedCounts(rows);
        assertEquals(40, counts[0]);
        assertEquals(0, counts[1]);
    }

    @Test
    public void transferUpReallyMoves() {
        // 往上转：25 从第 3 行回第 1 行，来源行文本真的松手
        List<String> rows = runTransfer(
                new ArrayList<>(Arrays.asList("1-40", "41-60", "25")), 2, 0,
                Collections.singletonList(25));
        assertEquals("1-40", rows.get(0));
        assertEquals("41-60", rows.get(1));
        assertEquals("", rows.get(2));
        int[] counts = ShedCoverage.ownedCounts(rows);
        assertEquals(40, counts[0]);
        assertEquals(20, counts[1]);
        assertEquals(0, counts[2]);
    }

    @Test
    public void transferBatchToEmptyRow() {
        List<String> rows = runTransfer(
                new ArrayList<>(Arrays.asList("1-40", "")), 0, 1,
                Arrays.asList(3, 4, 5));
        assertEquals("1 2 6-40", rows.get(0));
        assertEquals("3-5", rows.get(1));
        int[] counts = ShedCoverage.ownedCounts(rows);
        assertEquals(37, counts[0]);
        assertEquals(3, counts[1]);
    }

    @Test
    public void deleteStillSplitsRangeAndShrinksCount() {
        // 删除（棚卖了）保持原口径：号在连续段里拆开，棚数真的减少
        String after = ShedText.remove("1 3 5 7 8-40", Collections.singletonList(20));
        assertEquals("1 3 5 7 8-19 21-40", after);
        assertEquals(36, ShedCoverage.parseShedNumbers(after).size());
    }
}
