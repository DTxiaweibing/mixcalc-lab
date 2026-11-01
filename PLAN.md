# 秒算虾料 · 安卓版下一步完整开发计划

> 当前版本：`0.3.4` / `versionCode 7`
> 当前分支：`master`；Gitee / GitHub 两个远端保持同步（以 `git log -1` 为准）
> 已完成的旧三项对齐记录：[PLAN-与鸿蒙对齐-已完成.md](PLAN-与鸿蒙对齐-已完成.md)
>
> **这份就是安卓版的完整下一步计划，不是“来源说明”。**
> 结构与鸿蒙版 `PLAN.md` 的 P0 / P1 / P2 / P3 逐项对应，并补充了安卓的落点、现状和验收标准；以后只看这一份即可。

---

## 一、本轮要解决的 5 件事

| 优先级 | 事项 | 影响 |
|---|---|---|
| P0 | 快捷设置随窗口自适应、11 列铺满时隐藏 | 双折叠展开后仍出现「快捷设置」，和 11 列布局冲突 |
| P1 | 表头弹窗右边缘对齐单元格右边缘 | 靠右列弹窗往右溢出，点完不好操作 |
| P1.5 | 键盘自适应：竖屏系统键盘，横屏/矮窗口两行自定义键盘 | **核心**：横屏系统键盘占掉太多高度，表格只剩 2～3 行 |
| P2 | 棚号输入改成“棚号 ID 归属”模型 | 解决重号、越界、抽错行；改动最大，单独排期 |
| P3 | 回归、版本号、更新说明 | 保证两版行为一致 |

P0/P1 是小改，可以先出 **0.3.5**；P1.5 是核心交互改造，单独出 **0.4.0**；P2 是数据模型级改动，出 **0.5.0**，不要混版本。

---

## 二、P0 快捷设置：窗口自适应 + 双折叠隐藏

### 现状

- `MixCalcActivity.showColumnSettingsPopup()` 里：
  ```java
  int maxWidth = (int) (360 * dm.density);
  popup.setWidth(Math.min(dm.widthPixels, maxWidth));
  ```
  宽度上限 360dp，右侧永远留白。
- `isTabletMode` 在页面初始化时算一次；华为双折叠展开后，表格已经按新宽度铺满 11 列，但 `isTabletMode` 还是折叠时的值，所以菜单里仍有「快捷设置」。
- 菜单弹窗创建时才判断一次 `if (isTabletMode) GONE`，展开/折叠后不会刷新。

### 目标

1. 快捷设置面板左右铺满当前窗口，窗口变宽/变窄都跟着变；
2. 只要表格已经铺满 11 列，菜单里就不出现「快捷设置」；
3. 手机、平板、折叠态/展开态都一致；
4. 已经打开的菜单在转屏/折叠变化时关闭或刷新，不能停留在旧状态。

### 做法

- 监听 `onConfigurationChanged` / `onMultiWindowModeChanged`（必要时 `View.onSizeChanged`），重新计算：
  ```java
  int widthDp = getResources().getConfiguration().screenWidthDp;
  boolean elevenColumns = widthDp >= 600; // 与 TabletLayout 的实际分列阈值保持一致
  ```
- 快捷设置面板宽度改为 `MATCH_PARENT` 或 `screenWidth - 左右安全边距`，不再用 360dp 上限；
- 菜单每次打开时重新判断 `elevenColumns`，不要只在 `onCreate` 算一次；
- 如果表格已经 11 列，`menu_item_quick_settings.setVisibility(View.GONE)`；
- 折叠/展开时若菜单正开着，直接 `dismiss()`，让用户重新打开，避免旧菜单残留。

### 验收

- 折叠态：手机布局，快捷设置可显示，面板左右满宽；
- 展开态：11 列铺满，菜单无「快捷设置」；
- 平板横/竖屏：面板满宽，菜单无「快捷设置」；
- 反复折叠/展开 5 次，不出现“列已铺满但快捷设置还在”；
- 快捷设置里 9 列、四个开关、底部四个按钮都正常，不被屏幕边缘裁切。

### V1.2 文档核对后补充（2026-11-01）

