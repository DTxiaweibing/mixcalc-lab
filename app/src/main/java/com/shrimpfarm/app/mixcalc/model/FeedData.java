package com.shrimpfarm.app.mixcalc.model;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

public class FeedData {
    // 用户填的棚号原文 = 这行的“声明”。打点覆盖不会改写它，所以后面的行缩小/清空时，
    // 被占走的号还能原样退回这行。落盘存的就是它。
    private String shedNumber;
    // 显示文本 = 打点覆盖后这行“实际拥有”的号码。纯派生，不落盘，每次覆盖计算刷新；
    // 为 null 表示还没算过，显示回落到 shedNumber。
    private String shedDisplay;
    private int shedCount;
    // 第 1 行专用：锚点值，手改后的显示值。
    // 第一行显示值 = shedCountBase - (后面各行之和 - shedCountBaseSum)
    private int shedCountBase;
    // 第 1 行专用：设锚点那一刻，后面各行棚数之和。用来只扣“增量”，不重复扣
    private int shedCountBaseSum;
    private double average;
    private double fermentedFeed;
    private double powderFeed;
    private double feed03;
    private double feed05;
    private double feed10;
    private double water;
    private double weighedFeed;

    private int defaultShedCount = 0;
    private boolean isShedCountManuallyModified = false;
    private boolean isAverageManuallyModified = false;
    // 换料比例：粒径较大那一种非发酵料的占比 (0.1~0.9)，0 表示未设置
    private double switchRatio = 0;
    // 非发酵料按粒径由小到大：粉料、0.3、0.5、1.0
    private static final int NF_POWDER = 0, NF_FEED03 = 1, NF_FEED05 = 2, NF_FEED10 = 3;

    public FeedData() {
        this.shedNumber = "";
        this.shedCount = 0;
        this.shedCountBase = 0;
        this.shedCountBaseSum = 0;
        this.average = 0;
        this.fermentedFeed = 0;
        this.powderFeed = 0;
        this.feed03 = 0;
        this.feed05 = 0;
        this.feed10 = 0;
        this.water = 0;
        this.weighedFeed = 0;
        this.isShedCountManuallyModified = false;
        this.isAverageManuallyModified = false;
    }

    public void setShedCount(double value) {
        setShedCount((int)value);
    }

