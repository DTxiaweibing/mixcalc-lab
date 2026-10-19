# 混料计算器 · 自绘混合引擎 改动文件清单

补丁文件：`mixcalc-table-engine.patch`（unified diff，从仓库根 `-p1` 应用）
统计：**11 个文件，新增 2049 行，删除 2373 行**

| # | 文件 | 类型 | 行数 | 说明 |
|---|------|------|------|------|
| 1 | `app/src/main/java/com/shrimpfarm/app/mixcalc/MixCalcActivity.java` | 修改 | +599 / -2283 | 重写主 Activity：删除旧的表头/表体/表尾三段滚动容器装配与全部滚动同步容器/监听代码；改为接线自绘引擎——实现 `CellTextProvider`（表头文案与状态、单元格文本与状态、可编辑判定、表尾文本与样式）、`OnCellTapListener`（表头单击/长按、正文单击/长按、表尾单击）、`OnCellEditListener`（提交、下一个）；新增 `commitCell()` 承接原 TextWatcher/焦点监听逻辑，新增 `applyColumnConfig()`/`createTableColumn()` 按列宽/可见/固定状态重建列配置。计算引擎、持久化、弹窗逻辑保持不变。 |
| 2 | `app/src/main/res/layout/activity_mix_calc.xml` | 修改 | +4 / -90 | 把原来的表头/表体/表尾三个独立滚动区域整块删除，替换为单个 `MixCalcTableView`（`layout_weight=1`）。标题栏、齿轮按钮、误差显示、底部导航不变。 |
| 3 | `app/src/main/java/com/shrimpfarm/app/mixcalc/table/MixCalcTableView.java` | 新增 | +716 | 自绘表格核心引擎（`ViewGroup`）。整表一个坐标系、两个偏移量（`offsetX`/`offsetY`）决定横滚/纵滚/冻结列/吸顶表头/吸底表尾；可见行列区间绘制（只画视口内）；`OverScroller` 惯性滑动；触摸判定（拖动、甩动、单击、长按）；提供 `setColumnConfig`/`setRows`/`setCellTextProvider`/`beginEdit`/`endEdit`/`getCellRect`/`isCellVisible`/`notifyAllChanged` 等对外接口。 |
| 4 | `app/src/main/java/com/shrimpfarm/app/mixcalc/table/CellEditorOverlay.java` | 新增 | +205 | 单元格编辑浮层：整表复用**一个** `EditText`，编辑时精确贴到命中格上并唤起原生输入法；文本变化实时回调提交（保留"边输边算"）；滚动时跟随移动，滚出可见区自动提交并收起；输入法"下一个"回调宿主跳格。 |
| 5 | `app/src/main/java/com/shrimpfarm/app/mixcalc/table/CellStyle.java` | 新增 | +188 | 集中画笔与颜色（构造期建好复用）。外观对齐原 drawable：普通格、禁用格、换料比例提示格、棚数超限警告格、表头发酵料四种状态、表尾蓝/洋红按钮。 |
| 6 | `app/src/main/java/com/shrimpfarm/app/mixcalc/table/TableMetrics.java` | 新增 | +133 | 表格几何计算：列宽数组、前缀坐标、固定列宽/滚动宽度、视口、最大滚动范围、行列与坐标互转、命中换算（`xOf`/`yOfRow`/`rowAt`/`visualIndex`/`visualIndexAt`）。 |
| 7 | `app/src/main/java/com/shrimpfarm/app/mixcalc/table/TableColumnConfig.java` | 新增 | +90 | 列的原始顺序、可见性与固定状态；`visual()` 输出绘制顺序（固定列在前、非固定列在后，各自保持原序），与原 `createFixedColumns`/`createScrollColumns` 挑列方式一致。 |
| 8 | `app/src/main/java/com/shrimpfarm/app/mixcalc/table/TableColumn.java` | 新增 | +40 | 列元数据：id、列宽(px)、是否可编辑、文本颜色、输入类型。 |
| 9 | `app/src/main/java/com/shrimpfarm/app/mixcalc/table/CellTextProvider.java` | 新增 | +31 | 宿主→引擎的内容/状态提供接口：表头文案与状态、单元格文本与状态、是否可编辑、表尾文本与样式。业务规则留在宿主。 |
| 10 | `app/src/main/java/com/shrimpfarm/app/mixcalc/table/OnCellTapListener.java` | 新增 | +26 | 命中回调接口：正文单击/长按（带格矩形，供弹窗锚点）、表头单击/长按、表尾单击。 |
| 11 | `app/src/main/java/com/shrimpfarm/app/mixcalc/table/OnCellEditListener.java` | 新增 | +17 | 编辑回调接口：`onCellCommitted(row, columnId, text)` 与 `onEditNext(row, columnId)`。 |

## 包级隔离

新增代码全部位于新包 `com.shrimpfarm.app.mixcalc.table`，与旧实现无交叉引用；宿主仅第 1、2 项两个文件被改动，其余业务文件（含 `model/FeedData.java`）一行未动。

## 应用方式

```bash
git apply mixcalc-table-engine.patch
```

或在 Android Studio 中通过 `Git → Apply Patch` 导入。
