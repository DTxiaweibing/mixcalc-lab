package com.shrimpfarm.app.mixcalc.model;

import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

public class FeedData {
    private String shedNumber;
    private int shedCount;
    // 第 1 行专用：未扣减的基数（第 2..N 行棚数之和要从这里扣掉）
    private int shedCountBase;
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

    public void updateAllCalculations(int waterPercentage) {
        updateAllCalculations(waterPercentage, false, false);
    }
    
    public void updateAllCalculations(int waterPercentage, boolean fermentedIncludedInWater) {
        updateAllCalculations(waterPercentage, fermentedIncludedInWater, false);
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
        if (isShedCountManuallyModified) {
            return;
        }
        if (shedNumber == null || shedNumber.trim().isEmpty()) {
            this.shedCount = 0;
            this.defaultShedCount = 0;
            return;
        }
        String cleanNumber = shedNumber.trim();
        this.shedCount = 0;
        this.defaultShedCount = 0;
        cleanNumber = cleanNumber.replace("，", ",")
            .replace("、", ",")
            .replace(" ", ",")
            .replace(",,", ",")
            .replace(",,", ",");
        String[] parts = cleanNumber.split(",");
        int totalCount = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            int partCount = parsePartCount(part);
            totalCount += partCount;
        }
        this.shedCount = Math.max(0, totalCount);
        this.defaultShedCount = this.shedCount;
    }

    private int parsePartCount(String part) {
        if (part == null || part.trim().isEmpty()) {
            return 0;
        }
        if (part.contains("-") || part.contains("到") || part.contains("至") || part.contains("~") || part.contains("—")) {
            String rangePart = part.replace("到", "-")
                .replace("至", "-")
                .replace("~", "-")
                .replace("—", "-")
                .replace("号到", "-")
                .replace("号至", "-");
            String[] rangeParts = rangePart.split("-", 2);
            if (rangeParts.length == 2) {
                try {
                    String startStr = rangeParts[0].replaceAll("[^0-9]", "");
                    String endStr = rangeParts[1].replaceAll("[^0-9]", "");
                    if (!startStr.isEmpty() && !endStr.isEmpty()) {
                        int start = Integer.parseInt(startStr);
                        int end = Integer.parseInt(endStr);
                        if (start > 0 && end > 0) {
                            int result = (start <= end) ? (end - start + 1) : (start - end + 1);
                            return result;
                        }
                    }
                } catch (NumberFormatException e) { /* ignored */ }
            }
        }
        if (isValidNumber(part)) {
            return 1;
        }
        return 0;
    }

    private boolean isValidNumber(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        try {
            String numStr = str.replaceAll("[^0-9]", "");
            return !numStr.isEmpty();
        } catch (Exception e) {
            return false;
        }
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
        // 换料比例只在“恰好两种非发酵料”时有效，否则自动作废
        if (countNonFermentedFeeds() != 2) {
            switchRatio = 0;
        }
        if (!isShedCountManuallyModified) {
            calculateShedCount();
        }
        recalculateDerivedOnly(waterPercentage, fermentedIncludedInWater, fermentedIncludedInAverage);
    }

    /**
     * 只重算依赖棚数的派生值（平均吃料 / 水量 / 称料），不动棚数本身。
     * 供第 1 行扣完基数后刷新使用。
     */
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

    // 手动设定平均后，把总量按规则分回料格
    //   nf==0：只有发酵料 → 全给发酵料
    //   nf==1：该非发酵料 = 平均×棚数 - (参与平均?发酵料:0)
    //   nf==2：两料按比例分 平均×棚数 - (参与平均?发酵料:0)
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
            // 已选换料比例且还有“大一号”可换：把总量按比例拆成两档（大一号 = 总量×ratio）
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

    /**
     * 换料完成：恰好两档非发酵料、且被清空的是粒径较小那档时，把 {@code amount}
     * （进入编辑那一刻该格的原值）并入较大那档，本档清零。
     *
     * <p>用调用方传入的原值而非当前值：编辑浮层逐字符提交，退格清空到最后一位时
     * 单元格早已被改小，只有编辑前的快照才是真正的"细粒径料数值"。</p>
     *
     * <p>合并后只剩一档，换料比例随之作废，即「除发酵料外只有一种料」的换料完成状态。
     * 总量守恒，平均吃料 / 水量 / 称料都不变。</p>
     *
     * @param clearedIndex 被清空的非发酵料下标（0=粉 / 1=0.3 / 2=0.5 / 3=1.0）
     * @param amount       并入较大那档的数值
     * @return 并入的数值；条件不满足未合并返回 0
     */
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
                // 还有更细的一档有料：不满足"细粒径退场 → 换料完成"
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

    public void setShedNumber(String shedNumber) {
        this.shedNumber = shedNumber != null ? shedNumber : "";
        if (!isShedCountManuallyModified) {
            calculateShedCount();
        }
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

    public boolean isShedCountValid() {
        return shedCount > 0;
    }

    public String getShedCountDisplay() {
        return shedCount == 0 ? "" : String.valueOf(shedCount);
    }

    public String getAverageDisplay() {
        return average == 0 ? "" : formatDisplayNumber(average);
    }

    public String getFermentedFeedDisplay() {
        return fermentedFeed == 0 ? "" : formatDisplayNumber(fermentedFeed);
    }

    public String getPowderFeedDisplay() {
        return powderFeed == 0 ? "" : formatDisplayNumber(powderFeed);
    }

    public String getFeed03Display() {
        return feed03 == 0 ? "" : formatDisplayNumber(feed03);
    }

    public String getFeed05Display() {
        return feed05 == 0 ? "" : formatDisplayNumber(feed05);
    }

    public String getFeed10Display() {
        return feed10 == 0 ? "" : formatDisplayNumber(feed10);
    }

    public String getWaterDisplay() {
        return water == 0 ? "" : formatDisplayNumber(water);
    }

    public String getWeighedFeedDisplay() {
        if (weighedFeed == 0 || shedCount == 0) {
            return "";
        }
        return formatDisplayNumber(weighedFeed) + "×" + shedCount;
    }

    private String formatDisplayNumber(double value) {
        if (value == (int) value) {
            return String.valueOf((int) value);
        } else {
            return String.format(java.util.Locale.ROOT, "%.2f", value);
        }
    }
}
