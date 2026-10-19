# 计算器表格重构 · 最终方案

> 目标：把混料计算器的表格改成**自绘混合引擎**（自绘显示 + `EditText` 浮层输入），代码包级隔离。
> 状态：**本文件只是方案，尚未改动任何代码**。
> 涉及文件：
> - [MixCalcActivity.java](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java)（3818 行）
> - [activity_mix_calc.xml](file:///workspace/shrimpfarm/app/src/main/res/layout/activity_mix_calc.xml)（185 行）
> - [FeedData.java](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/model/FeedData.java)（计算引擎，**不改**）

---

## 一、要做什么（结论）

**用 1 个自绘 `ViewGroup` 取代现在的 32 个滚动容器，横、纵、冻结列、吸顶表头、吸底表尾全部由同一个坐标系里的偏移量决定。**

现在的表格是"32 个各自滚动的容器靠监听器互相同步"：
表头 1 个 + 表尾 1 个 + 30 行各 1 个 `HorizontalScrollView`（[activity_mix_calc.xml L77-L91 / L133-L147](file:///workspace/shrimpfarm/app/src/main/res/layout/activity_mix_calc.xml#L77-L147)、[buildScrollColumns L2618-L2684](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2618-L2684)）。

改造后：整个表格只剩下**一个 View**。

---

## 二、为什么（当前问题的根 + 为什么自绘能根治）

| 现象 | 根因 | 位置 |
|---|---|---|
| 左右拖动卡顿 | 每帧滚动回调对另外 31 个 `HorizontalScrollView` 各 `scrollTo` 一次，每次触发整块重绘 | [syncAllScrollViews L3531-L3550](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3531-L3550)、[onScrollChanged L3514-L3524](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3514-L3524) |
| 越用越卡 | `removeOnScrollChangedListener(null)` 是空操作，重建时旧监听器清不掉、累积 | [L2022](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2022) |
| `isSyncing` 失效 | 标志在同一次回调内即复位，`scrollTo` 引发的下一帧 dispatch 再触发一轮 | [L3515-L3522](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3515-L3522) |
| 快拖"橡皮筋" | 只有被触摸那行连续跟手，其余行每帧被离散 `scrollTo` 对齐 → 拖尾后跳变 | [L3488-L3550](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3488-L3550) |
| 拉到边回弹 | 源行到最大偏移后 `scrollX` 不再变化，触发不了同步，未对齐的行滞后补齐 | [L3518-L3521](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3518-L3521) |
| 边界处表头/数据错位 | 表头内容宽度被强制设为 `scrollWidth`，数据行按单元格自然宽度 measure，`maxScrollX` 可能不等 | [buildHeader L2100-L2105](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2100-L2105) |
| 斜向拖动纵向回弹 | 触摸仲裁是启发式（`deltaX>deltaY && deltaX>5`），外层 `ScrollView` 可能先抢到手势 | [L2628-L2661](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2628-L2661) |

> 代码里已到处 `setOverScrollMode(OVER_SCROLL_NEVER)`，系统原生拉伸已关，所以"橡皮筋"本质是**多滚动面不同步**的视觉产物。

**自绘为什么能根治**：不是"把 32 个容器同步得更好"，而是"**根本不存在多个容器**"。
整张表只有一个坐标系，横滚/纵滚/冻结列/吸顶/吸底全是同一个坐标系里的偏移问题：

| 需求 | 现结构怎么做 | 自绘怎么做 |
|---|---|---|
| 左右滚动时表头/表尾参与 | 表头/表尾各自的 `HorizontalScrollView` 要同步 | 表头/表尾本就画在横向坐标系里，只是 **y 被钉住** |
| 上下滚动时表头/表尾不动 | 放在纵向 `ScrollView` 之外 | **y 偏移不随 `scrollY` 变化** |
| 固定列冻结 | 每行左侧一个独立竖栈 | 该列绘制时 **x 偏移不随 `scrollX` 变化** |
| 同步 / 拖尾 / 回弹 | 1 主 + 31 从，还要 clamp | **不存在同步机制** → 结构上不可能发生 |
| 固定 / 解除固定 | 重新分区 + 整表重建（[rebuildCompleteTable L1975](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1975-L2005)） | 改配置 + `invalidate()` |
| 边界对齐错位 | 必须共用同一份宽度数组 | 只有一个坐标系，天然对齐 |
| 斜向拖动抢手势 | 启发式仲裁 | 横纵都由我们自己的 `onTouchEvent` 分发，没有外层容器来抢 |

---

## 三、目标形态

### 3.1 布局：三个区域合并成一个自绘 View

```
LinearLayout(vertical)
├─ 标题栏（不变：btn_column_settings / toolbar / tv_error_display）
├─ com.shrimpfarm.app.mixcalc.table.MixCalcTableView     ← 新增：全屏 weight=1
│      （表头 + 表体 + 表尾 全部由它绘制）
└─ bottom_nav（不变）
```

XML 里 [header_container L58-L92](file:///workspace/shrimpfarm/app/src/main/res/layout/activity_mix_calc.xml#L58-L92)、
[vertical_scroll_view L95-L111](file:///workspace/shrimpfarm/app/src/main/res/layout/activity_mix_calc.xml#L95-L111)、
[footer_container L114-L148](file:///workspace/shrimpfarm/app/src/main/res/layout/activity_mix_calc.xml#L114-L148)
**整块删除**，替换为一个 `MixCalcTableView`。

### 3.2 坐标系（绘制规则）

沿用现有语义：**固定列聚在最左，非固定列在右侧滚动区**（[createFixedColumns/buildScrollColumns](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2586-L2684) 就是按 `columnIds` 顺序这样挑列的）。

| 内容 | x | y |
|---|---|---|
| 固定列 c | 前面固定列宽度之和（**不含 `scrollX`**） | 表体：`headerHeight + row*rowHeight - scrollY` |
| 非固定列 c | `fixedWidth + 前面非固定列宽之和 - scrollX` | 同上 |
| 表头行 | 与列同上 | `0`（**不随 `scrollY`**） |
| 表尾行 | 与列同上 | `height - footerHeight`（**不随 `scrollY`**） |

绘制顺序：非固定区（clip 到 `x ≥ fixedWidth`）→ 固定列（clip 到 `x ≤ fixedWidth`，盖在滚动内容之上）→ 表头 → 表尾。

### 3.3 滚动规则

- 横向范围：`[0, nonFixedWidth - (viewWidth - fixedWidth)]`
- 纵向范围：`[0, TOTAL_ROWS*rowHeight - bodyHeight]`
- 一个 `OverScroller` 同时管两个方向；拖动/抛掷都在自己的 `onTouchEvent` 里。
- **没有外层 `ScrollView`**，所以没有跨容器同步，也没有抢手势。

---

## 四、文件清单

### 4.1 新增（新包 `com.shrimpfarm.app.mixcalc.table`）

| 文件 | 职责 |
|---|---|
| `TableColumn.java` | 列元数据：`id` / 标题 / 默认宽 / 是否可编辑 / 对齐 |
| `TableColumnConfig.java` | 列顺序 + 可见 + 固定（承接现有 `columnIds` / `columnVisibility` / `columnFixed`） |
| `TableMetrics.java` | 列宽数组、行高、`fixedWidth`、`contentWidth`、`getRowAt(y)` / `getColAt(x)` |
| `MixCalcTableView.java` | 核心 `ViewGroup`：`OverScroller` + 绘制 + 触摸 + 命中 |
| `CellEditorOverlay.java` | `EditText` 浮层：定位到命中格、提交回写、滚动跟随 |
| `CellStyle.java` | 画笔/颜色/字体/边框统一来源（避免 `onDraw` 内 new） |
| `OnCellTapListener.java` | 单击 / 长按 回调接口 |

路径统一为：
`app/src/main/java/com/shrimpfarm/app/mixcalc/table/<文件名>`

### 4.2 修改

| 文件 | 动作 |
|---|---|
| [activity_mix_calc.xml](file:///workspace/shrimpfarm/app/src/main/res/layout/activity_mix_calc.xml) | 三个表格区域（L58-L148）→ 一个 `MixCalcTableView` |
| [MixCalcActivity.java](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java) | 删除旧的表格装配与同步代码；把计算/持久化/弹窗接到新引擎（清单见第六节） |

### 4.3 不动

| 文件 / 对象 | 原因 |
|---|---|
| [FeedData.java](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/model/FeedData.java) | 计算引擎，一行不改 |
| `mixcalc/utils/ScreenUtils.java`、`BaseActivity`、`DatabaseHelper`、`R`、strings | 与表格结构无关 |
| 持久化 KEY（`KEY_FIXED_COLUMN` / `KEY_COLUMN_VISIBILITY` / `KEY_SAVED_DATA` …）与 JSON 格式 | 保证向后兼容、重启恢复 |
| 标题栏、`bottom_nav`、批次流程、其他模块 | 本期范围外 |

---

## 五、新增代码：逐类设计

### 5.1 `TableColumn`

```java
public class TableColumn {
    public final String id;            // 例 "shed_number"
    public final String title;         // 复用 initColumnNames() 的取值
    public final int defaultWidthDp;   // 复用 columnWidths[] 的取值
    public final boolean editable;     // 复用 isEditableColumn()
    public final int gravity;
}
```

### 5.2 `TableColumnConfig`

```java
public class TableColumnConfig {
    private final List<String> orderedIds;              // = 现有 columnIds 顺序
    private final Map<String, Boolean> visible;         // = 现有 columnVisibility
    private final Map<String, Boolean> fixed;           // = 现有 columnFixed

    public List<String> visualOrder();   // 固定列在前 + 非固定列在后，各自保序
    public boolean isVisible(String id);
    public boolean isFixed(String id);
}
```

> 现有 `initColumnVisibility/loadColumnVisibility/saveColumnVisibility`（[L1809-L1886](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1809-L1886)）继续用，只是把结果喂给 `TableColumnConfig`。

### 5.3 `TableMetrics`

```java
public class TableMetrics {
    int[] colWidths;      // 按 visualOrder 的宽度
    int rowHeight, headerHeight, footerHeight;
    int fixedWidth;       // 固定列总宽
    int nonFixedWidth;    // 非固定列总宽
    int contentWidth;     // 全部可见列总宽
    int xOfColumn(int visualIndex);
    int rowAt(float y), colAt(float x);   // 含冻结/吸顶偏移修正
}
```

> 宽度来源复用 [calculateAdaptiveColumnWidths L1565](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1565-L1624)（平板自适应）。

### 5.4 `MixCalcTableView`（核心）

```java
public class MixCalcTableView extends ViewGroup {

    void setColumns(List<TableColumn> cols, TableColumnConfig config);
    void setRows(List<FeedData> rows);
    void notifyCellChanged(int row, int col);   // 局部 invalidate(rect)
    void notifyAllChanged();
    void setOnCellTapListener(OnCellTapListener l);
    void beginEdit(int row, int col);           // 交给 CellEditorOverlay
    void scrollToX(int x);
    int  getScrollX();
    Rect getCellRect(int row, int col);         // 供弹窗做锚点
}
```

职责：
- `onMeasure`：宽度铺满；高度由父级 `weight=1` 给定。
- `onDraw`：按 3.2 的规则只绘制**可见行列区间**；表头/表尾 y 钉住。
- `onTouchEvent`：拖动 → `scrollBy`；抬手 → `fling`；长按/单击 → 命中 → 回调。
- `addView`：唯一的子 View 是 `CellEditorOverlay` 的 `EditText`。

### 5.5 `CellEditorOverlay`

```java
public class CellEditorOverlay {
    void begin(int row, int col);     // 定位 EditText + 聚焦 + 唤起输入法
    void onScrolled();                // 更新位置；滚出可见区则 commit + hide
    void commit();                    // 读文本 → 回写 FeedData → 回调宿主
    void hide();
}
```

- 复用**一个** `EditText` 实例，不再为每格创建。
- 提交（`TextWatcher` / 失焦 / 回车）→ 写回 `FeedData` → 回调 `MixCalcActivity`。
- 回车 → 跳到下一可编辑格（承接现有 [moveFocusToNextEditableCell L2941](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2941-L2995)）。

### 5.6 `CellStyle`

集中 `Paint` / 颜色 / 字体 / 边框 / 列分隔线，**全部在构造期建好并复用**。

---

## 六、修改 `MixCalcActivity`：逐项清单

### 6.1 删除（表格装配 + 同步机制，共 30 处）

| 方法 | 行 | 说明 |
|---|---|---|
| `buildTableFromScratch` | [2070](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2070) | 被 `engine.setColumns/setRows` 取代 |
| `buildHeader` / `buildFixedHeader` / `buildScrollHeader` / `createHeaderCell` | [2095](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2095) / [2112](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2112) / [2137](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2137) / [2160](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2160) | 表头改为 `onDraw` 绘制 |
| `buildFooter` / `buildFixedFooter` / `buildScrollFooter` | [2402](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2402) / [2419](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2419) / [2436](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2436) | 表尾改为 `onDraw` 绘制 |
| `buildDataRows` / `buildDataRow` / `buildFixedColumns` / `buildScrollColumns` | [2556](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2556) / [2566](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2566) / [2586](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2586) / [2618](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2618) | 行/列装配被自绘取代 |
| `createDataCell` | [2686](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2686) | **最核心的一处**，被自绘绘制 + 浮层编辑取代 |
| `buildEmptyTableWithoutListeners` / `buildEmptyRowWithoutListeners` / `createFixedColumnsWithoutListeners` / `createScrollColumnsWithoutListeners` / `createCellWithoutListeners` | [315](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L315) / [350](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L350) / [372](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L372) / [403](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L403) / [436](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L436) | 自绘无"空表装配" |
| `setupScrollSync` / `setupScrollViewListener` / `syncAllScrollViews` | [3488](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3488) / [3509](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3509) / [3531](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3531) | **卡顿与橡皮筋的源头**，整体删除 |
| `clearAllScrollListeners` / `clearAllCellListeners` / `cleanupAllViews` / `resetAllStates` | [2007](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2007) / [2031](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2031) / [2041](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2041) / [2056](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2056) | 不再有需要清理的子 View/监听器 |
| `replaceTextViewWithEditText` | [803](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L803) | 自绘里每个可编辑格不需要常驻 `EditText` |
| `checkAndLoadOtherColumnListeners` / `loadOtherColumnListenersIfNeeded` / `removeOtherColumnListeners` | [702](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L702) / [712](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L712) / [766](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L766) | 懒加载监听器机制整体删除（自绘天然按需） |
| `lazyInitializeListenersAndData` / `calculateScreenVisibleRows` | [494](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L494) / [269](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L269) | 同上 |
| `getTextWatchers` | [1735](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1735) | 反射取 `TextWatcher`，自绘不需要 |
| `updateFermentedHeaderBackground` / `updateFermentedHeaderInContainer` / `updateFermentedHeaderCellBackground` | [2354](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2354) / [2359](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2359) / [2377](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2377) | 改为按配置绘制表头底色 |
| `updateWaterHeader` / `updateWaterCells` | [3577](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3577) / [3586](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3586) | 改为 `notifyAllChanged()` |

### 6.2 替换（保留逻辑，换出口）

| 现状 | 行 | 改为 |
|---|---|---|
| `rebuildCompleteTable` | [1975](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1975) | `engine.setColumns(...)` + `notifyAllChanged()`（不再重建视图） |
| `refreshAllCells` / `refreshRowCells` | [1662](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1662) / [1675](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1675) | `engine.notifyAllChanged()` / `notifyCellChanged(row,col)` |
| `updateCalculatedCells` | [3213](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3213) | 只算数据 + `notifyRowChanged(row)` |
| `createStatCell` / `updateFooterCell` | [1389](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1389) / [2451](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2451) | 数据照算，绘制交给引擎 |
| `updateShedCountDisplay` / `updateRowInputEnabled` / `updateAverageCellEnabledState` / `refreshSwitchRatioIndicator` | [3198](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3198) / [3138](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3138) / [1102](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1102) / [3048](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3048) | 逻辑保留，产物变成"该格的绘制/可编辑状态" |
| `addShedListenersToRow` / `addShedTextWatcher` / `addOriginalTextWatcher` / `addAverageFeedTextWatcher` | [571](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L571) / [613](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L613) / [854](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L854) / [1006](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1006) | **迁移到 `CellEditorOverlay` 的提交回调**；核心算法（棚号区间解析、手动平均反向分摊、`isUpdatingAverageCell` 防递归）逐行保留 |
| `showColumnOperationMenu` / `handleColumnOperation` / `showAllColumns` / `unfixAllColumns` | [3376](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3376) / [3409](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3409) / [3444](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3444) / [3466](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3466) | 改 `TableColumnConfig` + `notifyAllChanged()` |
| `showColumnSettingsPopup` | [3677](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3677) | 交互不变，内部改配置 + 刷新 |
| `showSwitchRatioDrawer` | [3064](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3064) | 锚点由 `View` 改为 `engine.getCellRect(row,col)` |
| `initViews` | [1754](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1754) | 取得 `MixCalcTableView` 引用 + 注册回调 |
| `calculateScrollWidth` | [2084](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L2084) | 并入 `TableMetrics` |

### 6.3 保留不动（业务/计算/持久化/弹窗）

`onCreate`(176，仅接线部分改)、`getCurrentNavId`(243)、`initColumnNames`(247)、`loadQuickSettings`(256)、`getStatusBarHeight`(293)、`isTabletDevice`(301)、`hasOtherColumnData`(520)、`hasShedData`(531)、`loadAllDataInBackground`(536)、`getColumnIndex`(603)、`syncFeedCellsFromModel`(1079)、生命周期回调(1112/1118/1129/1138)、`checkShedCountLimitAndUpdateInput`(1150)、`formatDataToMessage`(1184)、`formatNumberWithPadding`(1305)、`formatWithPadding`(1316)、`buildHeaderWithExtraSpacing`(1340)、`buildDataRowWithSpacing`(1356)、`prepareDataAndNavigate`(1370)、`updateFooterRow`(1441)、`updateErrorDisplay`(1491)、`initTabletAdaptiveSettings`(1541)、`calculateAdaptiveColumnWidths`(1565)、`adjustTotalWidth`(1625)、`getScreenWidth`(1650)、`initData`(1795)、列可见/固定/水百分比/发酵料开关的读写(1809-1945)、`saveAllData`(1956)、`showStyledConfirmDialog70`(2210)、`showStyledListDialog`(2254)、`showFermentedCalculationDialog`(2279)、`getFilledRowCount`(2468)、`showDownloadDialog`(2479)、`resetAllData`(2529)、`isCellEnabled`(2996)、`isNonFermentedFeedColumn`(3006)、`getFeedValueByColumn`(3015)、`getLargerNonFermentedColumn`(3026)、`isSwitchRatioCell`(3039)、`updateAllCalculations`(3275)、`updateSeqStatView`(3293)、`getCellInitialValue`(3300)、`getCellDisplayValue`(3323)、`formatNumber`(3353)、`isEditableColumn`(3365)、`dpToPx`(3601)、`showResetConfirmationDialog`(3611)、`isBasicDataComplete`(3630)、`showNoBatchDialog`(3649)、`showBasicDataIncompleteDialog`(3662)、`updateSettingsRowStyles`(3799)、`updateToggleStyle`(3809)

### 6.4 删除的成员变量

`dataContent`(83)、`headerScrollContent`(84)、`footerScrollContent`(85)、`fixedHeaderContainer`(86)、`fixedFooterContainer`(87)、`headerScrollView`(88)、`footerScrollView`(89)、`verticalScrollView`(90)、`rowScrollViews`(94)、`fixedDataRows`(95)、`listenersAddedSet`(96)、`currentScrollX`(132)、`isSyncing`(135)、`lastX`(144)、`lastY`(144)、`isHorizontalScroll`(145)、`scrollListeners`(148)、`cellViewMap`(151)、`rowLayoutMap`(154)、`footerCellMap`(157)

### 6.5 新增的成员

```java
private MixCalcTableView table;
private TableColumnConfig tableConfig;
```

---

## 七、现有功能 → 新实现 对照表

| 现有功能 | 位置 | 新实现 |
|---|---|---|
| 11 列结构 | `columnIds`(117)、`columnWidths`(125) | `TableColumn` 列表 |
| 列显示/隐藏 | `columnVisibility`(114) | `TableColumnConfig.visible` |
| 列固定/解固定 | `columnFixed`(115) | `TableColumnConfig.fixed` |
| 30 行 / 行高 | `TOTAL_ROWS`(163) | `TableMetrics.rowHeight` |
| 表头 / 表尾统计 | `buildHeader` / `buildFooter` | `onDraw` 内绘制，y 钉住 |
| 可编辑列（8 个） | `isEditableColumn`(3365) | `TableColumn.editable` |
| 输入 / 焦点 / 防递归 | `addShedTextWatcher`(613) 等 | `CellEditorOverlay` 提交回调（算法原样迁移） |
| 手动平均反向分摊 + 焦点保持 | [FeedData.applyManualAverage](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/model/FeedData.java#L288-L343)、`isUpdatingAverageCell`(138) | 逻辑不改，`FeedData` 一行不动 |
| 长按换料比例抽屉 | `showSwitchRatioDrawer`(3064) | 命中回调 + `getCellRect` 锚点 |
| 懒加载监听器 | `loadOtherColumnListenersIfNeeded`(712) | **删除**；自绘天然按需 |
| 列设置弹窗 | `showColumnSettingsPopup`(3677) | 交互不变，内部改 `TableColumnConfig` + `invalidate()` |
| 水百分比 / 发酵料计入 | `showWaterPercentageDialog`(3552)、3 个 KEY | 只影响 `FeedData` 计算，绘制层无关 |
| 标题栏误差 | `updateErrorDisplay`(1491) | 不变 |
| 表尾统计刷新 | `updateFooterRow`(1441) | 数据照算，`notifyAllChanged()` 触发重绘 |
| 平板自适应列宽 | `calculateAdaptiveColumnWidths`(1565) | 产物喂给 `TableMetrics` |
| 数据持久化 | `saveAllData`(1956) / `loadAllDataInBackground`(536) | **完全不变** |
| 序号列统计 | `updateSeqStatView`(3293) | 逻辑保留，改绘制 |
| 斜向拖动抢手势 | `OnTouchListener`(2628-2661) | 删除；横纵由引擎 `onTouchEvent` 分发 |

---

## 八、施工顺序（一次改动的先后依赖）

> 不是分期交付，而是同一批改动的落地顺序；每步完成后可编译、可自查。

1. **建新包**：`table/` 下 7 个类，先实现"只读渲染"（绘制表头/表尾/数据行、冻结列、吸顶吸底、拖拽滚动）。
2. **改 XML**：L58-L148 三块替换为一个 `MixCalcTableView`。
3. **改 Activity 接线**：`initViews` 取引用；`initColumnVisibility/initColumnFixedState` 产物喂给 `TableColumnConfig`；`loadAllDataInBackground` 后 `setRows`。
4. **改计算刷新出口**：`updateAllCalculations`(3275) 末尾 `notifyAllChanged()`；`updateFooterRow` / `updateErrorDisplay` 照旧。
5. **删旧表格装配与同步**：第六节 6.1 / 6.4 清单。
6. **接编辑浮层**：迁移 6.2 里的三个 `TextWatcher` 逻辑到 `CellEditorOverlay` 提交回调；接 `moveFocusToNextEditableCell`。
7. **接列设置 / 长按抽屉**：`handleColumnOperation` 改配置 + 刷新；长按锚点改 `getCellRect`。
8. **回归与清理**：核对第七节对照表逐项通过，删除临时开关。

> 过渡期可保留 `useSelfDrawnTable` 开关与新引擎并存，验证通过后再删旧路径——这样"失败也不怕"。实验放在分支 `refactor/mixcalc-table`，`main` 不动。

---

## 九、风险与对策

| # | 高危点 | 位置 | 对策 |
|---|---|---|---|
| 1 | 可编辑单元格：输入法 / 焦点 / 光标 | `createDataCell`(2686) | **保留原生 `EditText`**，只把它从"每格常驻"改成"整表一个浮层"，输入方式不变 |
| 2 | 手动平均反向分摊 + 焦点保持 + 防递归 | [FeedData.applyManualAverage](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/model/FeedData.java#L288-L343)、`isUpdatingAverageCell`(138) | **逻辑一行不改**；回归用例覆盖"手动平均后焦点不乱跳、不递归" |
| 3 | 棚号区间解析 → 棚数 | `addShedTextWatcher`(613) | 算法原样迁移到提交回调 |
| 4 | 列固定/隐藏的存储兼容 | `saveColumnFixedState`(1872)、`KEY_FIXED_COLUMN`(73) | KEY 与格式不变；重启后仍能恢复；`seq` 恒固定([L1821](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L1821)) |
| 5 | 表尾统计 + 标题误差联动 | `updateFooterRow`(1441)、`updateErrorDisplay`(1491) | 刷新入口不变 |
| 6 | 长按换料比例抽屉锚点 | `showSwitchRatioDrawer`(3064) | 传入命中格矩形 |
| 7 | 持久化时机（`updateAllCalculations` 末尾即 `saveAllData`） | [L3275-L3292](file:///workspace/shrimpfarm/app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java#L3275-L3292) | 时机与 JSON 结构不变 |
| 8 | 无障碍（自绘对 TalkBack 不友好） | — | 本期暂不做，后续用 `ExploreByTouchHelper` 补 |
| 9 | `onDraw` 内分配对象导致卡顿 | — | 见第十节红线 |

---

## 十、`onDraw` 纪律（性能红线）

- 不在 `onDraw` 内 `new` 任何对象 / `new Paint`；`Paint`、`Rect`、`StringBuilder`、`char[]` 全部复用。
- 文本宽度 `measureText` 结果按格子缓存。
- 只遍历可见行列区间（`firstVisibleRow..lastVisibleRow`、`firstVisibleCol..lastVisibleCol`）。
- 局部刷新用 `invalidate(Rect)`，不要全屏 `invalidate()`。

---

## 十一、验证清单（真机，Android Studio 构建）

> 本机（沙箱）没有 Android 构建环境与模拟器，**代码由我编写，编译与实测必须由你在 Android Studio / 真机完成**。

1. 输入棚号 `1-5,7` → 棚数正确解析；超上限变红。
2. 手动改"平均吃料" → 非发酵料按规则反向分摊，**焦点不跳、不递归**。
3. 两种非发酵料时 → 长按料格弹"换料比例"；切到 3 种料比例自动作废。
4. 切换水百分比 → 水列、称料、表尾统计、标题误差同步更新。
5. 列固定 / 取消固定 / 隐藏 / 显示 → 数据不丢、列序正确、平板模式正常。
6. 30 行横向拖到两端 + 快速拖动 → **无卡顿、无拖尾、无回弹、表头/数据行始终对齐**。
7. 纵向滚动 → 表头/表尾**不动**；横向滚动 → 表头/表尾**跟随**；固定列**冻结**。
8. 斜向拖动 → 不发生纵向抢手势。
9. 杀进程重启 → 按批次恢复全部数据。
10. 平板（`sw600dp`）下布局与交互正常。
11. 大表压力（后续可扩到更多行/列）→ 掉帧率不随行数增长。

---

## 十二、明确不动

- `FeedData` 计算引擎、持久化 JSON 结构与保存时机。
- 标题栏、`bottom_nav`、批次流程、其他模块。
- 列设置的**交互形式**（弹窗内容与操作方式不变，只换底层实现）。
