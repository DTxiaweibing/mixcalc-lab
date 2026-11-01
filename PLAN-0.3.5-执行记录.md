# 0.3.5 执行记录（P0 快捷设置 / P1 表头弹窗）

> 计划来源：[PLAN.md](PLAN.md) 第二节 P0、第三节 P1。
> 本文件只记“做了什么、怎么验证的、还差什么”，不改计划本身。
> 每一步：先改代码 → 再跑验证 → 把命令和结果写回这里。

_开始：2026-11-01_

## 范围决定

- **6.1（保存节流 + 主文件备份）本轮不做，延后。** 原因：2 批次 × 30 行数据量下节流收益很小；备份真正的问题是“解析失败会立刻用空表覆盖原数据”，建议随 P2.1 数据模型迁移一起做（那时迁移风险最大，最需要快照保护）。
- **发版节奏**：先在真机按下面的验收清单过一遍；通过后把版本从 `0.3.4 / versionCode 7` 调到 `0.3.5 / versionCode 8`，再重新打 release 包发包。

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

## P0 补充修复：折叠屏外屏仍按平板布局（X QFOLD6 实测发现）

### 现象

用户在 X QFOLD6 云调试上实测：内屏展开时 11 列正常，**折叠成外屏后仍然是 11 列挤在一起**，
每列只有 30dp 左右，字都压扁了，而且菜单里没有「快捷设置」，用户也没有藏列的入口。

### 根因

`TabletLayout.isTabletDevice(Context)` 用了两条判据：

```java
if (configuration.smallestScreenWidthDp >= 600) return true;   // ← 折叠后不成立
float diagonal = sqrt(wInch² + hInch²);
return diagonal >= 7.0;                                        // ← 折叠后仍然成立
```

折叠屏的**物理对角线**不会因为折叠而改变（这台机 7 英寸以上），所以无论内屏还是外屏，
`isTabletMode` 永远是 `true`。于是：

```
isTabletMode = true
→ computeWidths() 按可见列数均分 370dp
→ 11 列 × 约 33dp，且末段整体缩放到屏宽
→ fillsAllColumns() 返回 true（平板布局总能铺满）
→ 菜单隐藏「快捷设置」，用户无法藏列
```

也就是说：判据用的是**设备类型**，而不是**当前窗口**。

### 改了什么

| 文件 | 改动 |
|---|---|
| `TabletLayout` | 删除 `isTabletDevice(Context)`（连同 `Context`/`DisplayMetrics`/`Log` 依赖），改为纯计算 `TABLET_MIN_WIDTH_DP = 600` + `isTabletWidth(int widthDp)` |
| `MixCalcActivity.initTabletAdaptiveSettings()` | `widthDp = round(getScreenWidth() / density)`，`isTabletMode = TabletLayout.isTabletWidth(widthDp)`；判据从「设备」改成「当前窗口」 |
| `MixCalcActivity.initTabletAdaptiveSettings()` | 加 `Log.i`：输出当前窗口宽度 dp、阈值、最终布局模式，方便下次云调试直接看日志确认 |
| `MixCalcActivity.onWindowMetricsChanged()` | 删掉多余的 `isTabletMode = isTabletDevice(this)`（`initTabletAdaptiveSettings()` 内部已经算） |
| `MixCalcActivity` | 删除已无用的私有 helper `isTabletDevice(Context)` |

`TabletLayout` 现在没有任何 Android 依赖，单测跑得更快，也更容易保证正确性。

### 关键判断

- **为什么用 600dp 而不是「自然宽 600dp 放得下」**：两者在默认列宽下数值一样，但语义不同。
  600dp 是 Android 的平板分界，也是"11 列按最小可读宽度排开"的下限；用它当布局模式开关，
  和 `fillsAllColumns()`（是否还要提供藏列入口）分开，互不干扰。
- **外屏 370dp 走手机布局后会发生什么**：`fixedWidths()` 得到 600dp 总宽 → 横向滚动。
  这是手机上一贯的行为，用户已经熟悉；同时 `fillsAllColumns()` 返回 false，菜单恢复「快捷设置」，
  用户可以藏掉不需要的列，把剩下的列放大到铺满。
- **不再有"设备级"开关**：分屏（半屏 400dp）、自由窗口拖拽也会正确切回手机布局。

### 验证

```
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline
→ BUILD SUCCESSFUL in 20s
→ TabletLayoutTest: tests=8 failures=0 errors=0
→ PopupGeometryTest: tests=5 failures=0 errors=0
```

新增 3 个用例（`TabletLayoutTest`）：

| 用例 | 期望 |
|---|---|
| `narrowWindowIsPhoneLayout` | 360 / 480 / 599 → 手机布局 |
| `wideWindowIsTabletLayout` | 600 / 800 / 1200 → 平板布局 |
| `foldedOuterScreenUsesPhoneLayout` | 370dp：不是平板布局，且 11 列放不下 → 保留快捷设置 |

**测试有效性检查**：把 `isTabletWidth()` 临时改成 `return true`（等价于修复前的行为），
`foldedOuterScreenUsesPhoneLayout` 与 `narrowWindowIsPhoneLayout` 立刻失败；改回后 8/8 通过。
说明用例确实锁住了这次回归。