- **Manifest 补 `configChanges`**：`orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|navigation|uiMode|layoutDirection`，避免分屏/折叠时 Activity 重建、状态丢失；
- **删掉 `loadColumnVisibility()` 里的 `forceVisible = isTabletDevice(this)`**：现在平板会强制把用户手动隐藏的列恢复，窄窗口下“隐藏列”这条退路失效；
- **600dp 依据**：11 列固定宽 = 8×60 + 3×40 = 600dp；`W >= 600` 就是“不横滑能塞下全部 11 列”，和列宽算法使用同一个阈值；
- **极矮窗口只读闸门**：可用高度不足 1 行时不要强行编辑，避免表格被压成 0 高；
- **采纳**“SYSTEM_NUM / CUSTOM_PAD / READONLY 三档键盘”思路，但按当前窗口实时尺寸重写判据，单独立项为 **P1.5**；不照抄文档里被截断的公式；
- **暂不重写**列宽公式/统计行独立 View：先保持当前稳定实现，P0/P1 后再单独评估；
- 文档本身有多处公式被截断（如 `W = 600)`、`W  1200`、`W = 5`），不能当逐字规格；只采纳上面这些能落地、能验收的条目；
- 统计行先做一次横滚验证：若确实会跟着滚走，再用 `offsetX` 补偿/固定绘制做小修，不按文档重写整个表尾。

---

## 三、P1 表头弹窗：右边缘对齐

### 现状

`MixCalcActivity.showAnchoredListDialog()`：

```java
int x = loc[0] + (cellRect != null ? cellRect.left : 0);
int y = loc[1] + (cellRect != null ? cellRect.bottom : 0);
popup.showAtLocation(table, Gravity.NO_GRAVITY, x, y);
```

- 弹窗左边缘贴单元格左边缘；
- 列菜单/水比/发酵料弹窗宽约 180dp，靠右的列会向右溢出；
- 手指点完表头，弹窗主体在右边，反而不方便。

### 目标

- 弹窗右边缘 = 被点表头单元格右边缘；
- 靠左的列自动夹到屏幕左边（x=0），靠右的列不超出屏幕；
- 水比、发酵料、列操作三个弹窗统一规则。

### 做法

```java
int popupWidth = ...;                 // 弹窗实际宽度，吸附前先测量
int cellRight = loc[0] + cellRect.right;
int x = cellRight - popupWidth;
x = Math.max(0, Math.min(x, dm.widthPixels - popupWidth));
```

- `createListPopup()` 目前宽度是 `Math.min(dm.widthPixels, dpToPx(180))`，可先固定 180dp 计算，或 `popup.getWidth()` 后再定位；
- 列设置弹窗是另一套 `showColumnSettingsPopup()`，如果也贴表头，同样按右边缘对齐；
- 纵向仍用 `cellRect.bottom`，不改变“弹窗在单元格下方”的习惯。

### 验收

- 最左列表头：弹窗贴左，不裁切；
- 最右列表头：弹窗右边缘贴单元格右边缘，完整可见；
- 中间列：右边缘对齐，点完能直接选；
- 横屏/竖屏、手机/平板、折叠/展开都试一遍。

---

## 四、P1.5 键盘自适应（核心，0.4.0）

> 目标：**竖屏/高度够就用系统键盘；横屏/矮窗口用两行自定义键盘**，把表格可见面积尽量留出来。
> 参考测算页：<https://workspace-cdn.uzone.com/f89e50275c7d4be6bc5061278f136d3d%2Fo%2F1793479906780.html?auth_key=1825017360-0-0-6de9a08da46c9db53001ea713fcec2ce>
> 说明：测算页的 barsTop / titleBar / 键盘单行高 / 边距必须用真机实测值替换，不能直接拿演示尺寸上线。

### 为什么值得单独做

- 横屏时系统键盘占掉约 250dp，表格只剩 2～3 行；
- 两行自定义面板约 116dp，可多出 3～4 行；
- 这是 V1.2 文档真正的核心。如果不改键盘，当前 `adjustResize` + 系统键盘已经能跑，其他大改都不急。

### 三档模式

