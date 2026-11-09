package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 声明 vs 显示：打点覆盖只改“显示文本”，不改用户填的原文。
 *
 * <p>这样后面那行改小/清空时，被占走的号会自己退回上面的行——而不是像以前那样
 * 第一次被占走就从原文里永久消失。</p>
 */
public class ShedFallbackTest {

    private static List<String> display(List<String> declared) {
        return ShedCoverage.renormalize(declared);
    }

    @Test
    public void lowerRowTakingNumbersOnlyHidesThemFromAboveRow() {
        List<String> declared = Arrays.asList("1-40", "1-15");
        List<String> shown = display(declared);
        assertEquals("16-40", shown.get(0));
        assertEquals("1-15", shown.get(1));
        // 原文一个字都没动
        assertEquals("1-40", declared.get(0));
    }

    @Test
    public void shrinkingLowerRowGivesNumbersBackAutomatically() {
        // 第 2 行从 1-15 改小成 1-5：6-15 没人认领，退回第 1 行
        List<String> shown = display(Arrays.asList("1-40", "1-5"));
        assertEquals("6-40", shown.get(0));
        assertEquals("1-5", shown.get(1));

        // 再清空第 2 行：整段 1-40 全退回
        List<String> back = display(Arrays.asList("1-40", ""));
        assertEquals("1-40", back.get(0));
        assertEquals("", back.get(1));
    }

    @Test
    public void upperRowKeepsOriginalFormattingWhenNothingIsTaken() {
        // 没人抢号时保留用户自己的写法，不重排
        List<String> shown = display(Arrays.asList("24-40 1-20", ""));
        assertEquals("24-40 1-20", shown.get(0));
    }

    @Test
    public void threeRowsFallBackInOrder() {
        List<String> declared = Arrays.asList("1-100", "20-30", "50");
        List<String> shown = display(declared);
        assertEquals("1-19 31-49 51-100", shown.get(0));
        assertEquals("20-30", shown.get(1));
        assertEquals("50", shown.get(2));

        // 第 3 行清空：50 退回第 1 行
        List<String> after = display(Arrays.asList("1-100", "20-30", ""));
        assertEquals("1-19 31-100", after.get(0));
    }

    @Test
    public void fullyCoveredRowKeepsItsDeclaration() {
        // 第 1 行被完全盖住（有效棚数 0）：申明保留，行交给结算回收；
        // 这时更不能把原文清掉，否则回收完格子会留一个空洞
        List<String> declared = Arrays.asList("1-40", "1-40");
        assertEquals("1-40", display(declared).get(0));
        assertEquals(0, ShedCoverage.ownedCounts(declared)[0]);
        assertEquals(40, ShedCoverage.ownedCounts(declared)[1]);
    }

    @Test
    public void renderTextIsIdempotent() {
        List<String> declared = new ArrayList<>(Arrays.asList("1-40", "1-15", "30"));
        List<String> once = display(declared);
        List<String> twice = display(once);
        assertEquals(once, twice);
    }

    @Test
    public void deleteOnlyTouchesThisRowSoNumbersFallBackUp() {
        // 「有地方退就退」：第 2 行把 1-15 删掉（卖出），只动第 2 行申明，
        // 这些号在第 1 行还申明着，于是整段退回第 1 行继续算料，不是凭空消失
        List<String> declared = new ArrayList<>(Arrays.asList("1-40", "1-15"));
        declared.set(1, ShedText.remove(declared.get(1), numbers(1, 15)));
        assertEquals("", declared.get(1));
        assertEquals("1-40", display(declared).get(0));
    }

    @Test
    public void deletingOneNumberReturnsJustThatNumberToUpperRow() {
        // 只删 5：5 退回第 1 行，其余 1-4 6-15 仍归第 2 行
        List<String> declared = new ArrayList<>(Arrays.asList("1-40", "1-15"));
        declared.set(1, ShedText.remove(declared.get(1), numbers(5, 5)));
        List<String> shown = display(declared);
        assertEquals("5 16-40", shown.get(0));
        assertEquals("1-4 6-15", shown.get(1));
    }

    @Test
    public void deletingNumberNobodyElseDeclaresReallyRemovesIt() {
        // 「没地方退就等于删除」：30 只有第 2 行有，删了就是全表没有这个棚
        List<String> declared = new ArrayList<>(Arrays.asList("1-20", "30"));
        declared.set(1, ShedText.remove(declared.get(1), numbers(30, 30)));
        assertEquals("", declared.get(1));
        assertEquals(0, ShedCoverage.ownedCounts(declared)[1]);
        // 第 1 行也没多出 30（它本来就没申明过）
        assertEquals("1-20", display(declared).get(0));
    }

    private static List<Integer> numbers(int from, int to) {
        List<Integer> list = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            list.add(i);
        }
        return list;
    }
}
