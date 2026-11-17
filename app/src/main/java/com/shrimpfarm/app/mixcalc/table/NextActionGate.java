package com.shrimpfarm.app.mixcalc.table;

/**
 * 「下一步 / 下一行」的点击节流：与鸿蒙 {@code lastNextAt} 同值 120ms。
 */
public final class NextActionGate {

    /** 两次导航的最小间隔（毫秒）。 */
    public static final long MIN_INTERVAL_MS = 120L;

    private NextActionGate() {
    }

    /**
     * @param now    当前时间（单调时钟）
     * @param lastAt 上一次导航放行的时间；从未导航过传 0
     * @return 允许本次导航
     */
    public static boolean allow(long now, long lastAt) {
        // 时钟回拨时 now - lastAt 为负，同样落到"不通过"，不会永久放行
        return now - lastAt >= MIN_INTERVAL_MS;
    }
}