- `SYSTEM_NUM`：系统数字键盘，竖屏/高度足够，弹出后仍能留出至少 5 行；
- `CUSTOM_PAD`：两行自定义面板，横屏/矮窗口，或系统键盘不足 5 行但自定义面板能留出至少 1 行；
- `READONLY`：只读降级，连 1 行都留不出（如 1100×250），不要强行编辑。

### 判据（按实时窗口，不按设备类型）

```java
int W = Math.round(mainLayout.getWidth() / dm.density);
int H = Math.round(mainLayout.getHeight() / dm.density);
int fixed = barsTop + titleBarH + headerH + footerH;
int rowsSys = (H - fixed - systemKeyboardH - nextRowOverlayH) / rowH;
int rowsCustom = (H - fixed - customPadH) / rowH;

boolean preferSystem = (W <= H) && rowsSys >= 5;  // 竖屏且系统键盘够用
String mode = preferSystem ? "SYSTEM_NUM"
            : (rowsCustom >= 1) ? "CUSTOM_PAD"
            : "READONLY";
```

- `systemKeyboardH` 用 `WindowInsets` / `getWindowVisibleDisplayFrame` 实测，不能写死；
- `customPadH` 以 116dp 为基准，按两行键高 + 内边距算；
- `nextRowOverlayH` 约 34dp，只在 `SYSTEM_NUM` 保留；
- 每次尺寸变化、折叠/展开、分屏都重算；跨档前先 `table.endEdit()`，避免两套键盘同时出现。

### 自定义面板布局（两行，约 116dp）

- 第一排：`1 2 3 4 5 6 7 8 9 ⌵`；
- 第二排：`0 - . 下一行 空格 清空 下一步 退格`；
- 权重按屏宽分 10 份，第二排合计 10 份；
- `空格`必须保留：`1 17 18` 这种不连续棚号要靠空格；
- `清空` = 还原到进入编辑前的值，不是清零（避开换料合并）；
- 棚号列长按 `-` 弹出分隔符选择：`-` / `、` / `到` / `至` / `号`；
- 数字列禁用 `.`，棚号列不禁用；
- 光标位置插入/删除，不是只在末尾追加；
- `CUSTOM_PAD` 隐藏「下一行 ▾」浮层（面板里有「下一行」键），`SYSTEM_NUM` 保留。

### Android 落点

- `MixCalcActivity.updateAdaptiveLayout()`：算 W/H、三档、跨档 `endEdit()`；
- `applyKeyboardMode()`：面板显隐、`setShowSoftInputOnFocus(false)`、`imm.hideSoftInputFromWindow(...)`、浮层显隐；
- `CellEditorOverlay`：
  - `setText()` 前保持现有白名单；
  - 自定义键走 `editText.getText().insert/delete` + `setSelection`；
  - `清空` 还原 `editBegin` 快照，不直接置空；
  - 数字列/棚号列的 `.` 键动态禁用；
- `MixCalcTableView`：`CUSTOM_PAD` 时把「下一行 ▾」隐藏，避免两个入口；
- `AndroidManifest.xml`：P0 的 `configChanges` 必须先补，否则折叠时 Activity 重建会打断模式切换。

### 验收矩阵

- 360×780 竖屏：`SYSTEM_NUM`，键盘弹出后至少 5 行；
- 780×360 横屏：`CUSTOM_PAD`，至少 2 行；
- 800×1200 平板竖屏：`SYSTEM_NUM`，至少 5 行；
- 1200×800 平板横屏：`CUSTOM_PAD`，比系统键盘多 3～4 行；
- 360×600 小屏竖屏：`CUSTOM_PAD`（系统键盘不够 5 行）；
- 800×380 上下分屏：`CUSTOM_PAD`，至少 2 行；
- 1100×250 三分屏：`READONLY`，不出现 0 高表格；
- 双折叠折叠↔展开：不闪键盘、不丢当前输入；
- 小艺/搜狗/Gboard：`SYSTEM_NUM` 都能输入；`CUSTOM_PAD` 系统键盘不弹出；
- 光标中间插入/删除、清空还原、下一行/下一步顺序规则都正常。

### 工作量

