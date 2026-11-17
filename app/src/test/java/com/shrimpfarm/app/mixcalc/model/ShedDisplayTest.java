package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/**
 * 回归：棚号「两行极简显示」算法（与鸿蒙 ShedDisplay.ets 同一套规则）。
 *
 * <p>期望值全部取自鸿蒙模型层跑车的权威输出（`node scripts/run-model-tests.mjs`，
 * 557 断言全绿）：`render()` = 铺满档 9 字符预算，`renderForMode(raw, false)` = 滚动档 7 字符。</p>
 *
 * <p>判定口径：每行 ≤ 预算，最多 2 行，任何 Range 不被拆分；
 * 无信息丢失时绝不出现省略号；只有一个区间时区间只露一次。</p>
 */
public class ShedDisplayTest {

    /** 铺满档 9 字符预算。 */
    private static void assertFull(String raw, String... expected) {
        assertLines(ShedDisplay.render(raw), ShedDisplay.SHED_MAX_CHARS_FULL, raw, expected);
    }

    /** 滚动档 7 字符预算。 */
    private static void assertScroll(String raw, String... expected) {
        assertLines(ShedDisplay.renderForMode(raw, false), ShedDisplay.SHED_MAX_CHARS_SCROLL, raw, expected);
    }

    private static void assertLines(List<String> lines, int budget, String raw, String... expected) {
        assertEquals("行数不符: " + raw, expected.length, lines.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("第" + (i + 1) + "行 (" + raw + ")", expected[i], lines.get(i));
        }
        for (String line : lines) {
            assertTrue("爆行(" + budget + "): " + line, line.length() <= budget);
        }
    }

    // ===== 旧交付用例（铺满档 9 字符，A-2 改为「最接近对半」后期望值随之更新）=====

    @Test
    public void case1_pureScatterTwoLines() {
        // ①纯散点全显示 A-2：取 |首行 − 总长/2| 最小的切分
        assertFull("1 3 5 7 9 11 13", "1 3 5 7", "9 11 13");
    }

    @Test
    public void case2_twoLinesFull() {
        // ②两行全显示 A-2
        assertFull("1 3-15 17 19-32", "1 3-15", "17 19-32");
    }

    @Test
    public void case3_threeDigitsNoOverflow() {
        // ③三位数防爆 A-2
        assertFull("101-105 107 109", "101-105", "107 109");
    }

    @Test
    public void case4_rangeTruncate() {
        // ④Range 截断 B-2：首行在首个区间后补中间词，首尾 Range 都露出
        assertFull("1 3-15 18 20 22 25-32", "1…3-15 18", "…25-32");
    }

    @Test
    public void case5_doubleRangeLimit() {
        // ⑤双 Range 极限 B-2（关键极限用例）
        assertFull("1 3 5 7-15 18 20 22 25-32 37 39 41", "1 3…7-15", "…25-32…");
    }

    @Test
    public void case6_doubleBigRangeWithScatter() {
        // ⑥双大 Range + 散点 B-2
        assertFull("1 3-15 18 20 22 24 26 28 30 100-199", "1…3-15 18", "…100-199");
    }

    @Test
    public void singleRangeOneLine() {
        assertFull("1-40", "1-40");
        assertScroll("1-30", "1-30");
    }

    @Test
    public void emptyAndBlank() {
        assertEquals(0, ShedDisplay.render(null).size());
        assertEquals(0, ShedDisplay.render("   ").size());
        assertEquals(0, ShedDisplay.renderForMode(null, false).size());
        assertEquals(0, ShedDisplay.renderForMode("  ", true).size());
    }

    @Test
    public void preprocessMergesAdjacent() {
        // 预处理：1、2、3 合并成 1-3，单行显示且不出现省略号
        assertFull("1 2 3", "1-3");
        assertFull("1-3 4", "1-4");
        // 各种分隔符都能解析
        assertFull("1、2、3", "1-3");
        assertScroll("1,2,3", "1-3");
    }

    @Test
    public void scatterTruncateHasTightEllipsis() {
        // B-1 散点截断：第二行尾部倒推、省略号紧贴数字（MAX-1 预算，不留空格）
        assertFull("2 4 6 8 10 12 14 16 18 20 22", "2 4 6 8", "…18 20 22");
        assertScroll("2 4 6 8 10 12 14 16 18 20 22", "2 4 6 8", "…20 22");
    }

    @Test
    public void singleRangeNeverShowsTwice() {
        // 回归（截图 0.3.16）：只有一个区间时，区间在两行各出现一次看着像号码重复
        assertFull("1 3 5 7 9-15 17 19 21", "1 3 5 7…", "…9-15 17…");

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
        assertFull("9-15 17 19 21 23 25", "9-15…", "…17 19…");
    }

    // ===== 验收清单（滚动档 7 字符，与鸿蒙 shed-column-fit.test.mjs 逐组一致）=====

