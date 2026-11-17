package com.shrimpfarm.app.mixcalc.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * 回归：棚号编辑快照与当前行列表是否还对得上。
 *
 * <p>打字途中的全表重算是「以进入编辑前的快照为基线，只替换正在编辑的那一行」，
 * 快照里的**行号**必须和当前列表逐行对应。一旦结算删过行（行整体上移、底部又补了
 * 新空行），行号就错位了，继续用旧快照会把棚数/料量算到别的行上。这个判据就是
 * liveTyping 的闸门：任何一行换了对象或位置，立刻退回「按当前声明重排」的路径。</p>
 */
public class ShedSnapshotAlignmentTest {

    private static List<Object> rows(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    @Test
    public void matchesWhenSameObjectsInSameOrder() {
        Object a = new Object();
        Object b = new Object();
        assertTrue(ShedSnapshotAlignment.matches(rows(a, b), rows(a, b)));
        assertTrue(ShedSnapshotAlignment.matches(rows(), rows()));
    }

    @Test
    public void rejectsAfterRowRemoved() {
        // 删掉一行、底部队列补了新的空行：size 还是 3，但位置全错
        Object a = new Object();
        Object b = new Object();
        Object c = new Object();
        Object padding = new Object();
        assertFalse(ShedSnapshotAlignment.matches(rows(a, b, c), rows(b, c, padding)));
    }

    @Test
    public void rejectsEqualButDifferentRows() {
        // 空行内容一模一样，但不是同一行对象：位置已经变了，不能当基线
        assertFalse(ShedSnapshotAlignment.matches(rows(new Object()), rows(new Object())));
    }

    @Test
    public void rejectsSizeMismatchAndNulls() {
        Object a = new Object();
        assertFalse(ShedSnapshotAlignment.matches(rows(a), rows(a, a)));
        assertFalse(ShedSnapshotAlignment.matches(null, rows(a)));
        assertFalse(ShedSnapshotAlignment.matches(rows(a), null));
    }
}