### 还差什么（真机）

- [ ] X QFOLD6 外屏（约 370dp）：应为手机布局、横向滚动、菜单有「快捷设置」；
- [ ] X QFOLD6 内屏：仍为平板布局、11 列铺满、菜单无「快捷设置」；
- [ ] 反复折叠/展开 5 次，两种布局切换正常，不残留旧列宽；
- [ ] 云调试日志里能看到 `布局模式: 手机/平板, 窗口宽 xxx dp (阈值 600dp)`。

---

## 本轮验证汇总

| 步骤 | 命令 | 结果 |
|---|---|---|
| P0 编译 | `gradlew :app:compileDebugJavaWithJavac --offline` | BUILD SUCCESSFUL |
| P0 单测 | `gradlew :app:testDebugUnitTest` | 5/5 通过（另有变异检查证明用例有效） |
| P1 编译 + 单测 + 打包 | `gradlew :app:testDebugUnitTest :app:assembleDebug --offline` | BUILD SUCCESSFUL，10/10 通过 |
| 折叠屏修复 | `gradlew :app:testDebugUnitTest :app:assembleDebug --offline` | BUILD SUCCESSFUL，13/13 通过（变异检查失败 2 个用例） |

代码规范自检（`check-limits.ps1`，app/src）：

- `MixCalcActivity.java` 有效行 1735（改动前 1751，净减 16 行），没有净增长；
- `showColumnSettingsPopup()` 90 行（原 92 行）；
- `showMainMenu()` 已退出超长名单（原 55 行）；
- 新增文件 `PopupGeometry.java`、两个测试文件均未超限；
- 折叠屏修复后又跑了一次：`MixCalcActivity.java` 仍为 1735 行（删掉 `isTabletDevice()` helper，原始行数 2611 → 2609），`TabletLayout.java` 反而更短且不再依赖 Android。

> 真机矩阵（折叠/展开、平板、分屏、各输入法）本机无法执行，需在设备上按上面两张清单过一遍再发 0.3.5。

---

## 发版注意事项：两个同名的 debug.keystore（2026-11-01 排查）

真机复测时出现「新包装不上、提示签名不一致、只能先卸载旧版」，排查结论如下。

### 机器上有两个同名但不同的密钥库

| 文件 | 大小 | 证书 CN | SHA-256 | 用途 |
|---|---|---|---|---|
| `C:\Users\Administrator\.android\debug.keystore` | 2.6 KB | `Android Debug` | `61f01d29…cf6f` | Android Studio 直接 Run 的**调试包**默认用它 |
| `D:\应用软件\安卓应用\debug.keystore` | 1.3 KB | `夏卫兵` | `d68ac3d8…ac76` | **正式发布密钥**（`keystore.properties` 指向它） |

两个文件都叫 `debug.keystore`，但**不是同一把钥匙**。之前手机上装的如果是 Android Studio 直接 Run 的调试包，
再装 release 包就会签名冲突，必须先卸载。

### 排除的猜测

「以前只有 v2 签名、现在多了 v1/v3」不成立。实测两个包都是**只有 v2**：

```
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): false
```

### 与线上一致性

| 对象 | SHA-256 |
|---|---|
| `D:\应用软件\安卓应用\debug.keystore` | `d68ac3d8…ac76` |
| `upload_certificate.pem`（上传市场用） | `d68ac3d8…ac76` |
| 旧上传包 `upload_00ee68a7….apk`（0.3 / versionCode 3） | `d68ac3d8…ac76` |
| 0.3.5 release 包 | `d68ac3d8…ac76` |

四者一致，说明**发布密钥没变**，市场升级不受影响。

### 结论与建议

- 以后手机上的测试包统一用 **release 包**（`assembleRelease` 产物），不要和 Android Studio 直接 Run 的调试包混装；
- 换包前可用 `apksigner verify --print-certs <apk>` 看 CN 是不是 `夏卫兵`；
- `D:\应用软件\安卓应用\debug.keystore` 是正式密钥，**只备份、绝不重新生成**；丢失后该应用将无法再更新。

### 已修：调试包也改用发布密钥

`app/build.gradle` 的 `debug` 构建也指定 `signingConfigs.release`：

```gradle
buildTypes {
    debug {
        if (hasSigningConfig) { signingConfig signingConfigs.release }
    }
    release { ... }
}
```

验证（重新 `assembleDebug` 后验签）：

```
app/build/outputs/apk/debug/app-debug.apk
→ DN: C=, ..., CN=夏卫兵
→ SHA-256: d68ac3d800da57bfea0b02f692d6d40696ee7565cc2c836ad76773fb90edac76
```

现在调试包与 release 包证书一致，可以互相覆盖安装、共用同一份数据；
换到没有 `keystore.properties` 的机器上仍会自动回退到 Android 默认调试签名。

另：Android Studio 手动导出的 `app/release/app-release.apk`（0.3.4 / versionCode 7）
验签结果与 CI 打的包一致（CN=夏卫兵，`d68ac3d8…ac76`，仅 v2 方案），可佐证发布密钥从未变过。

