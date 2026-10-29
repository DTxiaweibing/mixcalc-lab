# 安卓版与鸿蒙版对齐 · 待办计划

> 由来：鸿蒙版（`D:\mixcalc-harmony`）已实现下列三项，安卓这边还是旧行为。
> 目标：把这三项搬到安卓，做到两版一致。**先把计划写下来，有空再动手。**
>
> 每完成一项：勾选本文件对应条目 + 在 `CHANGELOG.md` 记一条。

_创建：2026-10-29_

---

## 1. ✅ 棚号输入白名单（仅允许可解析的字符）

**现状（安卓）**
- `MixCalcActivity.createTableColumn()` 里 `shed_number` 用 `InputType.TYPE_CLASS_TEXT`，**任何字符都能打进去**。
- 解析在 `FeedData.calculateShedCount()` / `parsePartCount()`：`，、空格 → 逗号分隔`，`到 至 ~ — 号到 号至 → -`，其余字符靠 `replaceAll("[^0-9]", "")` 兜底。

**问题**
- 用户能打进字母、表情、汉字等无意义字符，虽然最终被兜底过滤，但输入框里一堆垃圾，且 `1abc2` 这种会算成 1 棚（有数字就算 1），容易误解。

**要做的**
- 给棚号列的 `EditText` 加 `InputFilter`，只放行**白名单**字符：

```
0-9  -  到  至  ~  —  、  ，  ,  （空格）  号
```

- 落点：`table/CellEditorOverlay.java` → `begin()` 里按 `column.id` 设置：

```java
if ("shed_number".equals(column.id)) {
    editText.setFilters(new InputFilter[]{ new InputFilter() {
        @Override public CharSequence filter(CharSequence src, int start, int end,
                                             Spanned dest, int dstart, int dend) {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                char c = src.charAt(i);
                if ((c >= '0' && c <= '9') || " -到至~—、，,号".indexOf(c) >= 0) sb.append(c);
            }
            return sb.length() == end - start ? null : sb.toString();
        }
    }});
} else {
    editText.setFilters(new InputFilter[0]);   // 数字列沿用 inputType 的数字过滤
}
```

- 注意：`begin()` 每次换格都会调用，`setFilters` 要按列重置，别让棚号的过滤器串到数字列上。

**验收**
- 棚号列：`1到40`、`1号至40号`、`1、2、3`、`1 2 3`、`1-40` 都能输，且棚数正确（40 / 40 / 3 / 3 / 40）。
- 棚号列：字母、汉字（除 到/至/号）、表情、标点（除白名单）打不进去。
- 数字列：行为不变（只数字 + 小数点）。

**鸿蒙参照**：`entry/src/main/ets/table/MixCalcTable.ets` → `.inputFilter(this.editingCol === 1 ? '[0-9\\-到至~—、，, 号]' : '[0-9.]')`

---

## 2. ✅ 顺序录入限制（上一行没填，下一行不许输入）

**现状（安卓）**
- 任意行、任意格都能直接点开编辑。

**要做的规则**
- 定义「这一行有输入」= 该行 `shedNumber` 非空 **或** `shedCount > 0`。
- 点击某格进入编辑前：
  - 找出**第一行还没输入的行** `firstEmpty`（从第 1 行往下找）；
  - 若 `目标行 > firstEmpty` **且目标行本身也是空的** → **拦截**，弹提示，不进入编辑。
- 只拦「空行」：中间某行被清空后，**后面已经有数据的行照样能改**（否则会把人挡死）。

**提示弹窗（照鸿蒙样式，复用 `dialog_simple_confirm`）**
- 标题：`请按顺序录入`
- 正文：`请先填写第N行的「棚号」。`（N = firstEmpty + 1，带句号）
- 按钮：单个「知道了」（绿色 `#2D8C42`），点掉即关。

**落点**
- `MixCalcActivity` 里 `isCellEditable(row, columnId)`（或 `onCellClick` 前）加判断 + 弹窗。
- 同一套判断必须同时用在 **输入法「下一步」** 和 **「下一行」按钮**（见下面 2.1 / 2.2）。

**2.1 ✅ 键盘「下一步」也要守同一套规则**
- 现在 `IME_ACTION_NEXT` → `onEditNext(row, columnId)` 只是往右挪一格、行末换下一行，**没有任何拦截**。
- 改成：在 `onEditNext` 里复用同一段判断——
  - 同行右移：不拦；
  - 要跨到下一行时：若 `目标行 > firstEmpty && 目标行是空行` → 弹提示、**停在原格不跳**。
- 落点：`MixCalcActivity` 里给 table 设置的 `OnCellEditListener.onEditNext` 实现。
- 验收：第 1、2 行有数据、第 3 行空 → 从第 2 行最后一格按「下一步」→ 弹「请先填写第3行的『棚号』」，光标留在原格，键盘不收起。