- 单平台约 4～6 天（判据 + 面板 + 光标/键位 + 折叠切换 + 回归）；
- 单独出 0.4.0，不和 P0/P1 小改混版本。

---

## 五、P2 棚号智能输入（豆包方案评估）

> 参考：<https://www.doubao.com/thread/ac6792d547957>
> 结论：**方向对，但不能照抄**；它解决“重号、越界、抽错行”，但有 4 个细节要补。

### 4.1 现状

- `FeedData` 以 `shedNumber` 文本 + `shedCount` 数字为准；
- 第 1 行用 `shedCountBase / shedCountBaseSum` 做“基数 − 后续行”的锚点；
- 后面行只改自己的文本/棚数，不知道某个棚号当前归哪一行；
- 所以 `1-10` + `5-8` 会重复计 14 棚，后面的行可以写池外号码，编辑前面的行也不会把号码从后面行搬走。

### 4.2 豆包方案的核心

1. 第 1 行定义全局棚号池 `globalPool`，只有第 1 行能产生新棚号；
2. 第 2～30 行只能从池内、且已经被前面行占用的号码里“搬运/拆分”；
3. 输入先完整解析成 ID 数组；任何 0、负数、越界、未分配号码 → **整组拒绝**，不截断、不自动过滤；
4. `FeedData` 增加 `shedIds: number[]`，本行棚数 = `shedIds.length`；
5. `Batch` 保存 `totalShedPool / minShed / maxShed`；
6. 移除 `shedCountBase / shedCountBaseSum`。

### 4.3 必须修正/补充的 4 点

1. **“只能从前面行抽取”太死**
   - 第 3 行已拿 24-26，用户回头编辑第 2 行想拿 24-26，按“只看前面行”会被判“未分配”。
   - 建议允许从**任意其他行**搬，弹确认“会修改第 3 行”，而不是强迫用户先清空第 3 行。
   - 如果坚持只允许从上往下，必须明确提示“请先清空后面的行”。

2. **第一行“有后续数据就禁止编辑”太硬**
   - 加棚（1-40 → 1-50）是常见操作，强制重置会丢数据。
   - MVP 可先按豆包的“锁死 + 提示重置”做，简单安全；
   - 下一版建议支持“只扩容”：新号码只留在第 1 行，已有分配不动；缩容/改动已分配号码才拒绝或要求确认。

3. **“棚数 = shedIds.length”会和现在的“棚数可手改”冲突**
   - 现在用户可以手动改棚数，不按棚号算；新模型下棚数由 ID 集合决定，手改就没有意义。
   - 需要产品决策：要么取消手改（推荐，规则最干净），要么保留“手改覆盖”但会破坏 ID 归属，不建议。

4. **重复号码必须明确处理**
   - 豆包方案没写重复；`1-10,5-8` 现在会算 14 棚。
   - 新模型应**拒绝重复**并提示“5-8 与 1-10 重复”，不能默默去重，否则用户以为 14 棚、实际只有 10 棚。

### 4.4 建议的数据模型

- `FeedData.shedIds: List<Integer>`：本行真实持有的棚号；
- `shedNumber` 只做展示，由 `shedIds` 用 `compressShedIds()` 生成（如 `1-14,29-40`）；
- `Batch.totalShedPool: List<Integer>`、`minShed`、`maxShed`：第 1 行定义的总池；
- `Batch.excludedShedIds: List<Integer>`：本批次已剔除、任何行都不能再抽到的号码；
- `shedCount` 改为派生值（`shedIds.size()`），不再持久化独立值；
- 旧字段 `shedCountBase / shedCountBaseSum` 只保留在迁移代码里，新逻辑不再使用。

### 4.5 棚号池查看与剔除（新增，用户 2026-10-31 提出）

**需求**
- 长按任意一行的「棚号」单元格，弹出这一行的号码池，不进入编辑、不弹键盘；
- 池里显示本行当前到底有哪些棚号，显示“本行 N 个”；
- 可多选/全选，点“剔除所选”把卖掉、不吃料、出问题的棚号移出本批次；
- 剔除后，本行棚数立即减少；**下面任何行再输入/抽取，都不能再出现这些号码**。

