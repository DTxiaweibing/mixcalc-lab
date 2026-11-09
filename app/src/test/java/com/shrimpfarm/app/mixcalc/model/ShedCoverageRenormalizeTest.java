package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * 棚号文本按“实际归属”校正。
 *
 * <p>规则：一行文本里的号码被后面的行占走后（这行棚数必然变小），文本重写成它真正
 * 拥有的号码，保证看到的号码 = 参与算料的号码。没被占走的行保留用户自己的写法。</p>
 */
public class ShedCoverageRenormalizeTest {

    @Test
    public void prefixTakenByRowBelow() {
        // 用户报的场景：第 1 行 1-40，第 2 行写 1-15 → 第 1 行要变成 16-40
        List<String> out = ShedCoverage.renormalize(Arrays.asList("1-40", "1-15"));
        assertEquals("16-40", out.get(0));
        assertEquals("1-15", out.get(1));

        // 校正后的文本和实际棚数一致
        int[] counts = ShedCoverage.ownedCounts(out);
        assertEquals(25, counts[0]);
        assertEquals(15, counts[1]);
    }

    @Test
    public void middleNumbersTakenSplitsRange() {
        // 中间被拿走：20-30 少了 25，只能显示成 20-24 26-30
        List<String> out = ShedCoverage.renormalize(Arrays.asList("20-30", "25"));
        assertEquals("20-24 26-30", out.get(0));
        assertEquals("25", out.get(1));
    }

    @Test
    public void scatteredTakesLeaveRanges() {
        List<String> out = ShedCoverage.renormalize(Arrays.asList("1-40", "1-15 20"));
        assertEquals("16-19 21-40", out.get(0));
        assertEquals("1-15 20", out.get(1));
    }

    @Test
    public void untouchedRowKeepsItsOwnWriting() {
        // 一个号都没被占走：格式原样保留，不重排成升序
        List<String> out = ShedCoverage.renormalize(
                Collections.singletonList("24-40 1-20"));
        assertEquals("24-40 1-20", out.get(0));

        List<String> messy = ShedCoverage.renormalize(
                Collections.singletonList("1 3 5 7 8-40"));
        assertEquals("1 3 5 7 8-40", messy.get(0));
    }

    @Test
    public void fullyCoveredRowKeepsText() {
        // 整行被后行盖住（有效棚数 0）：文本不动，交给结算时的整行回收
        List<String> out = ShedCoverage.renormalize(Arrays.asList("1-40", "1-40"));
        assertEquals("1-40", out.get(0));
        assertEquals("1-40", out.get(1));
    }

    @Test
    public void idempotent() {
        List<String> once = ShedCoverage.renormalize(Arrays.asList("1-40", "1-15"));
        List<String> twice = ShedCoverage.renormalize(once);
        assertEquals(once, twice);
    }

    @Test
    public void blankRowsStayBlank() {
        List<String> out = ShedCoverage.renormalize(Arrays.asList("", null));
        assertEquals("", out.get(0));
        assertEquals("", out.get(1));
    }

    @Test
    public void threeRowsHandOverInOrder() {
        // 三行叠加：后行优先，前面两行都要按实际归属重写
        List<String> out = ShedCoverage.renormalize(
                new ArrayList<>(Arrays.asList("1-40", "1-20", "1-5")));
        assertEquals("21-40", out.get(0));
        assertEquals("6-20", out.get(1));
        assertEquals("1-5", out.get(2));
    }
}
