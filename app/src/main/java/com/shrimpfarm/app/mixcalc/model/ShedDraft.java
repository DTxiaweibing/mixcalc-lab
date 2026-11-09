package com.shrimpfarm.app.mixcalc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 棚号“边打字边全表重算”的推导器。
 *
 * <p>做法：进入棚号编辑时先拍一张全表文本快照（基线），之后每敲一个字符都拿
 * <b>快照 + 当前这一格的输入</b> 重新推一遍，而不是在上一次的结果上继续改。</p>
 *
 * <p>为什么必须从快照重推：{@code 16-40} 打到一半是 {@code 16-4}，会被解析成 4~16，
 * 当场把下面几行的 4~15 抢走。若直接在当前文本上改写，输完 {@code 16-40} 也还不回来
 * （那些号已经被永久写掉了）；从快照重推则半成品只影响这一帧，输完立刻恢复。</p>
 *
 * <p>编辑中的那一行始终显示用户原样输入的文本，不在用户敲字时改他正在敲的字。</p>
 */
public final class ShedDraft {

    /** 一次推导的结果：每行应显示的文本 + 每行实际棚数（下标与快照一一对应）。 */
    public static final class Result {
        private final List<String> texts;
        private final int[] counts;

        Result(List<String> texts, int[] counts) {
            this.texts = texts;
            this.counts = counts;
        }

        public List<String> getTexts() {
            return texts;
        }

        public int[] getCounts() {
            return counts;
        }
    }

    private ShedDraft() {
    }

    /**
     * @param snapshot   进入编辑那一刻的全表文本（基线）
     * @param editingRow 正在编辑的行，-1 表示不是棚号编辑
     * @param typedText  该行当前输入框里的完整文本
     */
    public static Result recompute(List<String> snapshot, int editingRow, String typedText) {
        List<String> base = new ArrayList<>();
        if (snapshot != null) {
            base.addAll(snapshot);
        }
        String typed = typedText == null ? "" : typedText;
        boolean editing = editingRow >= 0 && editingRow < base.size();
        if (editing) {
            base.set(editingRow, typed);
        }

        List<List<Integer>> sets = ShedCoverage.ownedSets(base);
        List<String> texts = ShedCoverage.renormalize(base);
        if (editing) {
            texts.set(editingRow, typed);
        }

        int[] counts = new int[sets.size()];
        for (int i = 0; i < sets.size(); i++) {
            counts[i] = sets.get(i).size();
        }
        return new Result(texts, counts);
    }
}
