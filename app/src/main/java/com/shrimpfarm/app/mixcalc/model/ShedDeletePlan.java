package com.shrimpfarm.app.mixcalc.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * 删除前的"去向判定"：这一批号里，哪些还有来路可退，哪些删了就没了。
 *
 * <p>删除只动本行申明（见 {@link ShedText#remove}）。本行拥有的号如果在**上面某行**
 * 还申明着，本行剔掉之后它会被打点覆盖退回到那一行继续算料；如果上面没有任何行
 * 申明过它，那它就是彻底从表里消失，用户需要被告知具体是哪几个。</p>
 *
 * <p>只看本行上方的行：本行显示的号一定是本行申明里、且没被下面行占走的号，
 * 所以它不可能同时被下面的行申明着。</p>
 */
public final class ShedDeletePlan {

    private final List<Integer> fallback = new ArrayList<>();
    private final List<Integer> permanent = new ArrayList<>();

    private ShedDeletePlan() {
    }

    /**
     * @param declaredTexts 全表每行的申明原文（按行序）
     * @param row           正在删的是第几行（0 基）
     * @param targets       选中的号码
     */
    public static ShedDeletePlan of(List<String> declaredTexts, int row, Collection<Integer> targets) {
        ShedDeletePlan plan = new ShedDeletePlan();
        if (targets == null || targets.isEmpty()) {
            return plan;
        }
        for (Integer n : new TreeSet<>(targets)) {
            if (n == null || n <= 0) {
                continue;
            }
            if (hasSourceAbove(declaredTexts, row, n)) {
                plan.fallback.add(n);
            } else {
                plan.permanent.add(n);
            }
        }
        return plan;
    }

    private static boolean hasSourceAbove(List<String> declaredTexts, int row, int number) {
        if (declaredTexts == null) {
            return false;
        }
        int limit = Math.min(row, declaredTexts.size());
        for (int i = 0; i < limit; i++) {
            String text = declaredTexts.get(i);
            if (text == null || text.trim().isEmpty()) {
                continue;
            }
            if (ShedCoverage.parseShedNumbers(text).contains(number)) {
                return true;
            }
        }
        return false;
    }

    /** 会退回上面某行的号码（升序）。 */
    public List<Integer> getFallback() {
        return fallback;
    }

    /** 没有来路、删了就真没了的号码（升序）。 */
    public List<Integer> getPermanent() {
        return permanent;
    }

    public boolean hasFallback() {
        return !fallback.isEmpty();
    }

    public boolean hasPermanent() {
        return !permanent.isEmpty();
    }
}
