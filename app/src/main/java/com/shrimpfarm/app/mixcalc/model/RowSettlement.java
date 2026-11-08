package com.shrimpfarm.app.mixcalc.model;

import java.util.List;

/**
 * 失焦结算：棚数为 0 的行当“新的一行”处理。
 *
 * <p>只在用户真正离开编辑时调用（收起浮层、换格、切批次、退出页面、载入）；
 * 编辑途中只按显示规则把料列置灰、平均/水/称料留白，底层数据必须保留，
 * 这样棚数重新分回来时数据能立刻恢复显示。</p>
 *
 * <p>棚号列不清：用户填过的棚号保留，只清料量、水和派生值。</p>
 */
public final class RowSettlement {

    private RowSettlement() {
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
