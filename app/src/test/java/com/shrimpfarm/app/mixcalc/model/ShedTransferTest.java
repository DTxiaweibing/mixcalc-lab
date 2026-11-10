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
 * <p>流程与 MixCalcActivity.applyTransferIn 一致，按方向分开：</p>
 * <ul>
 *   <li>往下转（目标行在下面）→ {@link ShedText#giveDown}：来源行<b>原文不动</b>，
 *       只把号写进目标行；后行优先让来源行自然少算，但"这些号从我这儿出去"的记录留着；</li>
 *   <li>往上转（目标行在上面）→ {@link ShedText#transfer}：来源行在下面压着，必须真的松手。</li>
 * </ul>
 *
 * <p>曾经的 bug 有两个：往下转不动来源行 + 目标行"范围内跳过"导致转移空操作（0.3.8 修）；
 * 以及转入后把来源行裁成"实际拥有"，把来路抹掉，目标行一清空号就凭空消失（本次修）。</p>
 */
public class ShedTransferTest {

    /** 模拟活动的转移流程：按方向改两边文本（不做任何"裁成实际拥有"的重写）。 */
    private static List<String> runTransfer(List<String> rows, int sourceRow, int targetRow,
                                            List<Integer> numbers) {
        boolean downward = targetRow > sourceRow;
        String[] moved = downward
                ? ShedText.giveDown(rows.get(sourceRow), rows.get(targetRow), numbers)
                : ShedText.transfer(rows.get(sourceRow), rows.get(targetRow), numbers);
        rows.set(sourceRow, moved[0]);
        rows.set(targetRow, moved[1]);
        return rows;
    }

    @Test
    public void transferMovesNumberInsideTargetSpanButNotInTargetSet() {
        // 真 bug 用例：37 落在第 2 行"最小~最大"（26~40）之间，但第 2 行实际没有这个号。
        // 旧逻辑按跨度判断"范围内就跳过"，整个转移成了空操作；现在必须真的搬过去
        List<String> rows = runTransfer(
                new ArrayList<>(Arrays.asList("36-40", "26-35 39-40")), 0, 1,
                Collections.singletonList(37));
        assertEquals("36-40", rows.get(0));
        assertEquals("26-35 37 39 40", rows.get(1));
        int[] counts = ShedCoverage.ownedCounts(rows);
        assertEquals(2, counts[0]);
        assertEquals(13, counts[1]);
    }

    @Test
    public void transferOfAlreadyOwnedNumberKeepsSourceText() {
        // 25 本来就被第 2 行的 20-30 占着（棚数里第 1 行没有它）：往下转不裁来源行，
        // 第 1 行仍写着 1-40，只是算料时少 20-30 这 11 个
        List<String> rows = runTransfer(
                new ArrayList<>(Arrays.asList("1-40", "20-30")), 0, 1,
                Collections.singletonList(25));
        assertEquals("1-40", rows.get(0));
        assertEquals("20-30", rows.get(1));
        int[] counts = ShedCoverage.ownedCounts(rows);
        assertEquals(29, counts[0]);
        assertEquals(11, counts[1]);
    }

    @Test
    public void phantomNumbersStayInSourceTextButDoNotCount() {
        // 第 1 行写着 1-25 36-38，36-38 实际被第 3 行占走（棚数只有 25）。
        // 往下转 36-38 给第 2 行：文本照写，棚数仍归第 3 行（后行优先），第 2 行还是 0 棚
        List<String> rows = runTransfer(
                new ArrayList<>(Arrays.asList("1-25 36-38", "", "36-38")), 0, 1,
                Arrays.asList(36, 37, 38));
        assertEquals("1-25 36-38", rows.get(0));
        assertEquals("36-38", rows.get(1));
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
        assertEquals("1-40", rows.get(0));
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
        assertEquals("1-40", rows.get(0));
        assertEquals("3-5", rows.get(1));
        int[] counts = ShedCoverage.ownedCounts(rows);
        assertEquals(37, counts[0]);
        assertEquals(3, counts[1]);
    }

    @Test
    public void downTransferKeepsProvenanceSoNumbersCanComeBack() {
        // 来路回归：第 1 行 1-40、第 2 行 1-15，把 20-24 往下转给第 2 行。
        // 来源行必须原样保留 1-40 —— 一旦被裁成 16-19 25-40，第 2 行清空后
        // 20-24 在上方就没人申明了，只能当真删除，号码凭空消失。
        List<String> rows = new ArrayList<>(Arrays.asList("1-40", "1-15"));
        rows = runTransfer(rows, 0, 1, Arrays.asList(20, 21, 22, 23, 24));
        assertEquals("1-40", rows.get(0));
        assertEquals("1-15 20-24", rows.get(1));
        int[] afterTransfer = ShedCoverage.ownedCounts(rows);
        assertEquals(20, afterTransfer[0]);
        assertEquals(20, afterTransfer[1]);

        // 第 2 行棚号清空 → 20 个号全部退回第 1 行，棚数回到 40
        rows.set(1, "");
        int[] back = ShedCoverage.ownedCounts(rows);
        assertEquals(40, back[0]);
        assertEquals(0, back[1]);
    }

    @Test
    public void trimmingSourceTextLosesProvenance() {
        // 反面断言：把来源行裁成"实际拥有"（旧 rewriteToOwned 的做法）之后，
        // 来路就没了——目标行清空也回不来。锁住这个后果，防止裁剪被"优化"加回来。
        List<String> rows = new ArrayList<>(Arrays.asList("1-40", "1-15"));
        String[] moved = ShedText.transfer(rows.get(0), rows.get(1),
                Arrays.asList(20, 21, 22, 23, 24));
        rows.set(0, moved[0]);
        rows.set(1, moved[1]);

        List<List<Integer>> owned = ShedCoverage.ownedSets(rows);
        rows.set(0, ShedText.normalize(owned.get(0)));
        assertEquals("16-19 25-40", rows.get(0));   // 来源原文被裁掉了

        rows.set(1, "");
        int[] counts = ShedCoverage.ownedCounts(rows);
        assertEquals(20, counts[0]);                // 只剩 20 个，20-24 再也回不来
    }

    @Test
    public void deleteStillSplitsRangeAndShrinksCount() {
        // 删除（棚卖了）保持原口径：号在连续段里拆开，棚数真的减少
        String after = ShedText.remove("1 3 5 7 8-40", Collections.singletonList(20));
        assertEquals("1 3 5 7 8-19 21-40", after);
        assertEquals(36, ShedCoverage.parseShedNumbers(after).size());
    }
}
