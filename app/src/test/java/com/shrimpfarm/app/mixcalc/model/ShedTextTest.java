package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/**
 * 棚号文本操作。
 *
 * 转移（transfer）：按号码集合换主人，两边文本都按转移后的实际号码重排
 * （升序、连号合成区间、空格分隔），没有任何"落在范围内就跳过"的暗规则。
 * 拿走号码（remove）：单号直接删；号在连续段里必须拆开，否则原行还在算着这个棚。
 */
public class ShedTextTest {

    @Test
    public void normalizeSortsAndMerges() {
        assertEquals("1-3 5", ShedText.normalize(Arrays.asList(5, 1, 3, 2)));
        assertEquals("20 25 36-38", ShedText.normalize(Arrays.asList(36, 37, 38, 25, 20)));
        // 只剩两个连号时不收成区间，写成 39 40 更好认
        assertEquals("39 40", ShedText.normalize(Arrays.asList(40, 39)));
        assertEquals("", ShedText.normalize(Collections.<Integer>emptyList()));
        assertEquals("", ShedText.normalize(null));
    }

    @Test
    public void wholeRowToBlankTargetIsPointless() {
        // 第 3 行整行（26-35）转给空行：只是把整行挪下去，会闪屏，直接不让转
        assertEquals(true, ShedText.pointlessWholeRowMove("", Arrays.asList(26, 27, 28),
                Arrays.asList(26, 27, 28)));
        assertEquals(true, ShedText.pointlessWholeRowMove(null, Arrays.asList(26, 27),
                Arrays.asList(26, 27, 30)));
        // 目标行不是空的：真合并，放行
        assertEquals(false, ShedText.pointlessWholeRowMove("41-60", Arrays.asList(26, 27),
                Arrays.asList(26, 27)));
        // 只转走一部分：来源行不会空，放行
        assertEquals(false, ShedText.pointlessWholeRowMove("", Arrays.asList(26, 27, 28),
                Arrays.asList(26, 27)));
    }

    @Test
    public void transferMovesNumbersBothSides() {
        // 第 1 行 1-40 里的 5 转给空行：来源拆成 1-4 6-40，目标得到 5
        String[] moved = ShedText.transfer("1-40", "", Collections.singletonList(5));
        assertEquals("1-4 6-40", moved[0]);
        assertEquals("5", moved[1]);
    }

    @Test
    public void transferIsNeverANoOpWhenTargetSpanCovers() {
        // 曾经的 bug：目标行 20-30，转来的 25 落在 20~30 之间就被跳过，转移成了空操作。
        // 现在按号码集合换主人：25 本来就归目标行（跨度≠实际号码），来源文本也随之重排，
        // 第 1 行不再挂着自己并不拥有的 20-30。
        String[] moved = ShedText.transfer("1-40", "20-30", Collections.singletonList(25));
        assertEquals("1-24 26-40", moved[0]);
        assertEquals("20-30", moved[1]);

        int[] counts = ShedCoverage.ownedCounts(Arrays.asList(moved[0], moved[1]));
        assertEquals(29, counts[0]);
        assertEquals(11, counts[1]);
    }

    @Test
    public void transferUpFromRange() {
        // 往上转同样一步到位：来源行 20-30 失去 25 → 拆成 20-24 26-30
        String[] moved = ShedText.transfer("20-30", "1-40", Collections.singletonList(25));
        assertEquals("20-24 26-30", moved[0]);
        assertEquals("1-40", moved[1]);
    }

    @Test
    public void transferNumbersNotInSourceStillLandOnTarget() {
        // 待转池里的号按实际记录走：就算来源文本里已经没有（理论上不该发生），也落到目标行
        String[] moved = ShedText.transfer("1-10", "", Arrays.asList(11, 3));
        assertEquals("1 2 4-10", moved[0]);
        assertEquals("3 11", moved[1]);
    }

    @Test
    public void removeDeletesStandaloneNumber() {
        // 3 是单号 → 删掉，8-40 这段不受影响
        assertEquals("1 5 7 8-40", ShedText.remove("1 3 5 7 8-40", Collections.singletonList(3)));
        assertEquals("8-40", ShedText.remove("1 3 5 7 8-40", Arrays.asList(1, 3, 5, 7)));
    }

    @Test
    public void removeSplitsRangeWhenNumberInside() {
        // 号在连续段里：必须拆开，否则原行还算着这个棚
        assertEquals("1 3 5 7 8-19 21-40",
                ShedText.remove("1 3 5 7 8-40", Collections.singletonList(20)));
        assertEquals("1-4 6-40", ShedText.remove("1-40", Collections.singletonList(5)));
    }

    @Test
    public void removeSplitsRangeAtEdges() {
        assertEquals("2-39", ShedText.remove("1-40", Arrays.asList(1, 40)));
        assertEquals("2-40", ShedText.remove("1-40", Collections.singletonList(1)));
        assertEquals("1-39", ShedText.remove("1-40", Collections.singletonList(40)));
    }

    @Test
    public void removeSplitsRangeInMiddle() {
        assertEquals("10-14 17-20", ShedText.remove("10-20", Arrays.asList(15, 16)));
        // 只剩两个连号时不收成区间，写成 11 12 更好认
        assertEquals("11 12", ShedText.remove("10-13", Arrays.asList(10, 13)));
    }

    @Test
    public void removeWholeRangeLeavesEmptyText() {
        assertEquals("", ShedText.remove("1-40", ShedCoverage.parseShedNumbers("1-40")));
    }

    @Test
    public void removeMixedParts() {
        assertEquals("1-4 6-10 30-40", ShedText.remove("1-10、25、30-40", Arrays.asList(5, 25)));
    }

    @Test
    public void removeKeepsTextWhenNothingRemoved() {
        // 一个都没删到：连分隔符和空格都原样保留
        assertEquals("20-30，5 9", ShedText.remove("20-30，5 9", Collections.singletonList(88)));
        assertEquals("20-30 7", ShedText.remove("20-30、5、7", Collections.singletonList(5)));
    }

    @Test
    public void removeHandlesMixedSeparators() {
        assertEquals("5 9", ShedText.remove("5,7 9", Collections.singletonList(7)));
    }

    @Test
    public void removeHandlesChineseRangeChars() {
        assertEquals("1-4 6-10", ShedText.remove("1到10", Collections.singletonList(5)));
        assertEquals("1-4 6-10", ShedText.remove("1～10", Collections.singletonList(5)));
    }
}