**语义**
- **剔除 = 本批次永久移出**，加入 `Batch.excludedShedIds`，不回到第 1 行；
- 第 1 行池 = `totalShedPool - excludedShedIds - 其他行已占用`；其他行池 = 自己的 `shedIds`；
- 三者的并集 = 总池，且互不重叠；任何输入里出现已剔除号码 → 整组拒绝，提示“X 已剔除”；
- 剔除前弹确认：“剔除后本批次不再参与计算，下面行也不能再抽到。确定？”；
- 剔除后本行可以为空（棚数 0），但保留行，不自动删行、不清其他列。

**UI 建议**
- 复用 `dialog_simple_confirm` 风格，用可滚动 `GridLayout`/`ListView` 显示号码块；
- 底部固定“全选 / 取消全选 / 剔除所选 / 关闭”，显示“已选 N 个”；
- `MixCalcTableView` 里给「棚号」列加 `OnLongClickListener`，长按不触发 `CellEditorOverlay`；
- MVP 先只显示本行号码；后续可加“本行 / 其他行 / 已剔除”三种状态的全局视图。

**恢复规则**
- MVP：剔除不可恢复；要恢复请重置表格或换新批次；
- 后续可在池面板加“恢复”，默认回第 1 行；恢复前输入里出现已剔除号码仍然拒绝。

### 4.6 分阶段落地

- **P2.1 数据层**：解析文本 → `List<Integer>`；`compressShedIds()`；`FeedData.shedIds`；`Batch.excludedShedIds`；旧数据迁移（先备份原始 JSON，重复/重叠时提示，不静默改数）；
- **P2.2 归属转移**：每行一个 `Set<Integer>` 所有权表；输入先校验，再从原行移除、加到当前行；跨行修改弹确认；更新第 1 行显示；
- **P2.3 产品规则**：第一行锁死/只扩容二选一；手改棚数取消或保留；错误文案、Toast、帮助页同步；
- **P2.4 棚号池**：长按棚号格 → 池面板 → 多选剔除 → 更新本行/总池/排除表；确认弹窗、批次切换持久化；
- **P2.5 回归**：下面用例全过，再考虑合并。

### 4.7 关键验收用例（总池 1-40）

- 第 2 行输入 `41` → 拒绝，提示超出 1-40；
- 第 2 行输入 `0` / `-5` → 拒绝；
- 第 2 行输入 `38-42` → 整组拒绝，不能偷偷截成 38-40；
- 第 2 行输入 `15-28` → 通过，从第 1 行移走，第 1 行显示剩余；
- 第 3 行输入 `24-26` → 通过，从第 2 行移走，提示“已修改第 2 行”；
- 第 2 行回头输入 `24-26`（第 3 行持有）→ 允许 + 确认，或明确提示先清第 3 行（按最终规则）；
- 输入 `1-10,5-8` → 拒绝重复，提示重复段；
- 第 1 行有后续数据时改总池 → 锁死并提示重置（MVP）；
- 杀进程重开、批次 01/02 切换后，ID 归属和显示一致；
- 旧版本数据升级：不丢行、不重复、不越界；异常数据有提示。

**棚号池/剔除用例**
- 第 1 行 1-50，长按第 1 行棚号池 → 能看到 1-50，显示“本行 50 个”；
- 选中 12、18 → 确认剔除 → 第 1 行变 48 个，12、18 进入已剔除；
- 第 2 行输入 `12` → 拒绝“12 已剔除”；
- 第 2 行输入 `10-15` → 整组拒绝，因为包含已剔除的 12；
- 第 2 行持有 15-20，长按第 2 行池 → 选中 18 剔除 → 第 2 行变 5 个，第 1 行数量不变，后续行不能再抽 18；
- 长按池面板可滚动、全选、取消、关闭；剔除确认框能取消且不改数据；
- 剔除后杀进程/切批次再回来，已剔除号码、各行数量、显示都不变；
- 长按棚号格不会打开键盘，也不会误触发行编辑；
- 第 1 行/第 30 行、横屏/竖屏、手机/平板都能正常弹出和滚动。

### 4.8 工作量

