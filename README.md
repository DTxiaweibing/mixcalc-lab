# 秒算虾料

虾料投喂计量用的表格计算器（安卓）。一条记录 = 一个棚号 + 棚数 + 各料号袋数，
在表格里直接算出平均、发酵料、水比和称料，不用另拿计算器。

| | |
| --- | --- |
| 当前版本 | **0.3.7**（versionCode 10） |
| 系统要求 | Android 9 及以上（minSdk 28，targetSdk 36） |
| 技术栈 | Java 11 + 原生 View（表格全自绘，无第三方 UI 库） |
| 权限 | **不申请任何权限，不联网** |
| 许可 | [Apache-2.0](LICENSE) |

[功能](#功能) · [下载](#下载) · [构建](#构建) · [目录结构](#目录结构) · [更新日志](CHANGELOG.md)

---

## 功能

**11 列一张表**：序号 / 棚号 / 棚数 / 平均 / 发酵料 / 粉料 / 0.3料 / 0.5料 / 1.0料 / 水 / 称料。

- **棚号区间自动拆**：一行可以写 `1-40`、`1、40`、`1 40`，系统按区间展开成实际棚号；
  多个棚号之间用空格、顿号或逗号分隔。
- **后行优先覆盖**：后面的行用了同样的号，先前的行自动让出，被占走的号在那一行改小或清空后自动退回；
  落盘存你填的原文，显示的是实际拥有的号。
- **上限 99**：单个棚号最大 99，超过即时提示，且不计入棚数与料量；改回合法值后自动接着算。
- **顺序录入**：键盘上的「下一步」沿本行可填格一格格往后走，格子跑出屏幕时表格自动滚过去；
  本行填完再按一下直接跳到下一行棚号，「下一行 ▾」遇到下一行该格不能填时会落到下一个能填的格。
- **行与列的整理**：列可固定（横向滚动时钉在左侧）、可隐藏（快捷设置里恢复）；
  填过内容但有效棚数为 0 的行会在换格/切换批次时整行回收，下方行上顶。
- **棚号气泡**：点棚数弹出该行所有棚号，可全选 / 反选 / 删除 / 转出到另一行。
- **「重置」固定在右下角**：表格左右上下怎么滚都在原位，点它先弹确认框。
- **屏幕适配**：手机固定竖屏（横着拿设备点开也不会先横一下再转正）；
  平板等宽窗口（≥600dp）上 11 列按原比例撑满屏宽，不再横向滚动；分屏 / 折叠 / 拖窗口大小后按新窗口重算。
- **中文 / English 界面**，帮助页与隐私政策随之切换；字号固定，不随系统「字体大小」变化。

## 下载

- **应用市场**：华为应用市场（上架后此处补链接）
- **GitHub Releases**：[最新版本](../../releases/latest)，APK 作为 Release 附件发布
- 仓库里的 `dist/` 不纳入版本控制，克隆代码后请自行构建，或直接下 Release 附件

安装：直接覆盖安装即可，数据保留。若设备上装的是其它密钥签名的包，需要先卸载（会清数据）。

## 构建

环境：

- JDK 17 及以上（本项目在 JDK 21 下构建）
- Android SDK Platform 36 + Build Tools（`aapt2` / `dexdump`）
- Gradle 由 Wrapper 提供（8.13，华为云镜像），无需单独安装

```bash
git clone git@github.com:DTxiaweibing/mixcalc-lab.git
cd mixcalc-lab

# 1. 指向本机 SDK
#    在 local.properties 里写：sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk

# 2. 配置 release 签名（可跳过，跳过时 release 包为未签名）
cp keystore.properties.example keystore.properties
#    填 storeFile / storePassword / keyAlias / keyPassword（UTF-8，路径可含中文）

# 3. 跑测试并出包
./gradlew testDebugUnitTest assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

只装到连着的设备上调试：`./gradlew installDebug`。

> 提交前建议跑一遍代码规范检查（单文件 500 行、单函数 50 行等），
> 仓库的 git hook 也会拦「存量超限文件继续变长」。

## 目录结构

```
app/src/main/java/com/shrimpfarm/app/
├── BaseActivity.java / LocaleManager.java     语言切换、公共 Activity
├── AssetWebViewActivity.java                  帮助页 / 隐私政策（assets 里的 HTML）
├── mixcalc/
│   ├── MixCalcActivity.java                   主界面：表格装配、菜单、弹窗、宽度监听
│   ├── model/                                 纯逻辑：棚号区间、结算、覆盖、显示折叠（带单测）
│   ├── table/                                 自绘表格：绘制、命中、编辑框、列宽、固定/隐藏
│   ├── prefs/                                 本地存储（SharedPreferences）
│   └── ui/                                    通用部件：弹窗、气泡提示、宽度监听
app/src/main/assets/help/                      中英文帮助页（HTML + 截图）
app/src/main/assets/privacy.html               隐私政策
app/src/test/java/                             单元测试（143 项）
docs/                                          版本说明、应用市场文案、对齐记录
```

表格是**一个自绘 View**（`MixCalcTableView`）而不是每格一个控件，几百个格子也不会卡；
单元格编辑用浮层输入框，随滚动跟随。

## 自己上传到应用市场

vivo 官方 MCP 只能查不能传，所以仓库里带了一个零依赖传包 CLI（纯标准库）：

```powershell
python scripts\vivo_publish.py doctor --apk "dist\秒算虾料-0.3.7-release.apk"   # 自检
python scripts\vivo_publish.py upload --apk "dist\秒算虾料-0.3.7-release.apk"   # 上传，返回 serialNumber
```

也可以把 APK 拖到 `scripts\vivo-upload.cmd` 上双击运行。凭据只放本机，
配置与错误码见 [docs/vivo-传包.md](docs/vivo-传包.md)。

## 文档

| 文件 | 内容 |
| --- | --- |
| [CHANGELOG.md](CHANGELOG.md) | 版本更新日志（0.3.3 起） |
| [docs/vivo-传包.md](docs/vivo-传包.md) | 自己传包：凭据配置、命令、错误码 |
| [docs/RELEASE-0.3.7.md](docs/RELEASE-0.3.7.md) | 本版升级说明（面向用户） |
| [docs/应用市场更新说明-0.3.7.md](docs/应用市场更新说明-0.3.7.md) | 应用市场「更新内容」可粘贴文案 |
| [docs/安卓端同步清单-鸿蒙改动全记录.md](docs/安卓端同步清单-鸿蒙改动全记录.md) | 与鸿蒙版逐条对齐的记录 |

## 隐私

应用不联网、不申请权限、不收集任何数据：所有内容只保存在本机 `SharedPreferences`，
卸载即清除。隐私政策见 [app/src/main/assets/privacy.html](app/src/main/assets/privacy.html)。

## 许可

[Apache License 2.0](LICENSE)