    public void setShedCountDirectly(int shedCount, boolean manuallyModified) {
        this.shedCount = shedCount;
        this.isShedCountManuallyModified = manuallyModified;
    }

    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("shedNumber", shedNumber != null ? shedNumber : "");
            json.put("shedCount", shedCount);
            json.put("shedCountBase", shedCountBase);
            json.put("shedCountBaseSum", shedCountBaseSum);
            json.put("average", average);
            json.put("fermentedFeed", fermentedFeed);
            json.put("powderFeed", powderFeed);
            json.put("feed03", feed03);
            json.put("feed05", feed05);
            json.put("feed10", feed10);
            json.put("water", water);
            json.put("weighedFeed", weighedFeed);
            json.put("defaultShedCount", defaultShedCount);
            json.put("isShedCountManuallyModified", isShedCountManuallyModified);
            json.put("isAverageManuallyModified", isAverageManuallyModified);
            json.put("switchRatio", switchRatio);
            return json;
        } catch (JSONException e) {
            Log.e("FeedData", "转换为JSON异常: " + e.getMessage());
            return new JSONObject();
        }
    }

    public void fromJson(JSONObject json) {
        try {
            if (json != null) {
                shedNumber = json.optString("shedNumber", "");
                shedCount = json.optInt("shedCount", 0);
                shedCountBase = json.optInt("shedCountBase", 0);
                shedCountBaseSum = json.optInt("shedCountBaseSum", 0);
                average = json.optDouble("average", 0);
                fermentedFeed = json.optDouble("fermentedFeed", 0);
                powderFeed = json.optDouble("powderFeed", 0);
                feed03 = json.optDouble("feed03", 0);
                feed05 = json.optDouble("feed05", 0);
                feed10 = json.optDouble("feed10", 0);
                water = json.optDouble("water", 0);
                weighedFeed = json.optDouble("weighedFeed", 0);
                defaultShedCount = json.optInt("defaultShedCount", 0);
                isShedCountManuallyModified = json.optBoolean("isShedCountManuallyModified", false);
                isAverageManuallyModified = json.optBoolean("isAverageManuallyModified", false);
                switchRatio = json.optDouble("switchRatio", 0);
            }
        } catch (Exception e) {
            Log.e("FeedData", "从JSON恢复数据异常: " + e.getMessage());
        }
    }

    public void calculateShedCount() {
        if (shedNumber == null || shedNumber.trim().isEmpty()) {
            this.shedCount = 0;
            this.defaultShedCount = 0;
            return;
        }
        // 行内重复棚号只算一次；跨行重叠由 Activity 的打点覆盖处理
        this.shedCount = ShedCoverage.parseShedNumbers(shedNumber).size();
        this.defaultShedCount = this.shedCount;
    }

    public void calculateAverage(boolean fermentedIncludedInAverage) {
        double sum = powderFeed + feed03 + feed05 + feed10;
        if (fermentedIncludedInAverage) {
            sum += fermentedFeed;
        }
        this.average = shedCount > 0 ? sum / shedCount : 0;
    }

    public void calculateWater(int waterPercentage, boolean fermentedIncludedInWater) {
        double sum = powderFeed + feed03 + feed05 + feed10;
        if (fermentedIncludedInWater) {
            sum += fermentedFeed;
        }
        this.water = sum * waterPercentage / 100.0;
    }

    public void calculateWeighedFeed(boolean fermentedIncludedInWater) {
        double totalWeight = powderFeed + feed03 + feed05 + feed10 + water;
        if (fermentedIncludedInWater) {
            totalWeight += fermentedFeed;
        } else {
            if (fermentedFeed > 0) {
                totalWeight += fermentedFeed;
            }
        }
        this.weighedFeed = shedCount > 0 ? totalWeight / shedCount : 0;
    }

    public void updateAllCalculations(int waterPercentage, boolean fermentedIncludedInWater, boolean fermentedIncludedInAverage) {
        // 换料比例只有恰好两种非发酵料时才算数，不然直接作废
        if (countNonFermentedFeeds() != 2) {
            switchRatio = 0;
        }
        // 不要在这里重算棚数：shedNumber 解析出来的是“本行自己数的个数”，
        // 跨行重叠时生效的是 Activity 打点覆盖后的棚数。若这里先改掉 shedCount，
        // 覆盖逻辑会误判“棚数变了”，把用户刚输入的料量按错误棚数重算一遍。
        // shedNumber 变更统一走 setShedNumber()。
        recalculateDerivedOnly(waterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
    }

    // 只重算跟棚数有关的派生值（平均吃料、水量、称料），棚数本身不动。
    // 第一行扣完基数后刷一下就用它。
    public void recalculateDerivedOnly(int waterPercentage, boolean fermentedIncludedInWater, boolean fermentedIncludedInAverage) {
        if (isAverageManuallyModified) {
            if (canEditAverage()) {
                applyManualAverage(fermentedIncludedInAverage);
            } else {
                isAverageManuallyModified = false;
            }
        }
        if (!isAverageManuallyModified) {
            calculateAverage(fermentedIncludedInAverage);
        }
        calculateWater(waterPercentage, fermentedIncludedInWater);
        calculateWeighedFeed(fermentedIncludedInWater);
    }

    public int getShedCountBase() {
        return shedCountBase;
    }

    public void setShedCountBase(int shedCountBase) {
        this.shedCountBase = shedCountBase;
    }

    public int getShedCountBaseSum() {
        return shedCountBaseSum;
    }

    public void setShedCountBaseSum(int shedCountBaseSum) {
        this.shedCountBaseSum = shedCountBaseSum;
    }

    // 非发酵料（粉/0.3/0.5/1.0）中有数据的种类数
    public int countNonFermentedFeeds() {
        int count = 0;
        for (int i = 0; i <= NF_FEED10; i++) {
            if (getNonFermentedValue(i) > 0) {
                count++;
            }
        }
        return count;
    }

    // 平均列是否可手动编辑：非发酵料 0/1/2 种，且不能整行为空
    public boolean canEditAverage() {
        if (shedCount <= 0) {
            return false;
        }
        int nf = countNonFermentedFeeds();
        if (nf >= 3) {
            return false;
        }
        if (nf == 0 && fermentedFeed <= 0) {
            return false;
        }
        return true;
    }

    public boolean isAverageManuallyModified() {
        return isAverageManuallyModified;
    }

    public void setAverageManuallyModified(boolean manuallyModified) {
        this.isAverageManuallyModified = manuallyModified;
    }

    public double getSwitchRatio() {
        return switchRatio;
    }

    public void setSwitchRatio(double switchRatio) {
        this.switchRatio = switchRatio;
    }

    public void setAverageManually(double value, boolean fermentedIncludedInAverage) {
        this.average = value;
        if (value > 0 && canEditAverage()) {
            this.isAverageManuallyModified = true;
            applyManualAverage(fermentedIncludedInAverage);
        } else {
            this.isAverageManuallyModified = false;
        }
    }

    // 手动填了平均之后，把总量按下面的规则拆回各料格
    //   nf==0：只有发酵料，全给发酵料
    //   nf==1：该非发酵料 = 平均×棚数 - (算平均的话扣掉发酵料)
    //   nf==2：两料按比例分
    private void applyManualAverage(boolean fermentedIncludedInAverage) {
        double amount = average * shedCount;
        int nf = countNonFermentedFeeds();

        if (nf == 0) {
            if (fermentedFeed > 0) {
                fermentedFeed = amount;
            }
            return;
        }

        double base = amount;
        if (fermentedIncludedInAverage && fermentedFeed > 0) {
            base -= fermentedFeed;
        }
        if (base < 0) {
            base = 0;
        }

        if (nf == 1) {
            int small = -1;
            for (int i = 0; i <= NF_FEED10; i++) {
                if (getNonFermentedValue(i) > 0) {
                    small = i;
                    break;
                }
            }
            // 选了换料比例、后面还有大一号的料可换，就把总量按比例拆成两档（大一号 = 总量×ratio）
            if (small >= 0 && switchRatio > 0 && small + 1 <= NF_FEED10) {
                double ratio = switchRatio;
                if (ratio < 0.1) {
                    ratio = 0.1;
                }
                if (ratio > 0.9) {
                    ratio = 0.9;
                }
                setNonFermentedValue(small + 1, base * ratio);
                setNonFermentedValue(small, base * (1 - ratio));
            } else if (small >= 0) {
                setNonFermentedValue(small, base);
            }
        } else if (nf == 2) {
            int small = -1;
            int large = -1;
            for (int i = 0; i <= NF_FEED10; i++) {
                if (getNonFermentedValue(i) > 0) {
                    if (small < 0) {
                        small = i;
                    } else {
                        large = i;
                        break;
                    }
                }
            }
            double ratio = switchRatio;
            if (ratio <= 0 || ratio >= 1) {
                double sv = getNonFermentedValue(small);
                double lv = getNonFermentedValue(large);
                double sum = sv + lv;
                ratio = sum > 0 ? lv / sum : 0.5;
            }
            if (ratio < 0.1) {
                ratio = 0.1;
            }
            if (ratio > 0.9) {
                ratio = 0.9;
            }
            setNonFermentedValue(large, base * ratio);
            setNonFermentedValue(small, base * (1 - ratio));
        }
    }

    private double getNonFermentedValue(int index) {
        switch (index) {
            case NF_POWDER: return powderFeed;
            case NF_FEED03: return feed03;
            case NF_FEED05: return feed05;
            case NF_FEED10: return feed10;
            default: return 0;
        }
    }

    private void setNonFermentedValue(int index, double value) {
        switch (index) {
            case NF_POWDER: powderFeed = value; break;
            case NF_FEED03: feed03 = value; break;
            case NF_FEED05: feed05 = value; break;
            case NF_FEED10: feed10 = value; break;
            default: break;
        }
    }

    // 换料完成：恰好两档非发酵料、清空的是细的那档时，把这格编辑前的值
    // (amount) 并进粗的那档，本档清零，返回并入的值；不满足条件返回 0。
    // 为什么用调用方传的原值、不用当前值：浮层是逐字符提交的，退格退到最后一位时
    // 格里的数早被改小了，只有编辑前的快照才是真正的"细粒径料数值"。
    // 合完只剩一档，换料比例作废，也就是"除发酵料外只剩一种料"的完成态，总量守恒。
    // clearedIndex 被清空的非发酵料下标：0=粉 1=0.3 2=0.5 3=1.0
    public double mergeSmallerWhenCleared(int clearedIndex, double amount) {
        if (clearedIndex < NF_POWDER || clearedIndex > NF_FEED10 || amount <= 0) {
            return 0;
        }
        int coarserIndex = -1;
        int coarserCount = 0;
        for (int i = 0; i <= NF_FEED10; i++) {
            if (i == clearedIndex || getNonFermentedValue(i) <= 0) {
                continue;
            }
            if (i < clearedIndex) {
                // 前面还有更细的一档有料，不算"细粒径退场→换料完成"
                return 0;
            }
            coarserIndex = i;
            coarserCount++;
        }
        if (coarserCount != 1) {
            return 0;
        }
        setNonFermentedValue(coarserIndex, getNonFermentedValue(coarserIndex) + amount);
        setNonFermentedValue(clearedIndex, 0);
        switchRatio = 0;
        return amount;
    }

    public String getShedNumber() {
        return shedNumber != null ? shedNumber : "";
    }

    /**
     * 用户声明/落盘的棚号原文。打点覆盖不会动它——被后行占走的号只是“暂时不归你”，
     * 后行改小之后还会退回来。
     */
    public void setShedNumber(String shedNumber) {
        this.shedNumber = shedNumber != null ? shedNumber : "";
        // 申明变了，派生显示就失效，等下一次打点覆盖重新算；不清会留下旧文本对不上新声明
        this.shedDisplay = null;
        calculateShedCount();
    }

    /** 实际拥有的棚号文本（派生值）；没算过时回落到声明原文。 */
    public String getShedDisplay() {
        return shedDisplay != null ? shedDisplay : getShedNumber();
    }

    public void setShedDisplay(String shedDisplay) {
        this.shedDisplay = shedDisplay;
    }

    public int getShedCount() {
        return shedCount;
    }

    public void setShedCount(int shedCount) {
        this.shedCount = shedCount;
        this.isShedCountManuallyModified = true;
    }

    public int getDefaultShedCount() {
        return defaultShedCount;
    }

    public double getAverage() {
        return average;
    }

    public double getAverage(boolean fermentedIncludedInAverage) {
        if (isAverageManuallyModified) {
            return average;
        }
        double sum = powderFeed + feed03 + feed05 + feed10;
        if (fermentedIncludedInAverage) {
            sum += fermentedFeed;
        }
        return shedCount > 0 ? sum / shedCount : 0;
    }

    public void setAverage(double average) {
        this.average = average;
    }

    public double getFermentedFeed() {
        return fermentedFeed;
    }

    public void setFermentedFeed(double fermentedFeed) {
        this.fermentedFeed = fermentedFeed;
    }

    public double getPowderFeed() {
        return powderFeed;
    }

    public void setPowderFeed(double powderFeed) {
        this.powderFeed = powderFeed;
    }

    public double getFeed03() {
        return feed03;
    }

    public void setFeed03(double feed03) {
        this.feed03 = feed03;
    }

    public double getFeed05() {
        return feed05;
    }

    public void setFeed05(double feed05) {
        this.feed05 = feed05;
    }

    public double getFeed10() {
        return feed10;
    }

    public void setFeed10(double feed10) {
        this.feed10 = feed10;
    }

    public double getWater() {
        return water;
    }

    public void setWater(double water) {
        this.water = water;
    }

    public double getWeighedFeed() {
        return weighedFeed;
    }

    public void setWeighedFeed(double weighedFeed) {
        this.weighedFeed = weighedFeed;
    }

    public boolean isShedCountManuallyModified() {
        return isShedCountManuallyModified;
    }

    public void setShedCountManuallyModified(boolean manuallyModified) {
        this.isShedCountManuallyModified = manuallyModified;
    }
}