**2.2 ✅ 安卓也要加「下一行 ▾」按钮（对齐鸿蒙）**
- 鸿蒙版在表格**右下角**有一个绿色胶囊按钮，作用 = **回车向下**（同列下一行）。
- 样式（照鸿蒙）：
  - 文字 `下一行 ▾`，13sp，白色
  - 宽 76dp、高 28dp，圆角 14dp，底色 `#2D8C42`
  - 位置：表格可视区右下角，距右 8dp、距表体底边 6dp 左右（鸿蒙是 `x = viewW - 84`、`y = 表头高 + 表体高 - 34`）
  - 只在编辑态显示（安卓是 EditText 常驻，点它不会收键盘）
- 行为：
  - 同列向下移一行；
  - **也受顺序录入规则控制**：本行没填 / 下一行是空行 → 弹提示、不移动；
  - 下面这行这一列不可编辑（例如没棚数时的平均/料格）→ **停在原处**，不跳、不关编辑。
- 落点：`MixCalcTableView` 里加一个子 View（或 `MixCalcActivity` 的 `FrameLayout` 里叠一个 `TextView`），在 `CellEditorOverlay.begin/end` 时显示/隐藏。
- 验收：
  - 棚号第 1 行点按钮 → 落到第 2 行棚号，键盘不掉；
  - 本行空着点按钮 → 弹顺序提示；
  - 第 30 行点按钮 → 停住不动。

**验收**
- 1、2 行有数据：点第 3 行（空）→ 正常编辑。
- 第 1 行空：点第 3 行 → 弹「请先填写第1行的『棚号』」，不进入编辑。
- 1、2、3 行都有数据 → 删掉第 2 行 → 第 3 行仍可编辑。
- 键盘「下一步」从第 2 行跨到第 3 行（3 为空）→ 弹提示、不跳（见 2.1）。
- 「下一行 ▾」按钮：本行空着点 → 弹提示；本行有数据点 → 同列下一行；到底或该列不可编辑 → 停住（见 2.2）。

**鸿蒙参照**：`MixCalcTable.ets` 的 `rowHasInput()` / `firstEmptyRow()` / `openEditor()` / `nextCell()` / `HintDialog`

---

## 3. ✅ 滑动惯性：改成鸿蒙式按帧指数衰减（不再用 OverScroller 封顶）

**现象**
- 快速甩动时**没有惯性**（像离手就停），慢速甩动正常。

**怀疑点（按顺序排查）**
1. `MixCalcTableView.capFlingVelocity()`：`limit` 公式在超大速度/极小跑道上可能算出 0 或 NaN，于是 `capped = 0` → 直接不 fling。
   - 需要加保护：`if (!Float.isFinite(limitValue) || limitValue < minFlingVelocity) limitValue = minFlingVelocity;`
2. `minFlingVelocity` 判断：`if (Math.abs(v) < minFlingVelocity) return;` 之后是否还有别的提前返回把大速度也挡了。
3. `VelocityTracker.computeCurrentVelocity(1000, maxFlingVelocity)` 的 `maxUnits` 用了 `maxFlingVelocity`（通常是 8000px/s），有些设备 `getScaledMaximumFlingVelocity()` 返回很大，导致计算单位混乱；建议固定 `computeCurrentVelocity(1000, 8000f)` 或与鸿蒙的像素/秒口径对齐。
4. 采样窗口：`VelocityTracker` 默认用最后 ~100ms；手速极快时可能只采到 1~2 个点，速度跳变 → 建议 `addMovement` 全收，`ACTION_UP` 时先 `computeCurrentVelocity` 再判断，必要时用最近 3 帧位移/时间自己算。

**鸿蒙怎么做的（可参照）**
- 每帧 `offsetX/offsetY` 按 `velocity` 累加，`velocity *= 0.95`（每帧衰减），`|velocity| < 0.2` 时停；**没有跑道上限的封顶**，靠帧衰减自然停下，所以手速再快也不会"算不出来"。
- 实现见 `MixCalcTable.ets`：`startFling(velocityX, velocityY)` / `onActionEnd`。

**两种改法（择一）**
- A（保守）：保留 `OverScroller`，只修 `capFlingVelocity` 的边界保护 + 采样口径，让大速度也能走完整条减速曲线。
- B（对齐鸿蒙）：自己按帧衰减来实现惯性，`v *= 0.95`，速度单位 = px/帧 × 60（换算成 px/s），彻底摆脱 `OverScroller` 的边界/曲线限制，和鸿蒙手感一致。

建议先做 **A**（改动小、风险低），若手感仍不一致再上 **B**。

**验收**
- 轻甩：滑一小段后自然停。
- 猛甩：能滑到边界并自然减速，不会"离手即停"。
- 到边界：不越界、不回弹过头。
- 与鸿蒙版同一手势对比，滑行距离与停下的时间接近。

---

## 顺带记录（不在这批里）
- 平板竖屏列宽自适应：鸿蒙已按安卓 `TabletLayout.computeWidths()` 实现（按可见列均分 → 语义 ×1.2/×0.8 → 夹 50–120dp → 整体贴合屏宽），安卓本身已有，无需改。
- 鸿蒙版已锁定竖屏（`module.json5 → orientation: portrait`），安卓是否也锁竖屏待确认。
