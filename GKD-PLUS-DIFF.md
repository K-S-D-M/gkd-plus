# GKD-Plus 与上游 GKD 差异文档

本文档记录 GKD-Plus（`fjjzy/gkd-plus`）相对上游 [gkd-kit/gkd](https://github.com/gkd-kit/gkd) 的全部差异，用于：
- 同步上游代码时快速定位需要重新应用的本地改动；
- 向使用者说明本分支新增的能力。

> 当前基线：上游 `e732da05`（已合并 `Merge upstream gkd-kit/gkd main into main`）。
> 上游已将模块 `:app` 重命名为 `:gkd-app`，包名由 `li.songe.gkd` 改为 `li.gkd.app`，并拆分出 `:gkd-db` / `:gkd-selector` / `:gkd-hidden-api` 模块。本分支所有改动均已适配新架构。

---

## 一、新增功能

### 1. AI 自动规则生成

快照保存后自动调用大模型生成 GKD 订阅规则，并写入「订阅 - 本地规则」。

- 支持 OpenAI 与 Anthropic 两种协议
- 支持模型列表拉取、连接测试
- 请求队列：多次快照按顺序处理，不丢失

**新增文件**
- `gkd-app/src/main/kotlin/li/gkd/app/util/AiRuleGenerator.kt` — AI 调用、JSON 提取/解析、规则写入核心
- `gkd-app/src/main/kotlin/li/gkd/app/feature/settings/AiSettingsDialog.kt` — AI 配置对话框
- `gkd-app/src/main/assets/gkd-rule-generator-prompt.md` — 内置提示词

**改动文件**
- `gkd-app/src/main/kotlin/li/gkd/app/data/settings/SettingsStore.kt` — 新增 `AiConfig` 数据类及 `aiEnable` / `aiConfig` 字段
- `gkd-app/src/main/kotlin/li/gkd/app/snapshot/SnapshotCapture.kt` — 快照保存后按开关触发 AI 生成
- `gkd-app/src/main/kotlin/li/gkd/app/feature/settings/AdvancedPage.kt` — 新增「AI 自动规则」区块与配置入口
- `gkd-app/src/main/kotlin/li/gkd/app/feature/settings/AdvancedVm.kt` — 新增 `setAiEnable`

### 2. 加强模式（双击快照按钮）

双击悬浮球触发「生成规则 → 执行动作 → 校验界面变化 → 失败重试」闭环。

- 对比执行前后节点树签名判断规则是否生效
- 未生效时把失败上下文回填 prompt 让 AI 重新生成，最多重试 2 次

**改动文件**
- `gkd-app/src/main/kotlin/li/gkd/app/service/ButtonService.kt` — 单击捕获快照 / 双击进入加强模式
- `gkd-app/src/main/kotlin/li/gkd/app/util/AiRuleGenerator.kt` — `enhancedGenerate()`

### 3. 首页「手动更新 APP」入口

在首页新增「手动更新 APP」卡片，弹窗提供夸克网盘 / 百度网盘下载地址，方便无加速器用户更新。

**改动文件**
- `gkd-app/src/main/kotlin/li/gkd/app/ui/home/DashboardPage.kt`

### 4. 基于 GitHub Release 的更新检查

更新渠道指向本仓库的 GitHub Release，直接解析 release 的 APK 资产下载安装。

**改动文件**
- `gkd-app/src/main/kotlin/li/gkd/app/util/Option.kt` — `UpdateChannelOption` 指向 `api.github.com/repos/fjjzy/gkd-plus/releases/latest`
- `gkd-app/src/main/kotlin/li/gkd/app/util/Upgrade.kt` — 新增 `GitHubRelease` 解析、版本比较、`absoluteDownloadUrl`

### 5. 导出日志的敏感信息脱敏

导出日志（含 store 配置、日志文件）时自动把 API Key / Bearer Token / x-api-key 替换为 `********`，避免密钥泄露。

**改动文件**
- `gkd-app/src/main/kotlin/li/gkd/app/util/FolderUtils.kt` — `buildLogFile()` 复制到临时目录并脱敏；新增 `redactSensitiveText`

### 6. 版本声明

「关于」页新增版本声明，说明本分支为社区改版，非官方版本。

**改动文件**
- `gkd-app/src/main/kotlin/li/gkd/app/feature/settings/AboutPage.kt`

---

## 二、品牌与构建配置

| 项目 | 上游 | GKD-Plus |
|------|------|----------|
| `applicationId` | `li.songe.gkd` | `li.songe.gkd.plus` |
| `versionCode` / `versionName` | 92 / 1.12.1 | 93 / 1.12.1-plus.N |
| 根项目名 | `gkd` | `gkd-plus` |
| 应用名 | `GKD` | `GKD-Plus` |
| APK 文件名 | `gkd-v{ver}.apk` | `gkd-plus-v{ver}.apk` |
| 仓库地址 | `github.com/gkd-kit/gkd` | `github.com/fjjzy/gkd-plus` |

**改动文件**
- `gkd-app/build.gradle.kts`
- `settings.gradle.kts`
- `gkd-app/src/main/res/values/strings.xml`
- `gkd-app/src/main/kotlin/li/gkd/app/util/Constants.kt`（`REPOSITORY_URL`）
- `gkd-app/src/main/kotlin/li/gkd/app/App.kt`（`commitUrl`）

---

## 三、测试

- `gkd-app/src/test/kotlin/li/gkd/app/util/FolderExtTest.kt` — 校验日志脱敏规则
- `gkd-app/src/test/kotlin/li/gkd/app/util/BackupUtilsTest.kt` — 校验备份数据保留真实 apiKey（不被脱敏）

---

## 四、同步上游的方法

上游结构已重构，直接 `git merge` 会产生大量冲突。推荐流程：

1. `git fetch origin`（origin 指向上游 gkd-kit/gkd）
2. `git merge origin/main`，冲突文件一律先取上游版本，得到干净的上游架构
3. 按本文档「一、新增功能」逐项把本地改动重新应用到新架构上
4. 编译验证：`./gradlew :gkd-app:assembleGkdDebug`
5. 单元测试：`./gradlew :gkd-app:testGkdDebugUnitTest`

> 注意：上游重构后 `SnapshotExt` 拆分为 `SnapshotCapture` + `SnapshotRepository`，
> `subsMapFlow` 迁到 `SubscriptionState`，`LOCAL_SUBS_ID` 迁到 `:gkd-db` 模块，
> `updateSubscription()` 改为 `SubscriptionRepository.update(id, transform)`，
> `launchTry` 改为 `launchLogged`，`toast` 改为 `ToastUtils.toast`，
> 选择器 `Selector.parseOrNull` 改为 `Selector.compile` + `MatchOptions`。
