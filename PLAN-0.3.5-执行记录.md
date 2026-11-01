# 0.3.5 执行记录（P0 快捷设置 / P1 表头弹窗）

> 计划来源：[PLAN.md](PLAN.md) 第二节 P0、第三节 P1。
> 本文件只记“做了什么、怎么验证的、还差什么”，不改计划本身。
> 每一步：先改代码 → 再跑验证 → 把命令和结果写回这里。

_开始：2026-11-01_

---

## P0 快捷设置：窗口自适应 + 铺满 11 列时隐藏

### 改了什么

| 文件 | 改动 |
|---|---|
| `app/src/main/AndroidManifest.xml` | `MixCalcActivity` 补 `configChanges="orientation\|screenSize\|smallestScreenSize\|screenLayout\|density\|keyboard\|keyboardHidden\|navigation\|uiMode\|layoutDirection"`，折叠/分屏/转屏不再重建 Activity |
| `MixCalcActivity.onConfigurationChanged()` | 改为统一走 `onWindowMetricsChanged()` |
| `MixCalcActivity.onMultiWindowModeChanged()` | 新增，同上（分屏/多窗口切换） |
| `MixCalcActivity.onWindowMetricsChanged()` | 重算 `isTabletMode` → 重算列宽 → 关掉旧菜单 → 重建表格 |
| `MixCalcActivity.dismissOpenPopups()` | 主菜单、列设置弹窗在窗口变化时直接关闭，避免停留在旧宽度 |
| `MixCalcActivity.installWindowSizeWatcher()` | 主容器宽度变化兜底（部分设备改窗口大小不触发 `onConfigurationChanged`）；只在宽度真的变了才重算，首次布局只记录 |
| `MixCalcActivity.configureQuickSettingsItem()` | 菜单每次打开时按**当前窗口**判断，不再用 `onCreate` 时算的 `isTabletMode` |
| `MixCalcActivity.isElevenColumnsFilled()` | 判据：11 列全可见 **且** 自然总宽放得下 → 菜单隐藏「快捷设置」 |
| `MixCalcActivity.loadColumnVisibility()` | 删掉 `forceVisible = isTabletDevice(this)`，平板不再强制恢复用户手动隐藏的列 |
| `MixCalcActivity.showColumnSettingsPopup()` | 宽度改为 `PopupGeometry.fullWidth(当前窗口宽)`，去掉 360dp 上限 |
| `TabletLayout.fillsAllColumns()` | 新增纯计算：全可见 + 总宽 ≤ 窗口宽；平板布局按 `computeWidths()` 结果判断 |
| `mixcalc/ui/PopupGeometry.java` | 新增：`fullWidth()`、`rightAlignedX()`（纯计算，无 Android 依赖） |

### 关键判断

- **判据不用 `widthDp >= 600`**：600dp 只是设计基线，手机固定列宽合计 600dp，展开后窗口 700dp 左右才真的放得下；按实际列宽算才不会“还没铺满就把快捷设置藏了”。
- **有列被隐藏时一定保留「快捷设置」**：否则用户没有入口把列恢复回来（这是删掉 `forceVisible` 后的必要配套）。
- **平板仍然隐藏快捷设置**：平板 `computeWidths()` 会把 11 列压进屏宽，判据天然成立。

### 验证

```
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline
→ BUILD SUCCESSFUL in 1m 13s
→ TabletLayoutTest: tests=5 failures=0 errors=0
→ PopupGeometryTest: tests=5 failures=0 errors=0
```

新增单测 `app/src/test/java/com/shrimpfarm/app/mixcalc/table/TabletLayoutTest.java`：

| 用例 | 期望 |
|---|---|
| 手机 360dp（1080px / density 3） | 铺不满 → 保留快捷设置 |
| 展开/大屏 700dp（2100px） | 铺满 → 隐藏快捷设置 |
| 藏掉任意一列 | 一定保留快捷设置 |
| 平板布局 | 总是铺满 |
| 空数组 / 窗口宽 0 | 不崩，按“没铺满”处理 |

**测试有效性检查**：把 `fillsAllColumns()` 的 `return total <= screenWidthPx` 临时改成 `return true`，`phoneNarrowWindowNeedsQuickSettings` 立刻失败；改回后 5/5 通过。说明用例真的在测这段逻辑，不是空跑。

