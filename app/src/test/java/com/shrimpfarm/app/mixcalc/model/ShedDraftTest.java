package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 边打字边全表重算：每个字符都从“进入编辑前的快照”重新推导，
 * 半成品区间（16-4）只影响当前这一帧，不能把别人的号永久改掉。
 */
public class ShedDraftTest {

    /** 逐字符敲入，返回每一步的实际结果（最后一帧即定稿）。 */
    private static List<ShedDraft.Result> typeCharByChar(List<String> snapshot, int row, String target) {
        List<ShedDraft.Result> frames = new ArrayList<>();
        for (int i = 1; i <= target.length(); i++) {
            frames.add(ShedDraft.recompute(snapshot, row, target.substring(0, i)));
        }
        return frames;
    }

    @Test
    public void typingWholeRangeKeepsOtherRowInSync() {
        List<String> snapshot = Arrays.asList("1-40", "");
        List<ShedDraft.Result> frames = typeCharByChar(snapshot, 1, "1-15");

        ShedDraft.Result last = frames.get(frames.size() - 1);
        assertEquals("16-40", last.getTexts().get(0));
        assertEquals("1-15", last.getTexts().get(1));
        assertEquals(25, last.getCounts()[0]);
        assertEquals(15, last.getCounts()[1]);
    }

    @Test
    public void firstCharAlreadyShowsRemainderOfAboveRow() {
        // 敲下第一个 "1" 的瞬间，下面一行的 1 就已经归它，上面一行当场显示 2-40
        ShedDraft.Result first = ShedDraft.recompute(Arrays.asList("1-40", ""), 1, "1");
        assertEquals("2-40", first.getTexts().get(0));
        assertEquals("1", first.getTexts().get(1));
        assertEquals(39, first.getCounts()[0]);
        assertEquals(1, first.getCounts()[1]);
    }

    @Test
    public void halfTypedRangeDoesNotPermanentlyStealNumbers() {
        // 回归：16-40 打到 "16-4" 会被解析成 4~16，当场抢走 4~15。
        // 从快照重推，输完之后必须完全恢复——之前"边改边累积"会把 4~15 永久改掉。
        List<String> snapshot = Arrays.asList("1-40", "");
        List<ShedDraft.Result> frames = typeCharByChar(snapshot, 1, "16-40");

        ShedDraft.Result half = ShedDraft.recompute(snapshot, 1, "16-4");
        assertEquals("1-3 17-40", half.getTexts().get(0));

        ShedDraft.Result last = frames.get(frames.size() - 1);
        assertEquals("1-15", last.getTexts().get(0));
        assertEquals("16-40", last.getTexts().get(1));
        assertEquals(15, last.getCounts()[0]);
        assertEquals(25, last.getCounts()[1]);
    }

    @Test
    public void editingRowAlwaysKeepsRawText() {
        // 编辑中的那行不被规范化：用户敲 "24-40 1-20" 就原样显示，只在计数上看实际归属
        ShedDraft.Result r = ShedDraft.recompute(Arrays.asList("1-40", ""), 1, "24-40 1-20");
        assertEquals("24-40 1-20", r.getTexts().get(1));
        // 1-20 和 24-40 归第二行（后行优先），第一行只剩 21-23
        assertEquals("21-23", r.getTexts().get(0));
        assertEquals(3, r.getCounts()[0]);
        assertEquals(37, r.getCounts()[1]);
    }

    @Test
    public void clearingTypedTextGivesNumbersBackToAboveRow() {
        List<ShedDraft.Result> frames = typeCharByChar(Arrays.asList("1-40", ""), 1, "1-15");
        ShedDraft.Result cleared = ShedDraft.recompute(Arrays.asList("1-40", ""), 1, "");
        assertEquals("1-40", cleared.getTexts().get(0));
        assertEquals("", cleared.getTexts().get(1));
        assertEquals(40, cleared.getCounts()[0]);
        assertEquals(0, cleared.getCounts()[1]);
        // 与上一帧的中间态无关：清空即完全回到基线
        assertEquals(25, frames.get(frames.size() - 1).getCounts()[0]);
    }
}
