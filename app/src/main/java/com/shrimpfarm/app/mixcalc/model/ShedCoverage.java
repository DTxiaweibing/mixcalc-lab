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

    /**
     * 按“实际归属”校正每行棚号文本。
     *
     * <p>一行文本里的号码被后面的行占走后，这行的有效棚数就变小了，但文本还写着原来的
     * 范围——看到的号码和参与算料的号码对不上。比如第 1 行写 {@code 1-40}、第 2 行写
     * {@code 1-15}，第 1 行只剩 16-40，文本就该显示 {@code 16-40}。</p>
     *
     * <p>已有号码一个没被别人占走的行原样返回（保留用户自己的写法，如
     * {@code 24-40 1-20} 不会被重排）；棚数为 0 的行也原样返回——它马上要被整行
     * 回收，清成空白会留下一个中间空洞。</p>
     *
     * @return 与入参等长的文本列表
     */
    public static List<String> renormalize(List<String> texts) {
        List<String> out = new ArrayList<>();
        if (texts == null) {
            return out;
        }
        List<List<Integer>> owned = ownedSets(texts);
        for (int i = 0; i < texts.size(); i++) {
            String original = texts.get(i) == null ? "" : texts.get(i).trim();
            List<Integer> mine = owned.get(i);
            // owned 一定是自己文本的子集，数量不等就说明有号被后面的行占走了
            if (!mine.isEmpty() && mine.size() != parseShedNumbers(original).size()) {
                out.add(ShedText.normalize(mine));
            } else {
                out.add(original);
            }
        }
        return out;
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
