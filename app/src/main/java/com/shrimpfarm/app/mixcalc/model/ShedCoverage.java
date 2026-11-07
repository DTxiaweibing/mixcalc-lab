package com.shrimpfarm.app.mixcalc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 棚号解析 + 打点覆盖（与鸿蒙版 ShedCoverage.ets 同一套规则）。
 *
 * 1. 每行棚号展开成具体棚号，行内重复只算一次；
 * 2. 所有行按顺序铺开，后写的行覆盖先写的行；
 * 3. 每行实际棚数 = 铺完后仍归本行的棚号个数。
 */
public final class ShedCoverage {

    /** 棚号上限：超过的段整段不计入，由 findOverLimit 给提示。 */
    public static final int MAX_SHED_NUMBER = 1000;
    /** 区间最多展开多少个，防误输入撑爆内存。 */
    private static final int MAX_RANGE_SPAN = 10000;

    private ShedCoverage() {
    }

    private static List<String> splitParts(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return result;
        }
        text = text.replace('到', '-').replace('至', '-')
                .replace('~', '-').replace('～', '-')
                .replace('—', '-').replace('－', '-');
        text = text.replaceAll("\\s*-\\s*", "-");
        String[] parts = text.split("[,，、;；/\\s]+");
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) {
                result.add(t);
            }
        }
        return result;
    }

    private static List<Integer> numbersInPart(String part) {
        List<Integer> nums = new ArrayList<>();
        if (part.indexOf('-') >= 0) {
            String[] segs = part.split("-");
            for (String seg : segs) {
                if (nums.size() >= 2) {
                    break;
                }
                String digits = seg.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try {
                        nums.add(Integer.parseInt(digits));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return nums;
        }
        String digits = part.replaceAll("[^0-9]", "");
        if (!digits.isEmpty()) {
            try {
                nums.add(Integer.parseInt(digits));
            } catch (NumberFormatException ignored) {
            }
        }
        return nums;
    }

    /** 把一行棚号文本展开成去重、升序的棚号列表；超过上限的段整段忽略。 */
    public static List<Integer> parseShedNumbers(String raw) {
        TreeSet<Integer> seen = new TreeSet<>();
        for (String part : splitParts(raw)) {
            List<Integer> nums = numbersInPart(part);
            if (nums.isEmpty()) {
                continue;
            }
            if (nums.size() == 1) {
                int n = nums.get(0);
                if (n > 0 && n <= MAX_SHED_NUMBER) {
                    seen.add(n);
                }
                continue;
            }
            int start = nums.get(0);
            int end = nums.get(1);
            if (start > end) {
                int t = start;
                start = end;
                end = t;
            }
            if (start <= 0) {
                start = 1;
            }
            if (start > MAX_SHED_NUMBER || end > MAX_SHED_NUMBER) {
                continue;
            }
            if (end - start > MAX_RANGE_SPAN) {
                continue;
            }
            for (int n = start; n <= end; n++) {
                seen.add(n);
            }
        }
        return new ArrayList<>(seen);
    }

    /** 输入里第一个超过上限的棚号，没有返回 0。 */
    public static int findOverLimit(String raw) {
        for (String part : splitParts(raw)) {
            for (int n : numbersInPart(part)) {
                if (n > MAX_SHED_NUMBER) {
                    return n;
                }
            }
        }
        return 0;
    }

    /** 打点覆盖：返回每行实际占有的棚号（升序）。 */
    public static List<List<Integer>> ownedSets(List<String> texts) {
        List<List<Integer>> sets = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            sets.add(new ArrayList<Integer>());
        }
        Map<Integer, Integer> owner = new HashMap<>();
        for (int i = 0; i < texts.size(); i++) {
            for (int n : parseShedNumbers(texts.get(i))) {
                owner.put(n, i);
            }
        }
        for (Map.Entry<Integer, Integer> e : owner.entrySet()) {
            sets.get(e.getValue()).add(e.getKey());
        }
        for (List<Integer> s : sets) {
            Collections.sort(s);
        }
        return sets;
    }

    /** 打点覆盖：返回每行实际棚数。 */
    public static int[] ownedCounts(List<String> texts) {
        List<List<Integer>> sets = ownedSets(texts);
        int[] counts = new int[sets.size()];
        for (int i = 0; i < sets.size(); i++) {
            counts[i] = sets.get(i).size();
        }
        return counts;
    }
}
