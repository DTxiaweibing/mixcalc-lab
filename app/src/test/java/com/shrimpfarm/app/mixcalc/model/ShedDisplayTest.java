package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * 回归：棚号「两行极简显示」算法。
 *
 * 判定口径：每行 ≤ 9 字符，最多 2 行，任何 Range 不被拆分；
 * 无信息丢失时绝不出现省略号。用例与交付文档一致。
 */
public class ShedDisplayTest {

    private static void assertRender(String raw, String... expected) {
        List<String> lines = ShedDisplay.render(raw);
        assertEquals(expected.length, lines.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("第" + (i + 1) + "行", expected[i], lines.get(i));
        }
        for (String line : lines) {
            assertTrue("爆行: " + line, line.length() <= 9);
        }
    }

    @Test
    public void case1_pureScatterTwoLines() {
        // ①纯散点全显示 A-2
        assertRender("1 3 5 7 9 11 13", "1 3 5", "7 9 11 13");
    }

    @Test
    public void case2_twoLinesFull() {
        // ②两行全显示 A-2
        assertRender("1 3-15 17 19-32", "1 3-15", "17 19-32");
    }

    @Test
    public void case3_threeDigitsNoOverflow() {
        // ③三位数防爆 A-2
        assertRender("101-105 107 109", "101-105", "107 109");
    }

    @Test
    public void case4_rangeTruncate() {
        // ④Range截断 B-2：中间被折叠，首尾 Range 都露出
        assertRender("1 3-15 18 20 22 25-32", "1…3-15", "…25-32");
    }

    @Test
    public void case5_doubleRangeLimit() {
        // ⑤双Range极限 B-2（关键极限用例）
        assertRender("1 3 5 7-15 18 20 22 25-32 37 39 41", "1 3…7-15", "…25-32…");
    }

    @Test
    public void case6_doubleBigRangeWithScatter() {
        // ⑥双大Range+散点 B-2
        assertRender("1 3-15 18 20 22 24 26 28 30 100-199", "1…3-15", "…100-199");
    }

    @Test
    public void singleRangeOneLine() {
        assertRender("1-40", "1-40");
    }

    @Test
    public void emptyAndBlank() {
        assertEquals(0, ShedDisplay.render(null).size());
        assertEquals(0, ShedDisplay.render("   ").size());
    }

    @Test
    public void preprocessMergesAdjacent() {
        // 预处理：1、2、3 合并成 1-3，单行显示且不出现省略号
        assertRender("1 2 3", "1-3");
        assertRender("1-3 4", "1-4");
        // 各种分隔符都能解析
        assertRender("1、2、3", "1-3");
    }

    @Test
    public void scatterTruncateHasLeadingEllipsis() {
        // B-1 散点截断：第一行贪心、第二行尾部倒推带 "… "
        List<String> lines = ShedDisplay.render("2 4 6 8 10 12 14 16 18 20 22");
        assertEquals(2, lines.size());
        assertEquals("2 4 6 8", lines.get(0));
        assertEquals("… 20 22", lines.get(1));
    }

    @Test
    public void singleRangeNeverShowsTwice() {
        // 回归（截图 0.3.16）：只有一个区间时，B-2 原来把首尾 Range 都当成它，
        // 9-15 在两行各出现一次，看着像号码重复。现在区间只露一次。
        assertRender("1 3 5 7 9-15 17 19 21", "1 3 5 7…", "…9-15 17…");

        // 合并后的全文里，区间 token 只出现一次
        List<String> lines = ShedDisplay.render("1 3 5 7 9-15 17 19 21");
        int count = 0;
        for (String line : lines) {
            int idx = line.indexOf("9-15");
            while (idx >= 0) {
                count++;
                idx = line.indexOf("9-15", idx + 1);
            }
        }
        assertEquals(1, count);
    }

    @Test
    public void singleRangeAtFrontLeadsLineOne() {
        // 唯一区间且在最前：行1 区间带头，行2 接后面的散号
        assertRender("9-15 17 19 21 23 25", "9-15…", "…17 19…");
    }
}
