package com.shrimpfarm.app.mixcalc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 棚号输入框「两行极简显示」算法（展示态专用）。
 *
 * <p>口径：数字、短横杠、各项之间的半角空格、省略号 … 各算 1 个字符；
 * 省略号可紧贴数字，起「分隔 + 折叠提示」双重作用。</p>
 *
 * <p>铁律：</p>
 * <ol>
 * <li>Range 绝对完整：绝不把任何连续区间（如 3-15）从中间切断；</li>
 * <li>单行硬上限：每行 ≤ 9 字符，最多 2 行，任何情况不得爆行；</li>
 * <li>省略号语义：仅当真发生截断时才出现，无信息丢失时绝不出现；</li>
 * <li>信息密度优先：必须截断时优先让首、尾两个关键 Range 都露出。</li>
 * </ol>
 *
 * <p>执行分支（命中即返回）：A-1 单行全显示 → A-2 两行全显示 →
 * B-1 散点截断（无 Range 时）→ B-2 双 Range 紧凑折叠截断。</p>
 *
 * <p>编辑态不走这里：点进格子时编辑浮层加载的仍是完整原始字符串。</p>
 */
public final class ShedDisplay {

    /** 单行硬上限（字符）。 */
    private static final int MAX = 9;

    private ShedDisplay() {
    }

    /**
     * 把原始棚号文本折叠成 1~2 行展示串。
     *
     * @return 非空行的列表（1 或 2 个元素）；原文为空或完全不可解析时返回单行原文。
     */
    public static List<String> render(String rawText) {
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
        if (total <= MAX) {
            lines.add(join(items));
            return lines;
        }

        // A-2 两行全显示（无省略号）
        for (int k = 1; k < n; k++) {
            if (disp(items.subList(0, k)) <= MAX && disp(items.subList(k, n)) <= MAX) {
                lines.add(join(items.subList(0, k)));
                lines.add(join(items.subList(k, n)));
                return lines;
            }
        }

        // 找出所有 Range 的下标
        List<Integer> ridx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (items.get(i).indexOf('-') >= 0) {
                ridx.add(i);
            }
        }

        // B-1 散点截断：第一行从头贪心，第二行从尾部倒推、头部加 "… "
        if (ridx.isEmpty()) {
            List<String> line1 = new ArrayList<>();
            for (String x : items) {
                List<String> next = new ArrayList<>(line1);
                next.add(x);
                if (disp(next) <= MAX) {
                    line1 = next;
                } else {
                    break;
                }
            }
            List<String> line2 = new ArrayList<>();
            for (int i = n - 1; i >= 0; i--) {
                String x = items.get(i);
                List<String> next = new ArrayList<>();
                next.add(x);
                next.addAll(line2);
                if (disp(next) <= MAX - 2) { // 预留 "… " 头部 2 字符
                    line2 = next;
                } else {
                    break;
                }
            }
            lines.add(join(line1));
            lines.add("… " + join(line2));
            return lines;
        }

        // B-2 双 Range 紧凑折叠截断：
        // 第一行 = 首个 Range 前的散号（贪心）+ … + 首个 Range；
        // 第二行 = … + 尾部 Range，尾部 Range 之后还有被折叠项则再补一个 …
        int firstR = ridx.get(0);
        int lastR = ridx.get(ridx.size() - 1);

        // 只有一个区间：区间只许露一次。原来首行放它、尾行又放它，
        // 用户看到 9-15 出现两遍，像是号码重复了。
        // 行1 = 区间前的散号（贪心）+ …；行2 = … + 区间 + 区间后的散号（贪心），没塞完再补 …
        if (firstR == lastR) {
            String mid = items.get(firstR);
            if (firstR == 0) {
                // 区间就在最前：行1 让它带头，行2 接后面的散号
                lines.add(n > 1 ? mid + "…" : mid);
                if (n > 1) {
                    lines.add(ellipsizedTail(items, 1, n, null));
                }
                return lines;
            }
            List<String> front = new ArrayList<>();
            for (int i = 0; i < firstR; i++) {
                String x = items.get(i);
                List<String> next = new ArrayList<>(front);
                next.add(x);
                if (disp(next) + 1 <= MAX) { // 末尾还要放一个省略号
                    front = next;
                } else {
                    break;
                }
            }
            lines.add(join(front) + "…");
            lines.add(ellipsizedTail(items, firstR + 1, n, mid));
            return lines;
        }

        String head = items.get(firstR);
        int budget = MAX - 1 - head.length(); // 预留 1 字符连接省略号
        List<String> front = new ArrayList<>();
        for (int i = 0; i < firstR; i++) {
            String x = items.get(i);
            int need = x.length() + (front.isEmpty() ? 0 : 1);
            if (disp(front) + need <= budget) {
                front.add(x);
            } else {
                break;
            }
        }
        String line1 = front.isEmpty() ? head : join(front) + "…" + head;

        String tailRange = items.get(lastR);
        String line2 = "…" + tailRange + (lastR < n - 1 ? "…" : "");
        lines.add(line1);
        lines.add(line2);
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
     * 没塞完就在末尾补一个 …。整体不超过单行上限。
     */
    private static String ellipsizedTail(List<String> items, int from, int to, String head) {
        StringBuilder sb = new StringBuilder("…");
        if (head != null) {
            sb.append(head);
        }
        boolean clipped = false;
        for (int i = from; i < to; i++) {
            // 省略号紧贴第一个项（没有 head 时不加空格），与 B-2 的 "…25-32" 风格一致
            String sep = sb.length() > 1 ? " " : "";
            String candidate = sb.toString() + sep + items.get(i);
            if (candidate.length() + 1 <= MAX) { // 预留末尾省略号的 1 字符
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
