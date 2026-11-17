package com.shrimpfarm.app.mixcalc.model;

import java.util.List;

/**
 * 棚号编辑快照的行对齐判据。
 *
 * <p>打字途中的重算以「进入编辑前的快照」为基线，快照的行号必须和当前行列表逐行对应；
 * 结算删过行之后行号就错了，继续用旧快照会把棚数算到别的行上。</p>
 */
public final class ShedSnapshotAlignment {

    private ShedSnapshotAlignment() {
    }

    /**
     * @param editStartRows 进入编辑那一刻的行对象列表
     * @param currentRows   当前行对象列表
     * @return 两列逐行都是同一对象（按引用比）才为 true
     */
    public static boolean matches(List<?> editStartRows, List<?> currentRows) {
        if (editStartRows == null || currentRows == null) {
            return false;
        }
        if (editStartRows.size() != currentRows.size()) {
            return false;
        }
        for (int i = 0; i < editStartRows.size(); i++) {
            // 按引用比：空行内容一样，但不是同一行也算错位
            if (editStartRows.get(i) != currentRows.get(i)) {
                return false;
            }
        }
        return true;
    }
}