- 单平台约 3～5 天（数据层 + 迁移 + 归属转移 + 棚号池/剔除 + 回归）；
- 不建议和 P0/P1 同一个版本发；先做 P0/P1/P1.5，再单独开 P2 分支。

---

## 六、P3 回归与发布

- P0/P1 完成后：手机 + 平板 + 双折叠展开/折叠各回归一次；
- P1.5 完成后：按键盘三档矩阵回归（见 P1.5 验收）；
- P2 完成后：用上面用例 + 现有顺序录入/批次/语言/键盘回归；
- 版本号：P0/P1 可出 `0.3.5`；P1.5 单独出 `0.4.0`；P2 单独出 `0.5.0`；
- 每次更新同步 `CHANGELOG.md` 和应用市场“用户版更新说明”；
- 本次 0.3.4 的用户版更新说明已写在：
  [docs/应用市场更新说明-0.3.4.md](docs/应用市场更新说明-0.3.4.md)。

### 6.1 数据安全 / 保存节流（建议，可跟 P1 一起做）

- `afterTextChanged` 每敲一个字符就 `commitCell → applyRowChange → saveAllData`；自绘键盘连按时会每键一次全表重绘 + SharedPreferences 落盘。改为：计算照旧，但保存挂到 `endEdit` / `onPause` / `switchBatch`，或加 200ms 防抖；
- `MixCalcPrefs` 增加主文件 + `_bak` 双写：主文件解析失败时回退备份，避免批次数据静默清空；
- 这一项不改交互，只做性能和容错，适合在 P0/P1 之后、P1.5/P2 之前顺手做。

---

## 七、建议执行顺序

1. **P0 快捷设置**（半天～1 天）：改宽度、监听窗口变化、统一 11 列判断、补 `configChanges`、去掉 `forceVisible`；
2. **P1 表头弹窗**（半天）：右边缘对齐 + 边界夹紧；
3. 出 `0.3.5` 回归手机/平板/双折叠；顺手做 6.1 的保存节流/备份；
4. **P1.5 键盘自适应**（单独分支，4～6 天）：先判据/只读闸门，再自定义面板和光标键位；
5. 出 `0.4.0`，跑键盘三档矩阵；
6. **P2 棚号模型**（单独分支，3～5 天）：先数据层和迁移，再归属转移，最后棚号池/文案；
7. 出 `0.5.0`，用 4.7 的用例做完整回归。

---

## 八、与鸿蒙版 PLAN 的逐项对应

| 鸿蒙版 `PLAN.md` | 安卓版本文件 | 说明 |
|---|---|---|
| P0 快捷设置窗口自适应 + 双折叠隐藏 | 二、P0 | 鸿蒙监听 `windowSizeChange`；安卓监听 `onConfigurationChanged` / `onMultiWindowModeChanged`，统一按当前是否 11 列判断 |
| P1 表头弹窗右边缘对齐 | 三、P1 | 鸿蒙改 `colMenuX/pickX`；安卓改 `showAnchoredListDialog()` 的 `x`，公式相同：`cellRight - popupWidth` |
| P1.5 键盘自适应 | 四、P1.5 | 同样是 SYSTEM_NUM / CUSTOM_PAD / READONLY 三档，判据、布局、验收一一对应；安卓落点 `MixCalcActivity` / `CellEditorOverlay` / `MixCalcTableView` |
| P2 棚号智能输入（豆包方案评估 + 棚号池） | 五、P2 | 数据模型、4 点修正、P2.1～P2.5（含长按号码池/剔除）、验收用例一一对应；安卓落点是 `FeedData.java` / `Batch.java` / `MixCalcPrefs.java` / `MixCalcTableView.java` |
| P3 回归与发布 | 六、P3 | 版本节奏一致：P0/P1 出 0.3.5，P1.5 出 0.4.0，P2 出 0.5.0 |
| 建议执行顺序 | 七、建议执行顺序 | 先小改、再键盘、最后数据模型，避免混版本 |

> 结论：安卓这份不是“只有一句来源”，而是和鸿蒙版同结构、同验收标准的完整计划；旧的三项记录已改名为 `PLAN-与鸿蒙对齐-已完成.md`，避免再混淆。
