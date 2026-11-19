# vivo 应用市场传包（本地 CLI）

官方的 vivo MCP（`dev.vivo.com.cn/vivo-developer-mcp`）**只有查询类工具**，没有上传/送审。
要自己传包，用仓库里的 `scripts/vivo_publish.py`：纯 Python 标准库，不装任何依赖，
在「开放平台 → 账号管理 → API 管理」申请到密钥后即可直接上传。

## 1. 一次性准备

在 vivo 开放平台申请传包服务的 `access_key` / `access_secret`（沙箱、正式各申请一套），
然后二选一填到本机（**不要提交进仓库**，`.gitignore` 已忽略）：

```jsonc
// scripts/vivo_credentials.json
{ "access_key": "你的 AK", "access_secret": "你的 SK" }
```

或者用环境变量（优先级更高）：

```powershell
$env:VIVO_ACCESS_KEY  = "你的 AK"
$env:VIVO_ACCESS_SECRET = "你的 SK"
```

## 2. 常用命令

```powershell
# 自检：Python 版本、凭据是否读到、APK 大小与 MD5
python scripts\vivo_publish.py doctor --apk "dist\秒算虾料-0.3.7-release.apk"

# 只组装请求、不发网络（不需要密钥也能跑）
python scripts\vivo_publish.py upload --apk "dist\秒算虾料-0.3.7-release.apk" --dry-run

# 真上传，成功会打印 serialNumber
python scripts\vivo_publish.py upload --apk "dist\秒算虾料-0.3.7-release.apk"

# 提交审核（serialNumber 可选，按平台要求补）
python scripts\vivo_publish.py submit --online-type 1 --param serialNumber=xxxx

# 通用调用，排查用
python scripts\vivo_publish.py call --method app.query.version --param packageName=com.shrimpfarm.app.calclab

# 先打沙箱
python scripts\vivo_publish.py upload --apk ... --sandbox
```

不想敲命令：把 APK 文件拖到 `scripts\vivo-upload.cmd` 上，双击运行。
它会先做自检，再问一次 `y/N` 才真的发请求。

## 3. 网关风格

平台存在两套公共参数，用 `--style` 切换（默认 `legacy`）：

| `--style` | 网关 | 版本参数 | `target_app_key` |
| --- | --- | --- | --- |
| `legacy` | `developer-api.vivo.com.cn/router/rest` | `version=1.0` | `developer` |
| `openapi` | `developer-openapi.vivo.com.cn/router/rest` | `v=1.0` | `app.developer` |

沙箱统一走 `--sandbox` → `sandbox-developer-api.vivo.com.cn/router/rest`。

签名规则：公共参数 + 业务参数按 ASCII 排序，`k=v` 用 `&` 拼接，
`HMAC-SHA256(access_secret)` 转十六进制；**上传接口的 `file` 参与表单但不参与签名**。
签名大小写默认小写，遇到 `code=23` 脚本会自动换大写重试一次。

## 4. 常见错误码

| 码 | 含义 | 处理 |
| --- | --- | --- |
| `23` | 签名非法 | 检查 secret、`--style`、时间戳；脚本已自动换大小写重试 |
| `A0113` | 包名与当前应用不一致 | 用 `com.shrimpfarm.app.calclab`（不是 `com.shrimpfarm.app.mixcalc`） |
| `A0114` | MD5 不一致 | 重跑 `doctor` 看 MD5，确认传的是同一个文件 |
| `A0115` | 版本号低于已上传 | `versionCode` 必须大于线上（当前线上 10，下个版本用 11） |
| `A0305` / `A0306` / `A0307` | 审核中 / 待发布，暂不可更新 | 等审核结束或先处理待发布版本 |

## 5. 注意

- 应用 ID **855358**，包名 `com.shrimpfarm.app.calclab`，线上 0.3.7 / versionCode 10。
- vivo 通常不受理同版本号覆盖，替换线上包必须先升 `versionCode`。
- 凭据只放本机；`scripts/vivo_credentials.json` 与 `~/.vivo/` 都已在 `.gitignore` 中。
- 单元测试：`python scripts\test_vivo_publish.py -v`（不联网，28 项）。

## 6. 已知限制：2026-11 未能拿到密钥

本账号在控制台里「API 管理」页显示**无权查看**，申请不到 `access_key` / `access_secret`，
因此上面的 CLI 目前只能跑 `doctor` 与 `upload --dry-run`，真上传用不了。

- 官方文档的入口是「管理中心 → 账号管理 → API管理 → 立即开通」，
  客服与第三方工具的说法是「管理中心 → 开放能力 → API传包 → 申请开通」，两处都受账号权限控制。
- 有第三方资料称该能力**只对企业开发者开放**；个人开发者可正常上架应用，但未必能开通传包接口。
- 现阶段的处理：继续在网页手动上传。若日后账号升级为企业号、或权限放开，**脚本无需改动**，
  按第 1 节填好密钥即可直接 `upload`。
