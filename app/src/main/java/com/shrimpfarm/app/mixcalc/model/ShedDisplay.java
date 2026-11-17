package com.shrimpfarm.app.mixcalc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 棚号输入框「两行极简显示」算法（展示态专用），与鸿蒙版 ShedDisplay.ets 同一套规则。
 *
 * <p>口径：数字、短横杠、各项之间的半角空格、省略号 … 各算 1 个字符；
 * 省略号可紧贴数字，起「分隔 + 折叠提示」双重作用。</p>
 *
 * <p>铁律：</p>
 * <ol>
 * <li>Range 绝对完整：绝不把任何连续区间（如 3-15）从中间切断；</li>
 * <li>单行硬上限：每行 ≤ 字符预算（铺满 9 / 滚动 7），最多 2 行，任何情况不得爆行；</li>
 * <li>省略号语义：仅当真发生截断时才出现，无信息丢失时绝不出现；</li>
 * <li>信息密度优先：必须截断时优先让首、尾两个关键 Range 都露出。</li>
 * </ol>
 *
 * <p>执行分支（命中即返回）：A-1 单行全显示 → A-2 两行全显示 →
 * B-1 散点截断（无 Range 时）→ B-2 双 Range 紧凑折叠截断。</p>
 *
 * <p>A-2 取所有可行切分里首行最接近总长一半的，平手偏短首行；
 * B-2 首行在首个区间后贪心补中间词，中间词全部露出时第二行不再加前导省略号；
 * B-1 省略号紧贴数字，省下的一字符多露一个号。</p>
 *
 * <p>编辑态不走这里：点进格子时编辑浮层加载的仍是完整原始字符串。</p>
 */
public final class ShedDisplay {

    /** 规则标称的单行上限（字符）：全列铺满时用这个预算。 */
    public static final int RULE_MAX = 9;
    /** 全列铺满（平板/宽窗口）每行字符预算。 */
    public static final int SHED_MAX_CHARS_FULL = RULE_MAX;
    /** 需要横向滚动的手机布局每行字符预算。 */
    public static final int SHED_MAX_CHARS_SCROLL = 7;
    /** 预算下限：极窄列也要给省略号留出位置。 */
    private static final int MIN_BUDGET = 3;

    private ShedDisplay() {
    }

    /**
     * 兼容入口：标称 9 字符预算（模型层回归测试与旧调用方走这里）。
     *
     * @return 非空行的列表（1 或 2 个元素）；原文为空时返回空列表。
     */
    public static List<String> render(String rawText) {
        return renderWithBudget(rawText, RULE_MAX);
    }

    /**
     * 按显示模式折叠：铺满 9 字符、滚动 7 字符。
     * 字号必须由调用方按同一个判据选，否则会出现「按 9 字符排版却用 14dp 画」。
     */
    public static List<String> renderForMode(String rawText, boolean allColumnsVisible) {
        return renderWithBudget(rawText,
                allColumnsVisible ? SHED_MAX_CHARS_FULL : SHED_MAX_CHARS_SCROLL);
    }

    private static List<String> renderWithBudget(String rawText, int charBudget) {
        final int max = Math.max(MIN_BUDGET, charBudget);
        List<String> lines = new ArrayList<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            return lines;
        }
        List<String> items = preprocess(rawText);
        if (items.isEmpty()) {
            // 完全解析不出号码（比如只有乱码）：原文单行兜底，别画空白
            lines.add(rawText.trim());
            return lines;
        }

        int n = items.size();
        int total = disp(items);

        // A-1 单行全显示
        if (total <= max) {
            lines.add(join(items));
            return lines;
        }

