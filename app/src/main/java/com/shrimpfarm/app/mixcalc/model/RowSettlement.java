package com.shrimpfarm.app.mixcalc.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 失焦结算：有效棚数为 0 的行整行回收。
 *
 * <p>只在用户真正离开编辑时调用（收起浮层、换格、切批次、退出页面、载入）；
 * 编辑途中只按显示规则把料列置灰、平均/水/称料留白，底层数据必须保留，
 * 这样棚数重新分回来时数据能立刻恢复显示。</p>
 *
 * <p>结算时分两级：</p>
 * <ol>
 * <li>{@link #recycleDeadRows(List)}——行级销毁：有内容（填过棚号或填过数据）但有效
 *     棚数已是 0 的行，整行从列表里移除，下面的行序号自动上移；调用方在底部补一行
 *     空行保持总行数。</li>
 * <li>{@link #settleZeroRows(List)}——兜底：万一还有棚数为 0 却留着料的行（理论上
 *     回收后不会剩），把数据清成新行，棚号保留。</li>
 * </ol>
 */
public final class RowSettlement {

    private RowSettlement() {
    }

    /**
     * 行级销毁：有内容但有效棚数为 0 的行，从列表里整行移除（连棚号文本一起）。
     *
     * <p>纯空行（没填过棚号也没填过数据）不算死行——那是留给用户继续录入的容量，
     * 删掉它们等于把表清空。删除依据在清数据之前判断，所以“只有数据没有棚号”的
     * 历史脏行也能一并回收，不会留成中间的空行。</p>
     *
     * @return 被移除行的下标（升序）；没删到返回空列表
     */
    public static List<Integer> recycleDeadRows(List<FeedData> rows) {
        return recycleDeadRows(rows, null);
    }

    /**
     * 同上，外加"本次被用户掏空的行"。
     *
     * <p>用户在气泡里把某一行最后几个棚号删掉（棚卖出）之后，这一行会变成既没有棚号、
     * 也没有数据的纯空行，按上面的规则会被当成"录入容量"留下来，于是表中间留一个洞，
     * 下面的行不会上顶。这类行是刚才的操作主动掏空的，必须回收；调用方把下标放进
     * {@code emptiedByUser} 即可（只有真的被掏空的行才该放，别塞纯空行）。</p>
     */
    public static List<Integer> recycleDeadRows(List<FeedData> rows, Set<Integer> emptiedByUser) {
        List<Integer> dead = new ArrayList<>();
        if (rows == null) {
            return dead;
        }
        // 有效棚数一律按申明文本重算（后行优先），**不看 FeedData 里的缓存值**：
        // 结算时机早于某些路径的刷新，缓存可能是"这行自己数的个数"，
        // 于是几行同时被下面一行覆盖时，这一轮只认出一个死行、其余要等下次操作才删，
        // 用户看到的就是"一行一行往上进"。以文本为准，一次就能把该删的全认出来。
        List<String> texts = new ArrayList<>(rows.size());
        for (FeedData data : rows) {
            texts.add(data == null ? "" : data.getShedNumber());
        }
        int[] owned = ShedCoverage.ownedCounts(texts);
        for (int i = 0; i < rows.size(); i++) {
            FeedData data = rows.get(i);
            if (data == null) {
                continue;
            }
            if (owned[i] > 0) {
                continue;
            }
            if (hasContent(data) || (emptiedByUser != null && emptiedByUser.contains(i))) {
                dead.add(i);
            }
        }
        for (int i = dead.size() - 1; i >= 0; i--) {
            rows.remove(dead.get(i).intValue());
        }
        return dead;
    }

    /** 这行是否被用户填过东西（棚号文本或任何数据）。 */
    private static boolean hasContent(FeedData data) {
        String text = data.getShedNumber();
        if (text != null && !text.trim().isEmpty()) {
            return true;
        }
        return hasRowData(data);
    }

    /**
     * @return 是否真的清掉了数据（没变化就不用触发重绘/落盘）
     */
    public static boolean settleZeroRows(List<FeedData> rows) {
        if (rows == null) {
            return false;
        }
        boolean changed = false;
        for (FeedData data : rows) {
            if (data == null || data.getShedCount() > 0 || !hasRowData(data)) {
                continue;
            }
            data.setAverage(0);
            data.setFermentedFeed(0);
            data.setPowderFeed(0);
            data.setFeed03(0);
            data.setFeed05(0);
            data.setFeed10(0);
            data.setWater(0);
            data.setWeighedFeed(0);
            data.setSwitchRatio(0);
            data.setAverageManuallyModified(false);
            changed = true;
        }
        return changed;
    }

    private static boolean hasRowData(FeedData data) {
        return data.getAverage() != 0
                || data.getFermentedFeed() != 0
                || data.getPowderFeed() != 0
                || data.getFeed03() != 0
                || data.getFeed05() != 0
                || data.getFeed10() != 0
                || data.getWater() != 0
                || data.getWeighedFeed() != 0
                || data.getSwitchRatio() != 0
                || data.isAverageManuallyModified();
    }
}