### 还差什么（真机，未在本机验证）

本机没有设备/模拟器，以下只能人工过一遍（PLAN.md P0 验收）：

- [ ] 折叠态：手机布局，快捷设置可显示，面板左右满宽；
- [ ] 展开态：11 列铺满，菜单无「快捷设置」；
- [ ] 平板横/竖屏：面板满宽，菜单无「快捷设置」；
- [ ] 反复折叠/展开 5 次，不出现“列已铺满但快捷设置还在”；
- [ ] 快捷设置 9 列、四个开关、底部四个按钮不被屏幕边缘裁切；
- [ ] 平板先手动藏列 → 重开应用，列保持隐藏，且快捷设置能再次出现。

---

## P1 表头弹窗：右边缘对齐

### 改了什么

| 文件 | 改动 |
|---|---|
| `MixCalcActivity.showAnchoredListDialog()` | 定位改为 `x = 单元格右边缘 - 弹窗宽`，再用 `PopupGeometry.rightAlignedX()` 夹进屏幕；`cellRect == null` 时保持原来的左对齐 |
| `MixCalcActivity.listPopupWidth()` | 新增：列表弹窗宽度只算一处（`min(屏宽, 180dp)`），定位与 `createListPopup()`、批次子菜单共用，避免“按 180dp 算、实际宽度不是 180dp” |
| `MixCalcActivity.createListPopup()` | 宽度改用 `listPopupWidth()` |
| `MixCalcActivity.showBatchPopup()` | 同上，只替换宽度来源，定位规则不变 |

水比、发酵料、列操作三个入口都走 `showAnchoredListDialog()`，改一处三处同时生效。
列设置弹窗是贴标题栏打开的（不贴表头），按 P0 的满宽处理，不套右边缘对齐。

### 验证

```
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline
→ BUILD SUCCESSFUL in 41s
→ TabletLayoutTest: tests=5 failures=0 errors=0
→ PopupGeometryTest: tests=5 failures=0 errors=0
```

新增单测 `app/src/test/java/com/shrimpfarm/app/mixcalc/ui/PopupGeometryTest.java`：

| 用例 | 期望 |
|---|---|
| 单元格右边缘 500、弹窗 180 | x = 320（右边缘对齐） |
| 最左列（右边缘 100） | x = 0，不往左溢出 |
| 最右列（右边缘 1070） | x = 890，右边缘仍在屏内 |
| 单元格右边缘超出屏宽（1200） | x = 900，夹到屏幕右边缘 |
| 弹窗比屏幕还宽 | x = 0，不出现负坐标 |

**测试有效性检查**：这套用例先按错误期望（1070 → 900）跑出 1 个失败，改正为 890 后全绿；说明断言确实在约束定位结果。

### 还差什么（真机，未在本机验证）

- [ ] 最左列表头：弹窗贴左，不裁切；
- [ ] 最右列表头：弹窗右边缘贴单元格右边缘，完整可见；
- [ ] 中间列：右边缘对齐，点完能直接选；
- [ ] 横屏/竖屏、手机/平板、折叠/展开各试一遍（当前 Manifest 仍锁竖屏，横屏要等 P1.5）。

---

## 本轮验证汇总

| 步骤 | 命令 | 结果 |
|---|---|---|
| P0 编译 | `gradlew :app:compileDebugJavaWithJavac --offline` | BUILD SUCCESSFUL |
| P0 单测 | `gradlew :app:testDebugUnitTest` | 5/5 通过（另有变异检查证明用例有效） |
| P1 编译 + 单测 + 打包 | `gradlew :app:testDebugUnitTest :app:assembleDebug --offline` | BUILD SUCCESSFUL，10/10 通过 |

代码规范自检（`check-limits.ps1`，app/src）：

- `MixCalcActivity.java` 有效行 1735（改动前 1751，净减 16 行），没有净增长；
- `showColumnSettingsPopup()` 90 行（原 92 行）；
- `showMainMenu()` 已退出超长名单（原 55 行）；
- 新增文件 `PopupGeometry.java`、两个测试文件均未超限。

> 真机矩阵（折叠/展开、平板、分屏、各输入法）本机无法执行，需在设备上按上面两张清单过一遍再发 0.3.5。

