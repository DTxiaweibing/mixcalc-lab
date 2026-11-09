package com.shrimpfarm.app.mixcalc.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 棚号文本的增删与重排（转出 / 转入 / 删除用）。
 *
 * 分隔符认 , ， 、 ; ； / 和空白；区间认 - — － ~ ～ 至 到。
 *
 * <ul>
 *   <li>转移（{@link #transfer}）：按号码集合换主人，两边文本都按转移后的实际号码
 *       重排重写——没有任何"落在范围内就跳过"的暗规则，号码换没换主人由打点覆盖说了算；</li>
 *   <li>拿走号码（{@link #remove}，删除/卖出用）：单号直接删；号写在连续段里就把这段拆开
 *       （1-40 去掉 5 → 1-4 6-40），否则原行还在算着这个棚，等于没删。</li>
 * </ul>
 */
public final class ShedText {

    /** 拼接用的分隔符：用户习惯用空格分开，不写「、」。 */
    private static final String JOINER = " ";
    private static final String SPLIT_REGEX = "[,，、;；/\\s]+";
    /** 区间符号：出现任何一个就说明这一段是连续段。 */
    private static final String RANGE_CHARS = "-—－~～至到";
    /** 连号收成区间的阈值：连着 3 个及以上才写成 a-b。 */
    private static final int RANGE_MIN_SPAN = 3;

    private ShedText() {
    }

    /**
     * 转移一批号码：返回 [新来源文本, 新目标文本]。
     *
     * <p>两边都按"转移后的实际号码"重排：升序、连号合成区间、空格分隔。
     * 不做"目标行范围内就跳过"之类的暗规则——转完之后号码在谁的文本里，
     * 打点覆盖（后行优先）就把棚数判给谁。</p>
     */
    public static String[] transfer(String sourceText, String targetText, Collection<Integer> numbers) {
        Set<Integer> source = new TreeSet<>(ShedCoverage.parseShedNumbers(sourceText));
        Set<Integer> target = new TreeSet<>(ShedCoverage.parseShedNumbers(targetText));
        if (numbers != null) {
            for (Integer n : numbers) {
                if (n == null || n <= 0) {
                    continue;
                }
                source.remove(n);
                target.add(n);
            }
        }
        return new String[]{normalize(source), normalize(target)};
    }

    /** 按实际号码重排文本：升序、连着 3 个及以上收成 a-b、空格分隔；没有号码返回空串。 */
    public static String normalize(Collection<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return "";
        }
        return compress(numbers);
    }

    /**
     * 整行的号全部转给一个空行：等于只是把整行往下挪一格（来源行被回收、下面整体上移），
     * 屏幕闪一下却什么都没变——这种转移直接不让做。目标行不是空的（真合并）才放行。
     *
     * @param targetText  目标行棚号文本
     * @param sourceOwned 来源行实际拥有的号码
     * @param numbers     待转池里的号码
     */
    public static boolean pointlessWholeRowMove(String targetText,
                                                Collection<Integer> sourceOwned,
                                                Collection<Integer> numbers) {
        boolean targetBlank = targetText == null || targetText.trim().isEmpty();
        if (!targetBlank || sourceOwned == null || sourceOwned.isEmpty() || numbers == null) {
            return false;
        }
        return numbers.containsAll(sourceOwned);
    }

    /**
     * 拿走选中的棚号：单号删掉，连续段拆开只留剩下的部分。
     * <pre>
     *   1 3 5 7 8-40  去掉 3   → 1 5 7 8-40      （单号，直接删）
     *   1-40          去掉 5   → 1-4 6-40        （在区间里，拆开）
     *   10-20         去掉 15、16 → 10-14 17-20
     *   20-30 5       去掉 88  → 原样返回（一个都没删到，连格式都不动）
     * </pre>
     */
    public static String remove(String text, Collection<Integer> numbers) {
        String base = text == null ? "" : text.trim();
        if (base.isEmpty() || numbers == null || numbers.isEmpty()) {
            return base;
        }
        Set<Integer> drop = new HashSet<>(numbers);
        String[] parts = base.split(SPLIT_REGEX);
        StringBuilder sb = new StringBuilder();
        boolean removed = false;
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            Integer single = standaloneNumber(part);
            if (single != null && drop.contains(single)) {
                removed = true;
                continue;
            }
            if (single != null) {
                // 不受影响的单号：原样搬过去
                appendPart(sb, part);
                continue;
            }
            // 连续段：看这段里有没有要被拿走的号
            List<Integer> own = ShedCoverage.parseShedNumbers(part);
            boolean touched = false;
            for (int n : own) {
                if (drop.contains(n)) {
                    touched = true;
                    break;
                }
            }
            if (!touched) {
                appendPart(sb, part);
                continue;
            }
            removed = true;
            List<Integer> remain = new ArrayList<>();
            for (int n : own) {
                if (!drop.contains(n)) {
                    remain.add(n);
                }
            }
            if (!remain.isEmpty()) {
                appendPart(sb, compress(remain));
            }
        }
        return removed ? sb.toString() : base;
    }

    /** 这一段是不是单号；是就返回号码，连续段或空段返回 null。 */
    private static Integer standaloneNumber(String part) {
        for (int i = 0; i < part.length(); i++) {
            if (RANGE_CHARS.indexOf(part.charAt(i)) >= 0) {
                return null;
            }
        }
        List<Integer> nums = ShedCoverage.parseShedNumbers(part);
        return nums.size() == 1 ? nums.get(0) : null;
    }

    /** 一串号码压成文本：连着的 3 个以上写成 a-b，其余用空格列出来。 */
    private static String compress(Collection<Integer> numbers) {
        List<Integer> sorted = new ArrayList<>(new TreeSet<>(numbers));
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < sorted.size()) {
            int start = sorted.get(i);
            int end = start;
            while (i + 1 < sorted.size() && sorted.get(i + 1) == end + 1) {
                i++;
                end = sorted.get(i);
            }
            if (end - start + 1 >= RANGE_MIN_SPAN) {
                appendPart(sb, start + "-" + end);
            } else {
                for (int n = start; n <= end; n++) {
                    appendPart(sb, String.valueOf(n));
                }
            }
            i++;
        }
        return sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(JOINER);
        }
        sb.append(part);
    }
}