        // A-2 两行全显示（无省略号）：在所有可行切分里挑首行长度最接近总长一半的，
        // 平手时保留更靠前的切分（首行更短），避免把 1-3 5 / 7 9 11 挤成 1-3 5 7 / 9 11
        int bestK = -1;
        int bestDiff = total + 1;
        for (int k = 1; k < n; k++) {
            int headLen = disp(items.subList(0, k));
            if (headLen <= max && disp(items.subList(k, n)) <= max) {
                // 用 2 倍距离比较，避免浮点：等价于 |headLen − total/2|，平手保留更靠前的 k
                int diff = Math.abs(headLen * 2 - total);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestK = k;
                }
            }
        }
        if (bestK > 0) {
            lines.add(join(items.subList(0, bestK)));
            lines.add(join(items.subList(bestK, n)));
            return lines;
        }

        // 找出所有 Range 的下标
        List<Integer> ridx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (items.get(i).indexOf('-') >= 0) {
                ridx.add(i);
            }
        }

        // B-1 散点截断：第一行从头贪心，第二行从尾部倒推、头部加紧贴数字的 "…"
        if (ridx.isEmpty()) {
            List<String> line1 = new ArrayList<>();
            for (String x : items) {
                List<String> next = new ArrayList<>(line1);
                next.add(x);
                if (disp(next) <= max) {
                    line1 = next;
                } else {
                    break;
                }
            }
            List<String> line2 = new ArrayList<>();
            for (int i = n - 1; i >= 0; i--) {
                List<String> next = new ArrayList<>();
                next.add(items.get(i));
                next.addAll(line2);
                if (disp(next) <= max - 1) { // 预留 "…" 头部 1 字符
                    line2 = next;
                } else {
                    break;
                }
            }
            lines.add(join(line1));
            lines.add("…" + join(line2));
            return lines;
        }

        // B-2 双 Range 紧凑折叠截断：
        // 第一行 = 首个 Range 前的散号（贪心）+ … + 首个 Range；
        // 第二行 = … + 尾部 Range，尾部 Range 之后还有被折叠项则再补一个 …
        int firstR = ridx.get(0);
        int lastR = ridx.get(ridx.size() - 1);

        // 只有一个区间：区间只许露一次（0.3.17 修复），否则看着像号码重复
        if (firstR == lastR) {
            String mid = items.get(firstR);
            if (firstR == 0) {
                // 区间就在最前：行1 让它带头，行2 接后面的散号
                lines.add(n > 1 ? mid + "…" : mid);
                if (n > 1) {
                    lines.add(ellipsizedTail(items, 1, n, null, max));
                }
                return lines;
            }
            // 行1 = 区间前的散号（贪心）+ …；行2 = … + 区间 + 区间后的散号（贪心）
            List<String> front = new ArrayList<>();
            for (int i = 0; i < firstR; i++) {
                List<String> next = new ArrayList<>(front);
                next.add(items.get(i));
                if (disp(next) + 1 <= max) { // 末尾还要放一个省略号
                    front = next;
                } else {
                    break;
                }
            }
            lines.add(join(front) + "…");
            lines.add(ellipsizedTail(items, firstR + 1, n, mid, max));
            return lines;
        }

        String head = items.get(firstR);
        int budget = max - 1 - head.length(); // 预留 1 字符连接省略号
        List<String> front = new ArrayList<>();
        for (int i = 0; i < firstR; i++) {
            int need = items.get(i).length() + (front.isEmpty() ? 0 : 1);
            if (disp(front) + need <= budget) {
                front.add(items.get(i));
            } else {
                break;
            }
        }
        // 首行在首个区间后继续贪心补中间词（不越过尾部区间）：
        // 1-3 / …21-30… 改成 1-3 5 / …21-30…，首行信息量用足
        StringBuilder firstLine = new StringBuilder(front.isEmpty() ? head : join(front) + "…" + head);
        int covered = firstR;
        for (int i = firstR + 1; i < lastR; i++) {
            String candidate = firstLine + " " + items.get(i);
            if (candidate.length() <= max) {
                firstLine.append(' ').append(items.get(i));
                covered = i;
            } else {
                break;
            }
        }

        String tailRange = items.get(lastR);
        // 中间词全部露出时第二行紧接首行，不再加前导省略号（1-3 5 / 7-15…）；
        // 中间确有折叠内容才用 … 起头（…21-30… / …21-22）
        String leadEllipsis = covered == lastR - 1 ? "" : "…";
        lines.add(firstLine.toString());
        lines.add(leadEllipsis + tailRange + (lastR < n - 1 ? "…" : ""));
        return lines;
    }

    /** 解析 → 去重 → 合并相邻/连续区间 → 排序的显示串列表。 */
    private static List<String> preprocess(String rawText) {
        List<int[]> intervals = new ArrayList<>();
        for (String token : tokenize(rawText)) {
            int dash = token.indexOf('-');
            try {
                int a;
                int b;
                if (dash >= 0) {
                    a = Integer.parseInt(token.substring(0, dash));
                    b = Integer.parseInt(token.substring(dash + 1));
                } else {
                    a = Integer.parseInt(token);
                    b = a;
                }
                if (a > b) {
                    int t = a;
                    a = b;
                    b = t;
                }
                if (a > 0) {
                    intervals.add(new int[]{a, b});
                }
            } catch (NumberFormatException e) {
                // 解析不了的片段直接跳过（展示态容错）
            }
        }
        intervals.sort((p, q) -> p[0] != q[0] ? Integer.compare(p[0], q[0]) : Integer.compare(p[1], q[1]));

        List<int[]> merged = new ArrayList<>();
        for (int[] iv : intervals) {
            if (!merged.isEmpty() && iv[0] <= merged.get(merged.size() - 1)[1] + 1) {
                int[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], iv[1]);
            } else {
                merged.add(new int[]{iv[0], iv[1]});
            }
        }

        List<String> items = new ArrayList<>();
        for (int[] iv : merged) {
            items.add(iv[0] == iv[1] ? String.valueOf(iv[0]) : iv[0] + "-" + iv[1]);
        }
        return items;
    }

    /** 与 ShedCoverage 一致的分隔符口径：空格、顿号、逗号、分号、斜杠，以及 到/至/~ 等区间写法。 */
    private static List<String> tokenize(String rawText) {
        String text = rawText.trim()
                .replace('到', '-').replace('至', '-')
                .replace('~', '-').replace('～', '-')
                .replace('—', '-').replace('－', '-')
                .replaceAll("\\s*-\\s*", "-");
        List<String> tokens = new ArrayList<>();
        for (String p : text.split("[,，、;；/\\s]+")) {
            String t = p.trim();
            if (!t.isEmpty()) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    /**
     * 折叠行的后半段：可选的"头一个项"（通常是区间），再从 from 到 to-1 贪心塞散号；
     * 没塞完就在末尾补一个 …。整体不超过单行预算。
     */
    private static String ellipsizedTail(List<String> items, int from, int to, String head, int maxChars) {
        StringBuilder sb = new StringBuilder("…");
        if (head != null) {
            sb.append(head);
        }
        boolean clipped = false;
        for (int i = from; i < to; i++) {
            // 省略号紧贴第一个项（没有 head 时不加空格），与 B-2 的 "…25-32" 风格一致
            String sep = sb.length() > 1 ? " " : "";
            String candidate = sb.toString() + sep + items.get(i);
            if (candidate.length() + 1 <= maxChars) { // 预留末尾省略号的 1 字符
                sb.append(sep).append(items.get(i));
            } else {
                clipped = true;
                break;
            }
        }
        if (clipped) {
            sb.append("…");
        }
        return sb.toString();
    }

    /** 一行（用半角空格拼接）的字符长度。 */
    private static int disp(List<String> items) {
        if (items.isEmpty()) {
            return 0;
        }
        int len = items.size() - 1;
        for (String x : items) {
            len += x.length();
        }
        return len;
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