    @Test
    public void acceptance1_to5() {
        assertScroll("1 2 3 5", "1-3 5");
        assertScroll("1 2 3 5 7 9 11", "1-3 5", "7 9 11");
        assertScroll("1 2 3 5 7 8 9 10 11 12 13 14 15", "1-3 5", "7-15");
        assertScroll("1 2 3 5 7 8 9 10 11 12 13 14 15 17 19", "1-3 5", "7-15…");
        assertScroll("1 2 3 5 7 8 9 10 11 12 13 14 15 17 19 21 22 23 24 25 26 27 28 29 30",
                "1-3 5", "…21-30");
    }

    @Test
    public void acceptance6_to9() {
        assertScroll("1 2 3 5 7 8 9 10 11 12 13 14 15 17 19 21 22 23 24 25 26 27 28 29 30 32 34",
                "1-3 5", "…21-30…");
        assertScroll("1 3 5 7 9 11 13 15", "1 3 5 7", "…13 15");
        assertScroll(rangeOf(1, 30), "1-30");
        assertScroll("1 2 3 5 7 8 9", "1-3 5", "7-9");
    }

    @Test
    public void acceptance10_to12() {
        assertScroll("10 11 12 14 15 16", "10-12", "14-16");
        assertScroll("1 2 3 5 7 8 9 10 11 12 13 14 15 17 19 21 22", "1-3 5", "…21-22");
        assertScroll("60 61 62 64 65", "60-62", "64-65");
    }

    private static String rangeOf(int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            if (i > from) {
                sb.append(' ');
            }
            sb.append(i);
        }
        return sb.toString();
    }

    // ===== 模式 API 与不变量 =====

    @Test
    public void modeConstantsMatchHarmony() {
        assertEquals(9, ShedDisplay.RULE_MAX);
        assertEquals(9, ShedDisplay.SHED_MAX_CHARS_FULL);
        assertEquals(7, ShedDisplay.SHED_MAX_CHARS_SCROLL);
        // 兼容入口 = 铺满档
        assertEquals(ShedDisplay.render("1 3 5 7 9 11 13 15"),
                ShedDisplay.renderForMode("1 3 5 7 9 11 13 15", true));
    }

    @Test
    public void scrollBudgetExposesLessNotMore() {
        // 9 字符档确实比 7 字符档露出更多内容，不是同一套排版换个字号
        String raw = "1 3 5 7 9 11 13 15";
        assertLines(ShedDisplay.renderForMode(raw, true), ShedDisplay.SHED_MAX_CHARS_FULL, raw,
                "1 3 5 7 9", "11 13 15");
        assertLines(ShedDisplay.renderForMode(raw, false), ShedDisplay.SHED_MAX_CHARS_SCROLL, raw,
                "1 3 5 7", "…13 15");
    }

    @Test
    public void noOverflowAcrossBudgets() {
        String[] raws = {
                "1 2 3 5", "1 2 3 5 7 9 11", "1 3 5 7 9 11 13 15",
                "1 2 3 5 7 8 9 10 11 12 13 14 15 17 19",
                "1 3-15 18 20 22 25-32", "1 3 5 7-15 18 20 22 25-32 37 39 41",
                rangeOf(1, 99), "2 4 6 8 10 12 14 16 18 20 22",
                "9-15 17 19 21 23 25", "101-105 107 109", "60 61 62 64 65"
        };
        for (String raw : raws) {
            assertLinesWithin(raw, ShedDisplay.render(raw), ShedDisplay.SHED_MAX_CHARS_FULL);
            assertLinesWithin(raw, ShedDisplay.renderForMode(raw, false), ShedDisplay.SHED_MAX_CHARS_SCROLL);
        }
    }

    private static void assertLinesWithin(String raw, List<String> lines, int budget) {
        assertTrue("行数超 2: " + raw, lines.size() <= 2);
        assertFalse("未产出任何行: " + raw, lines.isEmpty());
        for (String line : lines) {
            assertFalse("空行: " + raw, line.isEmpty());
            assertTrue("爆行(" + budget + "): " + raw + " -> " + line, line.length() <= budget);
            // Range 不被拆分：短横杠不能落在行的开头或结尾
            assertFalse("区间被切断: " + line, line.startsWith("-") || line.endsWith("-"));
        }
    }

    @Test
    public void ellipsisOnlyWhenTruncated() {
        // A-1 / A-2：无信息丢失，绝不出现省略号
        for (String raw : new String[]{"1 2 3 5", "1-30", "101-105 107 109", "60 61 62 64 65"}) {
            for (String line : ShedDisplay.renderForMode(raw, false)) {
                assertFalse("完整显示不该有省略号: " + raw + " -> " + line, line.contains("…"));
            }
        }
        // 真截断时必须有省略号提示（至少一个折叠行带 …）
        List<String> truncated = ShedDisplay.renderForMode("1 3 5 7 9 11 13 15", false);
        assertTrue(String.join("", truncated), String.join("", truncated).contains("…"));
    }
}
