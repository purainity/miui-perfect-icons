# MIUI 完美图标计划 · 替代客户端开发文档

原“完美图标计划”APP 依赖的资源服务器已停止服务，但图标资源仓库 `pzcn/Perfect-Icons-Completion-Project` 仍在 GitHub 上持续更新。本项目开发一个单机版替代客户端：从 GitHub 获取图标资源，在本地完成装配，产出 MTZ 主题包与 Magisk 模块 zip。**产出到文件即止**——不做安装、不做重启、不做任何设备修改。

原 APP 的全部逻辑由其 assets 内的 shell 脚本实现（核心为 `misc/module.sh`），本 APP 的行为规格即由这些脚本推导而来；原版脚本、服务端 CI 与实物产物样本均已存于 `docs/` 下（见 §3.1）。

---

## 1. 目标与范围

### 1.1 产出目标

1. **MTZ 主题包**：用户自行导入小米主题商店，需设计师账号或主题破解环境
2. **Magisk 模块 zip**：用户自行在 Magisk / KernelSU 中刷入

### 1.2 明确不做

- HarmonyOS / EMUI（hwt）相关的一切页面与流程
- 一键安装模块（`magisk --install-module` / `ksud`）、重启设备按钮、电源菜单
- Xposed 模块（原 APP 的“主题试用防恢复”hook）
- Beta 计划（脚本在线热更）、“下载兼容模式”开关
- 内嵌 git / curl / busybox / zip 等可执行文件与 so 库
- 服务器状态页、服务器连通性检查

---

## 2. 技术选型

### 2.1 技术栈

| 项 | 选型 |
|---|---|
| 语言 | 纯 Kotlin，单模块 `:app` |
| UI | XML Views + ViewBinding + Material Design 3 控件 |
| minSdk | 29（Android 10.0） |
| targetSdk | 跟进当前最新稳定版（当前 37） |
| 数据源 | 图标资源仅取 GitHub（`api.github.com` + `raw.githubusercontent.com`）；公告沿用原版接口（§9.1） |
| 界面语言 | 中文 |

### 2.2 选型依据

**minSdk——取 29（Android 10.0）：**

- 产物面向 HyperOS / MIUI 12+，其本身要求 Android 10+，生成器与目标设备天然重合；完美图标特性自 MIUI 12 起由系统启动器引入，依赖系统内置图标与图标自身的 Adapted Icon 特性（上游仓库说明：https://github.com/pzcn/Perfect-Icons-Completion-Project ）
- 免去旧版存储权限与旧通知兼容分支，MediaStore / SAF / 前台服务 / 深色模式全部走现代路径

**UI——XML Views + ViewBinding + Material Design 3：**

- 界面全部按 Material Design 3 重新设计，不照抄原版 kr-script 的视觉样式，仅保留其信息架构与文案语料

**数据源——仅 GitHub：**

- 图标资源仓库仍在 GitHub 上持续更新，清单比对、下载、装配全部在本机完成，不依赖任何自有服务器
- 原版 APP 的网络门禁一旦失败即终止全部流程；本项目不做连通性预检，失败由任务级重试兜底

### 2.3 硬性红线

| 项 | 禁止 |
|---|---|
| UI 框架 | Compose；DataBinding |
| 依赖 | `com.android.support.*` 旧支持库；任何携带 so 原生库的依赖 |
| 内置文件 | git / curl / busybox / zip 等可执行二进制（仅作为 `docs/original-app-scripts/` 中的行为参考，不随 APP 分发） |
| 数据源 | 默认强制镜像（镜像功能默认关闭，用户主动开启后仅替换请求地址；内置候选仅作预填选项，不保证可用性）；连通性预检逻辑 |
| 权限 | 任何需要 root 权限的功能 |

权限落地：Manifest 仅声明 `INTERNET`、`QUERY_ALL_PACKAGES`（应用选择页需列出全部已安装应用）与前台服务/通知相关权限；输出公共 Download 走 MediaStore（免存储权限）；输出目录与自定义图标目录走 SAF 一次性授权。

### 2.4 Release 构建与优化

- Release 通过 `optimization.enable = true` 启用 R8 代码与资源优化，不限制 `packageScope`，优化范围覆盖整个应用及其依赖；默认 Android 平台保留规则与依赖提供的规则仍由构建工具合并
- 应用自定义规则放在 `app/src/main/keepRules/rules.keep`，适用于包含 main 源集且启用 R8 的变体，不再通过 `proguardFiles` 接入旧的 `proguard-rules.pro`
- 为兼容此前 release 启动时的 `IllegalAccessError`，暂时保留 `kotlin.**` 与 `kotlinx.**` 存活类的名称；未使用的类仍可被移除。待经过完整的 release 实机验证后，再评估收窄或移除这两条规则
- 每次调整优化范围或保留规则后，须构建 release 并验证启动与主要功能；构建成功和 APK 静态检查不能代替实机验证

---

## 3. 原版机制与参考文件

### 3.1 参考文件（docs/ 直下四个目录）

```
docs/
├─ original-app-scripts/   原版 APP assets 全量拷贝，与发布的 v3.0.2 APK 内 assets 逐字节一致
│   ├─ local-scripts/      kr-script 页面定义与逻辑脚本：
│   │   ├─ install.xml          首页“下载安装”页结构（主题选择/自定义图标/遮罩开关/生成入口）
│   │   ├─ more.xml             APP 首页结构（公告/更新/各页面入口）
│   │   ├─ addon.xml            图标自定义页
│   │   ├─ disabledynamicicons.xml  禁用动态图标页
│   │   ├─ page/about.xml       设置与帮助页（外链、清理缓存）
│   │   ├─ misc/module.sh       ★核心：资源下载、装配、打包、安装的全部逻辑
│   │   ├─ misc/string.ini      全部中文文案
│   │   ├─ misc/transform_config{1,2}.xml   遮罩模板 V1/V2
│   │   └─ misc/*.sh            其余配置读写/检查脚本
│   ├─ script/             before_start.sh（初始化、公告拉取、遮罩默认值）等启动脚本；
│   │                      bin/ 内置 busybox/git/curl/toybox/zip 等二进制，仅为原版行为参考，新 APP 不携带
│   └─ kr-script.conf、easygo.json、xposed_init
├─ original-app-strings/   原版 APP 资源文案（pio 与 krscript 的 strings.xml，含“关于”致谢名单）
├─ server-ci/              图标仓库服务端 CI（见 §3.4）
└─ sample-outputs/         实物样本：模块产物内文件、产物文件清单（验收 diff 基准）、
                           APP 私有数据中的文本配置、mtz 外壳与 explore 主题的服务端原件（tar.xz）
```

APP 私有数据备份中的 `miui/` 图标缓存（约 800MB，内含嵌有原服务器凭据的 `.git` 目录）不纳入参考文件。

### 3.2 资源构成（原版三段式）

| 资源 | 原版来源 | 内容 |
|---|---|---|
| 主图标库 | 制品库 `iconsrepo.tar.xz` / 增量 `git pull`（git 仓库 `e.coding.net/miuiicons/icons/icons2.git`） | `icons/` 全部内容 → `res/drawable-xxhdpi/` |
| 主题包 | 制品库 `{主题ID}.tar.xz` | `icons/`（扁平 png 与分层目录并存）与 `layer_animating_icons/` |
| mtz 外壳 | 制品库 `mtz.tar.xz` | `description.xml`、`preview/`、`wallpaper/`、`com.miui.home` |

### 3.3 原版网络接口现状（逐一实测）

对原 APP 脚本中出现的全部网络接口做了完整探测，结论：**多数数据接口仍在响应且仍在更新，APP 整体失效的直接原因在网络前置检查环节**。

| 接口 | 用途 | 状态 |
|---|---|---|
| `https://miuiicons-generic.pkg.coding.net/icons/files/link.ini?version=latest` | 下载地址分发 | 200 正常（内容指向 oracle 对象存储） |
| `https://miuiicons-generic.pkg.coding.net/icons/files/announce3000.txt?version=latest` | 中文公告 | 200 正常（内容为 3.0 版本升级说明） |
| `https://miuiicons-generic.pkg.coding.net/icons/files/announce3000eng.txt?version=latest` | 英文公告 | 404 失效（本 APP 仅中文界面，不使用） |
| `https://miuiicons-generic.pkg.coding.net/icons/files/changelog2.xml?version=latest` | 更新日志 | 200 正常（仍有新提交条目，持续更新） |
| `https://miuiicons-generic.pkg.coding.net/icons/files/check?version=latest` | 服务器检查 | 200 正常 |
| `https://miuiicons-generic.pkg.coding.net/icons/files/Icons_Archiving.zip` | 图标存档 | 200 正常 |
| `https://miuiicons-generic.pkg.coding.net/icons/files/before_start_2100.sh?version=latest` | Beta 脚本热更 | 200 正常 |
| `https://miuiicons-generic.pkg.coding.net/icons/hyper/iconsrepo.ini?version=latest`、`https://miuiicons-generic.pkg.coding.net/icons/hyper/iconscount.txt?version=latest`、`https://miuiicons-generic.pkg.coding.net/icons/hyper/mtz.ini?version=latest` | 版本描述 | 200 正常 |
| `https://miuiicons-generic.pkg.coding.net/icons/hyper/{主题ID}.ini?version=latest`（全部 19 个主题） | 主题版本描述 | 全部 200 正常（iconsrepo 版本新于图标仓库本地 clone，`git_SHA` 与 git 仓库 HEAD 一致，CI 仍在推送） |
| `https://miuiicons-generic.pkg.coding.net/icons/hyper/{主题ID}.tar.xz?version=latest`、`https://miuiicons-generic.pkg.coding.net/icons/hyper/mtz.tar.xz?version=latest` | 主题包与 mtz 外壳直链下载 | 200 正常（xz 文件头校验通过；此为原版 ≤6MB 小文件通道） |
| `https://miuiicons-generic.pkg.coding.net/icons/hyper/iconsrepo.tar.xz?version=latest` | 主图标库直链 | 404 不存在（仅存于 oracle 对象存储；本 APP 资源改走 GitHub，不使用） |
| `https://miuiicons-generic.pkg.coding.net/hyper/files/*` | update_check.sh 使用的路径 | 404 失效（原 APP 内部已不一致的旧路径，不使用） |
| `https://objectstorage.ap-seoul-1.oraclecloud.com/n/cnkkaulgjby7/b/icons/o/index.html` | network_check 的探测目标（`link_check`） | **链路极不稳定**：多次探测仅约两至三成成功，失败均为 TCP 握手超时（与请求方法无关）；经独立网络通道验证服务端仍在线并正常返回内容 |
| `https://objectstorage.ap-seoul-1.oraclecloud.com/n/cnkkaulgjby7/b/icons/o/hyper/iconsrepo.tar.xz` | 主图标库大文件唯一下载通道（120MB） | **事实不可用**：链路间歇性握手超时，大文件抽样下载反复失败；即便连通，实测速度也仅 KB/s 级 |
| `https://e.coding.net/miuiicons/icons/icons2.git` | 主图标库增量更新 | 匿名可读，HEAD 与制品库 `.ini` 一致，CI 仍在推送 |
| `https://emuiicons-generic.pkg.coding.net/files/zip/{主题ID}.ini?version=latest` | EMUI 资源 | 200 正常（不在本项目范围） |
| 语雀 7 篇文档、QQ 群、push.html、icons-demo（两个域名）、Telegram 频道/群组、GitHub 两个仓库 | 文档外链 | 全部可达 |
| `https://iconsx.vercel.app/privacy.html` | 隐私协议（vercel 部署） | 404 失效；同站 `https://iconsx.pedroz.eu.org/privacy.html` 正常，外链改用该地址 |
| `https://status.pedroz.eu.org/status/icons` | 原版“查看服务器状态”链接 | 已失效；本 APP 不使用 |

**原 APP 失效根因**：`module.sh` 的 `network_check()` 以 `curl -I`、`--connect-timeout 1` 探测 oracle 对象存储上的 `link_check` 地址，重试三次全败即输出“× 未检测到网络连接”并终止。实测该对象存储的服务端仍在线（独立通道可正常取回内容），但链路质量极差——间歇性 TCP 握手超时、连通时速度仅 KB/s 级。对这样一条国际链路，“1 秒超时 + 仅三次重试”的门禁设计必然频繁全败，而门禁一败即终止全部流程，数据平面（coding 制品库、git 仓库实际仍在服务且持续更新）完全无从使用。主图标库 120MB 大文件更是只有这条链路可下，双重不可用。

对本 APP 的两条结论：其一，图标资源改由 GitHub 获取，不再依赖这条死链；其二，**不做任何独立的连通性预检**，直接请求真实资源端点并按实际失败处理（§5.1），避免重蹈原版“门禁端点单点故障拖垮全部功能”的覆辙。

### 3.4 服务端 CI 行为（需本地复刻的关键点）

图标仓库的 `.github/workflows/` 揭示了制品库内容的生成方式，存于 `docs/server-ci/`：

- **`miui_theme.yml`**：主题包打包前执行 `cp -rf common/* <主题ID>`——**`themes/common/` 的共用动态图标在此阶段合并进主题**，lrone、luck7 除外。新 APP 装配时须执行同样的合并（§5.2 步骤 3）
- **`miui_res.yml`**：将 `icons/**` 推送至 icons2 git 仓库并生成 `iconsrepo.tar.xz`；`iconscount` = `icons/` 一级子目录数（`ls -l | grep "^d" | wc -l`）；changelog2.xml 由 GitHub 提交记录自动拼装——**更新日志与 commits 同源**
- **`archiving_icons.yml`**：图标存档 = `addition/Icons_Archiving/` 打 zip

### 3.5 实现要点（易错点）

以下结论来自对 `module.sh` 的逐行分析、实物比对及 §6.2 所列官方文档与参考实现，实现时须注意：

1. **两类主题名称**：界面显示名不带后缀（如 `MIUI经典`），装配用名称 = 显示名 + `主题`（如 `MIUI经典主题`）。后者用于产物文件命名、module.prop、mtz description 替换与导出日志（§4.4）。
2. **module.prop 共 7 行**：`module.sh` 中另有一处向临时目录的 module.prop 追加 `iconsrepo=` 的语句，但该文件从未打入产物，属死代码，不复刻。
3. **自定义图标“高级附加”的位置与形态按产物区分**：模块侧位于产物根 `addons/`（原版 `addon()` 行为），安装时由 `customize.sh` 以 `unzip -oj` 按文件名展平进 `theme/default/`（通配符跨目录层级，已实验复核）；MTZ 侧在合并外壳后按同一语义递归展平到产物根（原版留在 `addons/` 目录，属原版错误，已修正）。展平规则、保留名与报错条件见 §6.2 修正 1。
4. **扁平 png 分离仅模块执行**：原版 `pack()` 以无参调用，`[ "$1" != mtz ]` 恒真，MTZ 产物同样被分离，属原版错误，已修正（依据见 §6.2 修正 3）。分离发生在全部 res 来源（基础库/主题/自定义）并入之后，分离出的产物根 `miui_mod_icons/` 同时包含主题与自定义的扁平静态图标（§5.2 步骤 7）。
5. **denylist 过滤先于自定义图标导入**：自定义动态图标不受禁用列表约束，已禁用的包名可经自定义目录重新进入产物。该顺序为既定设计而非错误（PerfectIconsPacker 同序实现并明言“禁用先于自定义生效，因此自定义可以重新提供被禁用的图标”，见 §5.2 怪癖说明）。
6. `customize.sh` 与 `updater-script` 在 Magisk 安装时即被消费，不随已装模块保留；模块备份样本中的 `update-binary` 已被导出工具改写、`post-fs-data.sh` 被移动位置，**权威文本一律以 `module.sh` heredoc 为准**（§6.1 已全文收录）。
7. 原版装配日志中无“请稍候”字样（对应 string 变量未定义）；`x 文件下载失败` / `x 文件下载损坏` 为小写 x。
8. 两个遮罩模板在仓库 `addition/` 与实际产物两处逐字节一致，装配直接取仓库缓存文件（不再内置 assets，模板单一来源）；三个模块模板（`customize.sh` 等）仓库中无对应物，必须随 APK 内置。
9. mtz 外壳的 `com.miui.home` 为 4 字节文本桩（内容 `test`），非合法 zip；装配时清除该桩，高级附加同名文件（须为完整可读 zip）优先，缺失时写入 22 字节空 zip（裸 EOCD）。该组件必要性与选择依据见 §6.2 修正 2。
10. 原版版本检查比对 `.ini` 中的 `theme_version` 时间戳 / 主图标库 `git_SHA`；新 APP 改比对 GitHub commit SHA，数据同源。

---

## 4. 数据源与资源获取

### 4.1 图标仓库结构（`github.com/pzcn/Perfect-Icons-Completion-Project`）

| 仓库路径 | 内容 | 规模 | 用途 |
|---|---|---|---|
| `icons/<包名>/0.png + 1.png` | 全主题共用分层图标（0=背景，1=前景），约 2400 个应用目录 | 4790 文件 / 约 97MB | 装配底图 |
| `themes/<主题ID>/icons/` | 主题图标：扁平 `<包名>.png` 与 `<包名>/` 分层目录并存 | 见 §4.4 | 主题覆盖层 |
| `themes/<主题ID>/layer_animating_icons/<包名>/` | 主题动态图标 | 见 §4.4 | 装配动态图标 |
| `themes/common/layer_animating_icons/` | 共用动态图标 | 约 1.2MB | 并入除 `lrone`、`luck7` 外的所有主题 |
| `addition/mtz/` | mtz 外壳：`description.xml`（含 `themename` 占位串）、`preview/`（12 图）、`wallpaper/`、`com.miui.home` | 15 文件 / 约 5.3MB | MTZ 生成 |
| `addition/transform_config.xml` / `transform_config2.xml` | 遮罩 V1 / V2 | 2 文件 | 装配唯一来源（随资源同步下载，不再内置 assets） |
| `addition/Icons_Archiving/` | 历史图标存档 | 465 文件 | “下载图标存档”功能（懒加载） |
| `emui/`、`.github/`、`addition/{demo,Icons_Addon,module,old_action,pages}` | 华为资源 / CI / 素材 | — | 不获取 |

### 4.2 接口

- **清单（全量）**：`GET https://api.github.com/repos/pzcn/Perfect-Icons-Completion-Project/git/trees/{commit_sha}?recursive=1`
  返回全量文件清单（当前约 2.8 万条目，JSON 约 9MB，`truncated=false`），含每个文件的 **blob SHA** 与大小——即增量比对所需的全部信息。
- **清单（增量）**：`GET https://api.github.com/repos/pzcn/Perfect-Icons-Completion-Project/compare/{base_sha}...{head_sha}`
  返回两提交间的变更文件表（`status: ahead` 时含每个变更文件的 blob SHA 与 added/modified/removed/renamed 状态，KB 级体积）。仅当本地快照基线为最新提交的祖先时可用；分叉（diverged）、提交跨度过大（>200）或文件数达到 Compare 返回上限（300）时回退全量清单。合并变更表时先移除全部删除及重命名源路径，再写入新路径，避免重命名顺序造成覆盖；信息不完整时回退全量清单。变更表不含文件体积，不逐文件发 HEAD：先以本地文件大小尝试缓存校验，仍需下载时在内容校验成功后按实际字节数回填；有未知体积的下载使用不定进度。
- **文件下载（主通道）**：`https://raw.githubusercontent.com/pzcn/Perfect-Icons-Completion-Project/{commit_sha}/{path}`
  下载引用必须用 **commit SHA**（blob SHA 无法作为下载引用，raw 以 307 重定向拒绝）；增量比对用 blob SHA。
- **文件下载（兜底通道）**：`GET …/git/blobs/{blob_sha}`，请求头 `Accept: application/vnd.github.raw` 返回原始文件内容
  仅在主通道重试耗尽（raw 端点对合法文件存在偶发瞬时报错）时使用；默认 JSON 响应为 base64（约 +36% 体积），必须使用 raw 媒体类型。内容仍按 blob SHA 校验。
- **版本检查（优先通道）**：`GET https://github.com/pzcn/Perfect-Icons-Completion-Project/commits/main.atom`——主站 Atom feed，**不占 API 限额**，条目链接含最新 commit SHA；单次请求不重试，失败即回退次通道。Atom 请求一律不带令牌；使用 API 镜像时，版本检查与同步采用所选镜像的主站 Atom；自定义 API 镜像没有主站镜像时跳过官方 Atom，直接使用镜像 commits API。主站域仅此一个机器可读端点（树页面为 564KB 的 HTML，内嵌数据不含 blob SHA；`{repo}.json` API 视图已 404），Trees/Compare 清单无法以主站域替代。
- **版本检查（回退通道）**：`GET …/commits?per_page=1`（API 域），支持 ETag 缓存。ETag 与对应 API 基址及其响应 SHA 一起缓存，只有三者匹配时 304 才复用；Atom 成功不覆盖 API 的 ETag 与 SHA 配对。
- **访问令牌**：用户可配置 GitHub 访问令牌（无权限令牌即可，仅公开数据只读），将核心 API 限额从 60 次/时提高至 5000 次/时。令牌以 `Authorization: Bearer` 头附加，**仅发送至官方 `api.github.com` 与 `raw.githubusercontent.com` 域名**；主站域（Atom）与镜像请求一律不带令牌。令牌被 401 拒绝时快速失败并提示“GitHub 令牌无效或已过期”，不重试；并发下载中遇到限额耗尽也立即停止重试并保留限额异常，以便使用已校验的缓存。令牌验证直接请求带新令牌的官方 commits API，不以未携带令牌的 Atom 或镜像请求代替；验证过程中可选择“跳过验证并保存”，失败后也可选择“仍要保存”，未验证不代表令牌有效。
- **镜像**：用户可为 API、主站（Atom）与文件下载分别配置地址基址（API 与文件两项用户可配，主站基址随预设自动确定），**三个基址均含仓库路径**，语义对称。支持三种形态：前缀式（`https://gh-proxy.com/https://api.github.com/repos/pzcn/Perfect-Icons-Completion-Project`，完整官方地址拼在前缀后）、域名替换式（`https://api.6github.com/repos/…`，路径结构与官方一致）、jsDelivr 引用式（文件前缀以 `@` 结尾时按 `{base}{commit}/{path}` 拼接，**必须以 commit 为引用**——jsDelivr 的分支引用对本仓库的大文件树回源失败，commit 引用则命中其对象存储与 CDN 缓存）。镜像地址跟随服务端 30x 重定向，官方地址维持不重定向。镜像由第三方维护：转发 API 的镜像要么以自有令牌回源（全体用户共享 5000 次/时池，可能间歇 403），要么以匿名出口回源（出口 IP 共享 60 次/时池，更易耗尽）；此类 403 伴随 `X-RateLimit-Remaining: 0` 透传头，仍按限额异常处理。经 API 镜像进行版本检查与同步时，提交、根树和完整 Trees 清单均由所选镜像提供，不依赖官方 GitHub 直连；按 Git 树对象格式自底向上计算并比对镜像根树，之后逐文件校验 blob SHA。该校验能够发现清单与内容不一致，但无法证明镜像提供的数据来自官方，镜像可能同时伪造版本、根树、清单和文件；界面提醒用户选择可信镜像。GitHub 完全不可达时必须选择同时支持 API 和文件下载的可用镜像；仅文件镜像仍需直连官方 API，重定向到官方的镜像也不能绕过官方连接故障。自定义前缀允许 HTTP 明文传输，镜像不接收令牌。另有一种主站 raw 重定向形态（`github.com/{owner}/{repo}/raw/{ref}/{path}`，302 至 raw 域名）经部分镜像可用，但相对直接配置 raw 前缀无额外收益，不采用。
- **限额规避**：版本检查经主站 Atom feed（零 API 消耗）为常态，Atom 不可达时才回退 API 通道（ETag 304 亦零消耗）；jsDelivr 解析域（`data.jsdelivr.com`）也可解析分支 commit 但可用性依赖其 CDN，不作通道。清单（Trees/Compare）与 Blob 兜底无 API 域之外的等价来源（jsDelivr 文件列表 API 的 hash 非 git blob SHA，无法支撑逐文件校验与增量索引），不可替代。
- **限额记账**：官方 API 同步时，无更新的版本检查经 Atom（或 304）通常不消耗额度，有更新时请求一次 Compare 或 Trees；API 镜像同步时，镜像 Atom 不可用或未配置时回退镜像 commits API，需更新时另请求镜像 Git commits API 获取根树，并请求镜像 Trees。raw 与归档（zipball/tarball）下载不占核心限额。
- 整仓 zipball（codeload）约 600MB 且不支持 Range 断点续传，不采用（归档下载有独立限额计数，与核心 API 互不影响）。

### 4.3 获取与缓存策略

- **按文件获取**：首次下载 `icons/**` + 所选主题 `themes/<ID>/**` + `themes/common/**`（如适用）+ `addition/mtz/**` + 两个遮罩 xml，约 105~130MB。切换主题时按需补下该主题目录。
- **增量更新**：官方 API 优先 Compare 增量（快照基线与最新提交间的 KB 级变更表，套用到上次快照条目），基线缺失、分叉、跨度过大或变更表不可靠时回退全量 Trees 清单；API 镜像在有更新时始终取得完整 Trees 并对照镜像根树检查一致性，因为 Compare 无法证明变更表完整。与本地索引（路径 → blob SHA）比对，只下载变化/新增文件，删除远端已删除的文件。快照记录清单所用 API 基址，仅与当前 API 基址一致时可作为增量基线；同步时切换数据源或旧快照无来源会重新获取清单，仍可复用内容匹配的文件；图标存档仍优先复用完整且通过内容校验的已有缓存。同源快照基线与最新提交一致时不发起清单请求。
- **并发与可靠性**：4~8 并发；单文件失败自动重试（指数退避，重试上限 5 次即共 6 次尝试；无代理网络下 GitHub 端点存在间歇性握手超时，需留足重试余量）；raw 通道重试耗尽的文件经 Blob API 兜底通道再试 2 次（应对 raw 端点对合法文件的偶发瞬时报错）；兜底仍失败的普通错误由并发任务安全收集，不中断整体，整体下载结束后统一再补一轮，仍失败才报错；先写 `.tmp` 再原子改名；以单文件为断点单位（仓库最大文件为 3.4MB 壁纸，无需字节级续传）。
- **`addition/Icons_Archiving/`**：仅在用户触发“下载图标存档”时下载或从缓存导出。
- **本地缓存布局**：资源缓存直接位于私有 `files/` 目录下——`repo/` 子目录镜像仓库结构（`icons/`、`themes/<ID>/`〔按下载过的主题累积保留〕、`addition/`），同级另有 `index.json`（commit SHA、每文件 blob SHA、拉取日期）、`api.json`（最新 commit + ETag）、`snapshot-<主题ID>.json` 与 `archive-snapshot.json`（带版本标记的完整快照清单；旧版清单不作为完整快照或增量基线，重新拉取清单后仍可复用已校验的文件）。更新日志缓存（`files/original-changelog.xml`）与资源缓存同目录但独立管理。“清理缓存”按资源缓存自有清单删除（`repo/` 与各索引/快照文件，不触碰更新日志缓存），大小统计范围与之相同。装配工作区、产物暂存、自定义快照等瞬态内容置于系统 `cache/` 目录（系统存储紧张时可自动清空，全部可重建）。
- **网络提示**：无代理直连网络下 GitHub 各端点（api / raw / codeload / 主站）均可能出现间歇性连接超时，属用户侧网络环境问题；APP 以“单文件重试 + 并发请求 + Blob API 兜底”的获取策略应对（§4.3 并发与可靠性），镜像仅作为用户自选的网络备选（§4.2 镜像）。首次下载前提示体量（约 100~130MB）与“下载缓慢或失败时可尝试切换网络”。

### 4.4 主题表（19 项）

| ID | 显示名 | 装配名 | 仓库体积 |
|---|---|---|---|
| `default` | MIUI经典 | MIUI经典主题 | 9.4M |
| `Hyper` | HyperOS | HyperOS主题 | 12M |
| `explore` | MIUI探·界 | MIUI探·界主题 | 12M |
| `coloros12` | ColorOS12 | ColorOS12主题 | 3.8M |
| `coloros13` | ColorOS13 | ColorOS13主题 | 3.1M |
| `coloros14` | ColorOS14 | ColorOS14主题 | 4.7M |
| `flyme9` | Flyme9 | Flyme9主题 | 3.7M |
| `flyme10` | Flyme10 | Flyme10主题 | 4.6M |
| `iOS` | iOS | iOS主题 | 2.3M |
| `lrone` | Lrone | Lrone主题 | 25M |
| `luck7` | Luck7 | Luck7主题 | 25M |
| `perfectcurve` | 完美曲线 | 完美曲线主题 | 15M |
| `Aquamarine` | 鸿蒙-润泽 | 鸿蒙-润泽主题 | 2.0M |
| `AmethystLake` | EMUI-紫晶湖 | EMUI-紫晶湖主题 | 2.2M |
| `GoldenBeach` | EMUI-碧海金沙 | EMUI-碧海金沙主题 | 1.8M |
| `LightWings` | EMUI-光之翼 | EMUI-光之翼主题 | 1.7M |
| `Nebulae` | EMUI-宇宙之眼 | EMUI-宇宙之眼主题 | 2.0M |
| `StarrySky` | EMUI-星夜彩绘 | EMUI-星夜彩绘主题 | 2.0M |
| `Reconstruction` | EMUI-未来之翼 | EMUI-未来之翼主题 | 1.9M |

> 后 7 个 EMUI 命名主题在 MIUI 装配链路中同样有效（仅为主题样式，与华为 hwt 流程无关），全部保留。`HyperOS` 项在主题选择页附注“需配合 HyperOS 桌面”。

---

## 5. 生成流程

### 5.1 前置检查与资源准备（点生成按钮后自动进行）

```
1. 校验配置：主题 ID 合法；输出目录有效（授权失效 → “目录授权已失效，请重新选择”）
2. 资源检查（本地索引 commit SHA vs GitHub 最新 commit SHA）：
   - 无缓存或落后 → 下载/增量更新，面板显示状态“正在下载图标资源”
     + 进度条与“已下载 / 总大小 MB”进度文本（无逐行日志）
   - 已最新 → 状态“主图标仓库没有更新，无需下载”
3. 完整性：下载时按 blob SHA 逐文件校验；不一致 → “文件下载损坏，请重新尝试下载”
```

下载/装配以前台服务通知承载（§11），支持后台运行。**不做独立的连通性预检**：原版以 HEAD 探测第三方对象存储作为网络门禁，该端点停止响应 HEAD 后拖垮全部功能（§3.3）；本 APP 直接请求真实资源端点，按实际失败进入 §5.4 处理。

### 5.2 装配规则（与原版 `pack()` 等价；工作目录为私有临时目录）

```
1. 基础库：repo/icons/**                     → icons/res/drawable-xxhdpi/**
2. 主题覆盖（同名覆盖，主题优先）：
   themes/<ID>/icons/**                      → icons/res/drawable-xxhdpi/**
   themes/<ID>/layer_animating_icons/**      → icons/layer_animating_icons/**
3. 共用动态图标（主题 ∉ {lrone, luck7}；原为服务端 CI 合并，本地复刻）：
   themes/common/layer_animating_icons/**    → icons/layer_animating_icons/**（同名覆盖）
4. denylist 过滤：
   = all       → 删除整个 layer_animating_icons/  状态 “正在禁用所有动态图标…”
   = 包名集合  → 逐个删除对应目录，状态 “正在禁用 {N} 个应用的动态图标…”
   = 空        → 不处理
5. 遮罩：icons/transform_config.xml = V1 或 V2（§6.3）
6. 自定义图标（开关开启），状态 “正在导入自定义图标…”：
   静态图标/*   → icons/res/drawable-xxhdpi/
   动态图标/*   → icons/layer_animating_icons/
   高级附加/*   → 产物根 addons/ 暂存（模块产物保留于此，安装时由 customize.sh 按文件名展平；
                  MTZ 在步骤 8 后合并外壳时按文件名递归展平到产物根，子目录仅作分类，见 §6.2）
7. 扁平 png 分离（仅模块执行）：
   icons/res/drawable-xxhdpi/ 顶层扁平 *.png（含主题与自定义静态图标）
   → 产物根 miui_mod_icons/（目录形分层图标留在 res；
   分离在全部 res 来源并入之后执行；MTZ 不分离，扁平 png 留在 icons 内）
8. icons/{res/, layer_animating_icons/, transform_config.xml} 打为 zip，命名 icons
```

一条必须注意的原版怪癖（已在 `module.sh` 逐行核实，实现不得“顺手修正”）：

- **denylist 盖不住自定义动态图标**：原版顺序为“过滤（第 205 行）→ 导入自定义（第 207 行）”，自定义动态图标在过滤**之后**并入 `layer_animating_icons/`，已禁用的包名若存在于自定义目录会被重新加入，“禁用全部”亦然。该顺序经交叉确认为既定设计而非错误：PerfectIconsPacker 的合并顺序与之相同（合并默认 → 静态禁用 → 动态禁用 → 自定义静态 → 自定义动态 → 遮罩 → 附加），其文档明言“禁用先于自定义生效，因此自定义可以重新提供被禁用的图标”“禁用删除该 id 的全部静态形态，自定义只覆盖其中一层”（《主题格式》 https://github.com/YifePlayte/PerfectIconsPacker/blob/main/docs/THEME_FORMAT.md 、《自定义模型》 https://github.com/YifePlayte/PerfectIconsPacker/blob/main/docs/CUSTOMIZATION.md ）——禁用作用于默认携带内容、自定义在其后覆盖，两者是不同层次的操作。予以保留；在禁用动态图标页对此例外作明确提示（§10.5）。

原版另一处怪癖（步骤 7 对 MTZ 同样执行）属原版错误，已修正、不再保留：原版 `pack()` 在主流程中以无参形式调用（`module.sh` 第 384 行），函数内 `$1` 为空，`[ "$1" != mtz ]` 恒为真，分离无条件执行；修正后 MTZ 产物不含 `miui_mod_icons/`，恰还原该判断的本意（修正依据见 §6.2 修正 3）。

装配各步骤以状态文本逐段显示（§10.6），无“请稍候”字样：`正在导出{装配名}…` →（禁用/导入步骤，如适用）→ `正在打包图标资源…` →（MTZ：`正在合并 MTZ 外壳…`）→ `正在打包产物…` → `正在保存产物` → 结果文本。

### 5.3 输出

- 目录：用户所选 SAF 输出目录（默认公共 Download `/sdcard/Download`，见 §9.3“输出目录”行）
- 命名（时间 = 设备时区）：
  - MTZ：`{装配名}完美图标补全-{MMddHHmm}.mtz`
  - 模块：`{装配名}模块-{MMddHHmm}.zip`
- 完成提示：`MTZ 主题包已保存至 {显示标签}` / `模块已保存至 {显示标签}`（显示于任务面板并出现“打开文件”按钮；完成通知带“打开”动作；显示标签为 `Download/文件名` 或 SAF 目录下的 `目录/文件名`，非文件系统绝对路径）

### 5.4 错误矩阵

| 场景 | 行为 |
|---|---|
| 无网络 / GitHub 不可达 | `未检测到网络连接，取消操作。请检查网络后重试` + 重试 |
| GitHub API 403（限额） | `X-RateLimit-Remaining: 0` 时判定为限额耗尽：未配令牌 → `GitHub 匿名 API 限额已用完，请稍后重试`；已配令牌或经镜像转发 → `GitHub API 限额已用完，请稍后重试`（镜像共享池不受本 APP 控制）。稍后重试；本地已有缓存时询问是否使用缓存继续。其他 403（镜像自身策略等）按普通失败重试 |
| GitHub API 401（令牌无效） | `GitHub 令牌无效或已过期`，快速失败不重试 |
| 下载中断/损坏 | `文件下载损坏，请重新尝试下载`；已完成文件保留，下次只补缺失（按 blob SHA） |
| 输出目录授权失效 | `目录授权已失效，请重新选择` |
| 用户取消 | 终止 + 清理临时文件，不产半成品；已下载缓存保留 |

---

## 6. 产物规格

### 6.1 Magisk 模块 zip

```
module.zip
├─ icons                       ← §5.2 装配产物（zip）
├─ miui_mod_icons/<包名>.png   ← 步骤7 分离的扁平静态图标（主题 + 自定义）
├─ addons/**                   ← 自定义“高级附加”（可选）
├─ module.prop
├─ post-fs-data.sh
├─ customize.sh
└─ META-INF/com/google/android/
   ├─ update-binary
   └─ updater-script           ← 仅一行：#MAGISK
```

**module.prop（7 行）**：
```
id=MIUIiconsplus
name=MIUI 完美图标补全
author=@PedroZ
description=使用{装配名}并补全MIUI完美图标
version={设备时区 yyyyMMddHHmm}
theme={装配名}
themeid={主题ID}
```

**post-fs-data.sh**：全文见 `app/src/main/assets/post-fs-data.sh`（与原版 `module.sh` heredoc 逐字节一致，由 TemplateIntegrityTest 校验；作用：清空主题商店图标缓存目录并加 `chattr +i` 防重建）

**update-binary**：全文见 `app/src/main/assets/update-binary`（与原版 heredoc 逐字节一致；标准 Magisk 模块安装器，要求 Magisk v20.4+）

**customize.sh 行为要点（文本模板，原文 = `docs/original-app-scripts/local-scripts/misc/module.sh` 中 `module_files()` heredoc）**：
- `SKIPUNZIP=1`；横幅输出（“完美图标补全计划 / Perfect-Icons-Complement-Project / 本模块下载自【完美图标计划】APP / 在酷安搜索【完美图标计划】获取更多信息 / QQ群：561180493 / Telegram: miuiicons”及英文对照行）
- 校验 Android ≥ 10、`ro.miui.ui.version.code` ≥ 10，不满足 `abort`
- 解析 `/system/media`（支持 symlink：`MEDIAPATH=system$(realpath /system/media)`，普通目录为 `system/media`）；`REPLACE=/{MEDIAPATH}/theme/miui_mod_icons/dynamic`
- `unzip -oj "$ZIPFILE" icons -d $MODPATH/$MEDIAPATH/theme/default/`；`miui_mod_icons/*` → `$MODPATH/$MEDIAPATH/theme/miui_mod_icons/`；`addons/*` → `$MODPATH/$MEDIAPATH/theme/default/`；`module.prop`、`post-fs-data.sh` → `$MODPATH/`
- 写 22 字节空 ZIP（EOCD：`50 4B 05 06` + 18 字节 `00`）到 `$MODPATH/$MEDIAPATH/theme/default/dynamicicons`
- `set_perm_recursive $MODPATH 0 0 0755 0644`；`rm -rf /data/system/package_cache/*`；提示“√ 安装成功，请重启设备”

注：HyperOS 4 的模块形态与原版不同——重制版 PerfectIconsPacker 将动态图标拆分为独立的 `dynamicicons` 归档、以 `theme_staging/` 暂存并整体 `mv` 安装、不设 `SKIPUNZIP`、要求 Android 13+（其《主题格式》与《架构》文档：https://github.com/YifePlayte/PerfectIconsPacker/blob/main/docs/THEME_FORMAT.md 、https://github.com/YifePlayte/PerfectIconsPacker/blob/main/docs/ARCHITECTURE.md ）。本 APP 模块沿用原版结构（动态图标在 `icons` 内、`unzip` 逐条目安装、Android 10+），两者面向的系统代次与安装机制不同，不互为规格。

### 6.2 MTZ 主题包

```
{装配名}完美图标补全-{MMddHHmm}.mtz   (= zip)
├─ icons               ← §5.2 装配产物（res 含扁平 png 平铺 + 分层目录 + layer_animating_icons + transform_config.xml）
├─ <高级附加文件>       ← 自定义“高级附加”按文件名递归展平到根作为主题组件（可选，子目录仅作分类、不进入产物）
├─ com.miui.home       ← 22 字节空 zip（裸 EOCD；高级附加提供的同名完整可读 zip 优先）
├─ description.xml     ← 取 repo/addition/mtz/description.xml，字符串“themename”全部替换为装配名
├─ preview/**          ← 原样（12 张预览图）
└─ wallpaper/**        ← 原样（default_wallpaper.png）
```

注：官方设计师格式的动态图标位于 `icons/fancy_icons/<包名>/`（`manifest.xml` 结构，见官方语法文档 https://zhuti.designer.xiaomi.com/docs/grammar/ ）；本计划的动态图标沿用系统默认主题的 `layer_animating_icons/<包名>/<层>/`（quiet/fancy 分层）机制，两者是 `icons` 模块内两套不同的动态图标体系。

description.xml（仓库模板，`uiVersion=10`，title 为 `themename&MIUI完美图标补全`，含项目介绍与图标绘制者署名 @潮留美海かり @PedroZ @Yife Playte @Eirperuier @Mastiff Li）原样使用；服务端原件样本见 `docs/sample-outputs/mtz.tar.xz`（其中的 `com.miui.home` 仍为文本桩，装配时替换为空 zip）。完成提示附警示：`需要设计师账号或主题破解才能导入并使用。`（与保存结果同屏多行显示）

**修正说明（原版 MTZ 打包逻辑存在格式错误，已修正）**：真实 MTZ 的根目录由 `description.xml`、`preview/`、`wallpaper/` 与一组无扩展名的组件 zip（`icons`、`com.miui.home`、`com.android.systemui`、`framework-miui-res` 等）构成——该形态为官方组件清单与全部参考实现的共同结论，导入器对未知目录/条目的行为无实证。修正依据分级：

- **官方文档明示**：《主题入门教程》（https://zhuti.designer.xiaomi.com/docs/themePage/guide/ ）的“编辑组件-主题包模块对应表”与《图标变换与滤镜》（https://zhuti.designer.xiaomi.com/docs/blog/iconTransformationFilter.html ）的图标命名规则：主题包目录即根目录组件清单（无 `addons` 条目，国际版指南 https://in.zhuti.designer.intl.xiaomi.com/tutorial/guide/ 同）；静态图标置于 `icons/res/drawable-xxhdpi/`，以包名或 Activity 命名
- **参考实现行为一致**：均把扁平 png 写入 icons 内——PerfectIconsPacker（https://github.com/YifePlayte/PerfectIconsPacker ，代码与测试）、IconPack-to-MTZ（https://github.com/Adiker/IconPack-to-MTZ ，无高级附加功能，作为 icons 结构与真机导入的独立证据：其文档报告独立 MTZ 已在 HyperOS 设备导入成功）、iconpack2mtz（https://github.com/Ra1nify/iconpack2mtz ）、IconEditor（https://github.com/Inefy-03/IconEditor ）、mtz-extractor（https://github.com/ryshaal/mtz-extractor ）；高级附加写根目录的直接证据仅 PerfectIconsPacker 的代码与测试，IconEditor（原样保留导入 MTZ 的全部根条目）与 mtz-extractor（逐顶层目录打包为根条目）以“根目录条目即组件”的行为间接支持。注意 PerfectIconsPacker 自身文档与代码在附加路径上相互矛盾（模块文档写按文件名平铺并报同名错、MTZ 文档写 `addons/` 保留子目录，代码两处均保留相对路径），本 APP 不采信其嵌套附加行为、以原版模块安装行为为准（见修正 1）
- **原版模块行为实测**：本地复核 `unzip -oj addons/*` 展平语义（通配符跨目录层级、按文件名落地）

三处修正（①为推断性格式修正，②为占优的推断性选择，③有官方文档直接支持）：

1. **高级附加**：原版置于根目录 `addons/`，官方组件清单无此条目、参考实现均不使用，推断其无法生效（模块侧 `customize.sh` 会把 `addons/` 下全部文件按文件名展平进 `theme/default/`，MTZ 导入路径无此搬运）——修正为按文件名递归展平到根，子目录仅作分类、不进入产物；与 PerfectIconsPacker 代码“保留子目录”不同（其嵌套路径同样不是已知组件形态）。展平后的文件名（大小写不敏感）命中保留名——`icons`、`description.xml`、`miui_mod_icons`、`preview`、`wallpaper`、`addons`、`module.prop`、`META-INF`、`system`、`dynamicicons`——或不同子目录出现同名文件时报错中止；`com.miui.home` 不在保留名内，同名附加按修正 2 优先替换。系统侧 `theme/default/` 与 MTZ 根目录是同一套组件模型（`icons` 以归档文件落地，`module-backup-filelist.txt` 实物可证）
2. **com.miui.home**：外壳原件为 4 字节文本桩（“test”），非合法 zip——修正为清除桩后写入 22 字节空 zip（裸 EOCD，与原版 `customize.sh` 写入 `dynamicicons` 桩同款手法，实物备份佐证系统侧容忍空 zip 归档）。该组件的必要性属推断而非实证：PerfectIconsPacker 携带空 zip 并称其用于桌面主题识别，IconPack-to-MTZ 不携带亦可在其测试设备导入；选择空 zip 是因其在各场景下不劣于文本桩（合法、保留外壳结构、可被附加替换）。高级附加提供的同名文件优先，但须为完整可读 zip——装配时完整读取每个条目以触发本地头与 CRC 校验，非有效文件报错中止（避免把原版文本桩的问题原样带回）
3. **扁平 png**：不再分离到根目录 `miui_mod_icons/`（§5.2 步骤 7）——官方文档明示静态图标在 `icons/res/drawable-xxhdpi/` 且全部参考实现一致（最强证据）；`miui_mod_icons/` 是模块安装到 `theme/` 下的系统路径、MTZ 内无已知读取方（推断）

模块产物不受上述修正影响，仍与原版逐文件一致。

### 6.3 遮罩配置（两版；装配取仓库缓存文件，随资源同步下载）

- **V1（MIUI 12–14）**：`SupportLayerIcon=true` + `ConfigIconMask`，全文见仓库 `addition/transform_config.xml`（与原版 assets 逐字节一致）
- **V2（HyperOS）**：`SupportLayerIcon=true` + `UseDynamicIcon=true` + `ConfigIconMask`，全文见仓库 `addition/transform_config2.xml`
- **默认值**：按 `ro.miui.ui.version.code` 推断：≤ 14 → V1（关），> 14 → V2（开）。通过 `android.os.SystemProperties` 反射读取；失败时默认 V1（关），并在开关副标题提示用户自行确认
- **模板来源策略**：两个遮罩 xml 在资源下载清单内（§4.1），装配直接读仓库缓存，模板单一来源（不内置 APK）；缓存中遮罩缺失时装配报错“遮罩配置缺失，请重新下载资源”。`customize.sh`、`post-fs-data.sh`、`update-binary` 三个模块模板在仓库中不存在对应物（仓库 `addition/module/` 是原版服务端打包骨架，与原版 `module.sh` heredoc 生成的模板不同源），必须随 APK 内置；模块装配分别从仓库缓存读取所选遮罩、从 APK 内置资源读取三个安装脚本，不能将脚本当作遮罩读取或报告遮罩缺失。MTZ 不读取模块安装脚本。本地有完整资源快照时，两种装配均可全程离线进行

### 6.4 验收口径

- **模块产物**：同配置下与原 APP 产物逐文件 diff 一致（允许时间戳差异）；实物对照清单：`docs/sample-outputs/icons-zip-filelist.txt`（9440 条）与 `module-backup-filelist.txt`（爱玩机备份，现存唯一实物样本）
- **MTZ 产物**：无实物对照，按 §6.2 修正后格式验收（原版 MTZ 逻辑存在格式错误，修正依据分级与参考链接见 §6.2 修正说明，行为基准参照 PerfectIconsPacker 的 `MtzBuilder` 及其测试 `ThemeBuildTest`），以单元测试复核 + 破解环境实际导入为准
- mtz 可被破解环境主题商店导入；模块 zip 可被 Magisk v20.4+ / KernelSU 刷入

---

## 7. 配置项（持久化）

| 配置 | 默认 | 原版对应 | 说明 |
|---|---|---|---|
| 主题 ID | `default` | `theme_files/theme_config`（`sel_theme=`） | |
| 新版遮罩 | 按 `ro.miui.ui.version.code` 推断：≤14 → 关（V1），> 14 → 开（V2）；读取失败默认关 | `theme_files/new_transform_config` | §6.3 |
| 自定义图标开关 | 关 | `theme_files/addon_config`（`addon=`） | |
| denylist | 空集 | `theme_files/denylist`（文件不存在 = 空；`all` = 全部；包名以空白分隔） | |
| 输出目录 | 公共 Download | `zipoutdir_config` | SAF 选目录 |
| GitHub 访问令牌 | 空（匿名） | 无 | 无权限令牌（仅公开数据只读）即可；明文保存；仅发送官方域名，镜像不附带 |
| 镜像开关 | 关（直连） | 无 | 开启后 API 与文件下载可分别配置镜像前缀；说明始终显示，开关行不随选择重建 |
| 镜像预设 | `gh-proxy.com` | 无 | 内置候选按“双源在前（含主站 Atom 通道）、仅文件与 jsDelivr 系在后”排序（含域名替换式双源 6github 与 jsDelivr 官方节点及社区镜像），另含“自定义”项（填地址前缀，基址含仓库路径，对话框按字段给出对应形态填写示例） |
| 资源缓存 commit SHA / 日期 | 空 | `iconsrepo.ini` | 本地索引 |
| 公告缓存 | 空 | `theme_files/announce.txt` | §9.1 |
| 更新日志缓存 | 空 | changelog2.xml（原版仅暂存） | 持久缓存最近一次结果，离线可看 |

---

## 8. UI 设计规范（Material Design 3）

原版界面由 kr-script 引擎渲染（位图行图标 + 自绘开关/选择器）。新 APP 不复用其任何视觉元素与布局语言（原 `local-scripts/pic/` 位图全部弃用），以 Material Design 3 组件体系重新设计；具体视觉参数（配色值、字号、字重、间距等）遵循 M3 规范与系统动态取色，开发时按 M3 指南取默认值即可，本文档不逐项规定。

### 8.1 主题

- 基础主题 `Theme.Material3.DayNight.NoActionBar`，深浅色跟随系统
- Android 12+ 默认动态取色（Dynamic Color），低版本使用 M3 内置色板
- 不得出现 `android.support.*` 视觉类

### 8.2 组件映射规则（替代 kr-script 元素）

| 原版 kr-script 元素 | 新 APP M3 组件 |
|---|---|
| 列表行（位图图标+标题+副标题+箭头） | Two-line ListItem：leading 为 Material 线性图标（tint 主题色），当前值或说明文本置于副标题位置（§9.3），trailing 为 `chevron_right` / `open_in_new` 图标，或 `MaterialSwitch` |
| picker（弹窗单选） | 独立选择页面（RadioButton 列表，§10.2） |
| param type=app 多选 | 独立选择页面（Checkbox 列表 + 搜索，§10.3） |
| switch（自绘） | `MaterialSwitch` |
| action + confirm | ListItem / FilledButton + `MaterialAlertDialog` 确认 |
| group 分组标题 | 卡片上方的分组小标题（onSurfaceVariant） |
| 页面容器 | 每个功能分组 = 一张 `MaterialCardView`（Filled 样式），卡内为 ListItem 列表 |
| 任务面板 | `BottomSheetDialogFragment`（带 DragHandle，标题 + 状态文本 + 进度，§10.6） |
| 文字提示 | 卡片正文 |

二级页面分组卡片由 `view_feature_group.xml` 定义，使用 `Widget.MiuiPerfectIcons.Card.Group` 样式，并仅声明布局中使用的 Android 命名空间。

**图标资源（Material Symbols）**：全部界面线性图标取自 Google 官方 [google/material-design-icons](https://github.com/google/material-design-icons) 仓库的现成 Android VectorDrawable，路径 `symbols/android/<图标名>/materialsymbolsoutlined/<图标名>_24px.xml`（如 `ic_palette.xml` ← `palette_24px.xml`）。使用方法：在仓库内找到目标图标（可在 Material Symbols 官网检索图标名），把该 XML 原样复制为 `app/src/main/res/drawable/ic_<图标名>.xml`，**不修改任何内容**（坐标系、pathData、tint 均为官方原件），随后在 `MaterialSymbolsIntegrityTest` 中登记该文件的 SHA-256 摘要（`sha256sum` 即可），使图标与官方资源保持逐字节一致并受回归测试保护。品牌图标（GitHub、微信、Telegram、QQ）取自 Apache 2.0 许可的 Remix Icon v4.6.0，同样以官方原件加摘要登记的方式管理。禁止手绘、转换或编辑图标 pathData。

### 8.3 布局骨架

- 首页：TopAppBar（仅标题“MIUI 完美图标计划”，无菜单动作）+ 纵向滚动内容
- 二级页面：TopAppBar + 返回箭头，内容随页面而定；页面内第一个内容元素若为分组小标题，其上间距为 4dp（与顶栏的额外间距由内容容器自身 padding 承担）；前方已有卡片或其他内容的分组小标题以 20dp 上间距分隔
- 结果反馈：轻量操作（清理缓存、导入等）用 Snackbar；任务结果在任务面板内展示，面板关闭时由完成通知承载；长任务：前台服务通知。任务运行期间首页内容顶部显示“查看正在执行的任务”按钮（点击重新打开任务面板；任务运行中再点生成按钮同样直接打开面板而不新起任务）
- 系统栏：edge-to-edge，状态栏/导航栏透明，内容自动 inset

---

## 9. 首页 UI 规格

```
┌────────────────────────────────────────┐
│ TopAppBar：MIUI 完美图标计划             │  ← 无菜单按钮
├────────────────────────────────────────┤
│ 〔公告卡〕（有内容时才渲染）              │
│ 〔查看正在执行的任务〕（任务运行时才显示） │
├────────────────────────────────────────┤
│ 更新                                    │
│ ┌────────────────────────────────────┐ │
│ │ ▸ 更新日志及检查更新  最新：01-02   │ │
│ └────────────────────────────────────┘ │
├────────────────────────────────────────┤
│ 配置                                    │
│ ┌────────────────────────────────────┐ │
│ │ 选择主题图标        MIUI 经典    › │ │
│ │ 图标自定义          未启用       › │ │
│ │ 禁用动态图标        未启用       › │ │
│ │ 输出目录   当前目录：Download（默认） › │ │
│ │ 是否启用新版遮罩          [Switch] │ │
│ └────────────────────────────────────┘ │
│ 〔说明文字〕                            │
│ ┌──────────────┬─────────────────────┐ │
│ │ 生成 MTZ 主题包 │ 生成 Magisk 模块  │ │
│ │ 需设计师账号或主题破解导入│ 自行刷入 │ │
│ └──────────────┴─────────────────────┘ │
├────────────────────────────────────────┤
│ 文档                                    │
│ ┌────────────────────────────────────┐ │
│ │ ▸ 预览主题图标                     │ │
│ │ ▸ 完美图标项目介绍                 │ │
│ │ ▸ HyperOS/MIUI 使用指南            │ │
│ │ ▸ 自定义图标使用指南               │ │
│ │ ▸ 完美图标适配指南                 │ │
│ │ ▸ 3.0 版本更新说明                 │ │
│ │ ▸ 完美图标 GitHub 地址             │ │
│ │ ▸ APP GitHub 地址                 │ │
│ │ ▸ Telegram 频道                    │ │
│ │ ▸ Telegram 群组                    │ │
│ │ ▸ QQ 群：561180493                 │ │
│ │ ▸ 隐私协议                         │ │
│ │ ──────────────                     │ │
│ │ ▸ 清理缓存（副标题：当前缓存大小）   │ │
│ │ ▸ 关于                             │ │
│ └────────────────────────────────────┘ │
└────────────────────────────────────────┘
```

### 9.1 区块①：公告卡片

- 数据源（原版同一接口，实测仍在响应）：`https://miuiicons-generic.pkg.coding.net/icons/files/announce3000.txt?version=latest`
- 行为：每次进入首页异步拉取；成功且非空 → 覆盖本地缓存并显示；失败 → 显示上次缓存；无任何缓存 → 整张卡片不渲染（原版 `announce_check.sh` 语义）；不提供关闭按钮
- 样式：Filled 卡片，顶部小字标签“公告”（primary 色），正文自动换行

### 9.2 区块②：更新入口

M3 ListItem（leading：`history`），标题“更新日志及检查更新”（副标题沿用原版：“查看近期更新日志，检查更新，获取更新通知”），行内动态值（显示于副标题位置，布局无 trailing 文本槽）：
- 有缓存：`最新：{yyyy-MM-dd}`；检测到资源更新时 `最新：{日期} · 有更新`
- 从未加载：`轻触查看资源更新情况`

点击 → 更新日志页（§10.1）。

### 9.3 区块③：配置卡

一张 Filled 卡片内含六行：

| ListItem | leading | 当前值（副标题位置；trailing 仅图标） | 交互 |
|---|---|---|---|
| 选择主题图标 | `palette` | 值：`{显示名}`（实时，无前缀）+ chevron | → 主题选择页（§10.2） |
| 图标自定义 | `tune` | 值：`未启用`/`已启用` + chevron | → 图标自定义页（§10.4） |
| 禁用动态图标 | `animation` | 值：`未启用`/`已选 N 个应用`/`已禁用全部` + chevron | → 禁用动态图标页（§10.5） |
| 输出目录 | `folder` | 值：`当前目录：{路径}`（默认 `Download（默认）`；授权失效显示 `目录授权已失效，请重新选择`）+ chevron | SAF `OPEN_DOCUMENT_TREE` 选目录并持久化授权 |
| 网络设置 | `language` | 值：`直连 GitHub`/`已设置令牌`/`已启用镜像`/`已启用镜像与令牌` + chevron | → 网络设置页（§10.9） |
| 是否启用新版遮罩 | `layers` | `MaterialSwitch` | 切换即持久化；默认值见 §6.3 |

遮罩行副标题（固定文案，基于原版修订，仅补 pangu 空格）：
> 如果你使用的是 HyperOS 的新桌面，请开启这个选项。如果是 MIUI 那就关闭。如果图标大小异常，可尝试切换。

### 9.4 区块④：生成按钮

按钮上方说明文字（基于原版修订，补 pangu 空格）：
> 提供适用于 Magisk 或 KernelSU 的模块，提供 mtz 主题包（需要设计师账号或主题破解）。首次使用前请仔细阅读使用指南。产物适用于 Android 10 及以上版本的 HyperOS / MIUI。

两枚等宽并排 FilledButton（无按钮下小字，说明文字仅位于按钮上方）：

- 生成 MTZ 主题包
- 生成 Magisk 模块 ZIP

点击任一按钮 → `MaterialAlertDialog` 确认（基于原版修订：本 APP 为本地生成，“下载”改为“将生成”）→ 进入生成流程（§5），弹出任务面板（§10.6）：
- MTZ：`确认上述配置无误后，将生成 mtz 主题包，需要设计师账号或主题破解才能应用。`
- 模块：`确认上述配置无误后，将生成模块，可自行安装。`
- 追加：`产物适用于 Android 10 及以上版本的 HyperOS / MIUI。首次下载约 100~130 MB，下载缓慢或失败时可尝试切换网络。`

系统要求不做强制校验（模块模式的版本校验在 customize.sh 安装时进行）。

### 9.5 区块⑤：文档卡

一张 Filled 卡片，内含外链 ListItem（leading 各配 Material 线性图标，trailing `open_in_new`；系统浏览器 / Custom Tabs 打开，链接清单见 §12）：

预览主题图标 · 完美图标项目介绍 · HyperOS/MIUI 使用指南 · 自定义图标使用指南 · 完美图标适配指南 · 3.0 版本更新说明 · 完美图标 GitHub 地址 · APP GitHub 地址 · Telegram 频道 · Telegram 群组 · QQ 群：561180493 · 隐私协议

卡片底部直接附加两行（无分隔线）：

- **清理缓存**：副标题实时显示 `当前缓存大小：{大小}`（如 `当前缓存大小：128.4 MB`）；点击 → 确认对话框（沿用原版：`当图标安装后出现奇怪的问题时，可以尝试清理缓存，清理后下次安装时会重新下载资源`）→ 删除本地资源缓存 → Snackbar `缓存清理完成`
- **关于** → 关于页（§10.8）

---

## 10. 二级页面 UI 规格（均为独立页面，M3）

### 10.1 更新日志页（标题：更新日志及检查更新）

```
TopAppBar（返回）
[信息卡] 已适配第三方应用图标：{N} 个
         本地图标库版本：{SHA 前 7 位}
         本地图标库同步日期：{yyyy-MM-dd}
[检查更新]（FilledButton）
──────────────────────────
▸ 订阅新版本通知
  通过微信订阅后，有新版本会推送通知
──────────────────────────
（下拉刷新；倒序列表，每条：）
  {yyyy.MM.dd HH:mm}
  {提交信息首行}
  {提交信息其余行（如有）}
  by {作者}
  （trailing：open_in_new 图标，有链接才可点击）
```

- **已适配数量**：统计本地缓存 `icons/` 一级子目录数（与原版 CI 口径一致）；无缓存显示 `--` 并附“下载图标库”按钮（触发资源下载）
- **检查更新**（输出格式基于原版修订，无前缀与缩进）：恒三行 `主图标仓库{已是最新|有新版本}` / `旧版本：{SHA，无缓存时显示 无}` / `新版本：{SHA}`；本地已有缓存且落后时出现“更新资源”按钮（增量拉取）
- **订阅新版本通知**（外链）：`https://iconsx.pedroz.eu.org/push.html`
- **日志列表**：沿用原版 changelog2.xml 接口（§3.3 实测仍可用），结果持久缓存、离线可看（§7）；每条 = 时间（设备时区）+ 提交信息（首行为标题，其余行为正文）+ `by {作者}`；带链接的条目（trailing `open_in_new`）点击 → 浏览器打开 commit 详情页，无链接条目不可点击

### 10.2 主题选择页（标题：选择主题图标）

- 列表：19 项，每项 = `MaterialRadioButton` + 显示名（单行 ListItem）；`HyperOS` 项附注“需配合 HyperOS 桌面”
- 交互（与原版 picker auto-off 一致）：点击某项 → 立即选中并保存 → 自动返回首页；首页“选择主题图标”行与生成参数即时刷新；不提供“确定”按钮

### 10.3 应用选择页（标题：选择应用；从禁用动态图标页进入）

- 顶部搜索框：按应用名或包名实时过滤
- 列表：M3 ListItem（leading = 应用图标，标题 = 应用名，副标题 = 包名，trailing = `Checkbox`）
- 数据源：`PackageManager.getInstalledApplications(MATCH_DISABLED_COMPONENTS)`，按应用名排序
- 已在 denylist 中的项预勾选
- TopAppBar 右侧“确定”TextButton（显示已选数，如 `确定（12）`）；系统返回键 = 取消不保存

### 10.4 图标自定义页（标题：图标自定义）

```
○ 启用自定义图标 [MaterialSwitch]
  开启后，导出的主题和模块将包含自定义图标
（启用后以下可见）
[说明卡] 三类目录说明（简化文案，规则详见 §5.2 步骤6）：
   静态图标：支持 包名.png（扁平）或 包名目录（分层），会并入产物的静态图标
   动态图标：整个包名目录连同内部结构一起并入动态图标
   高级附加：模块产物置于根目录的 addons，安装时按文件名展平；MTZ 产物按文件名递归展平到根目录作为主题组件，
   子目录仅作分类；占用保留名、同名文件或 com.miui.home 附加非有效 zip 时报错中止
   自定义目录：{当前目录路径，未选择显示“未选择”}
▸ 导入静态图标文件（选择一个或多个 PNG 文件）
▸ 导入静态图标目录（适用于分层图标）
▸ 导入动态图标目录（保留目录内的全部文件）
▸ 导入高级附加文件
▸ 下载图标存档   保存到自定义图标目录
▸ 查看已存档图标（GitHub 外链）
```

- 目录机制兼容原版：`/sdcard/Documents/MIUI完美图标自定义/` 下设 `静态图标`、`动态图标`、`高级附加` 三个子目录（+ `.nomedia`）；首次启用经 `OPEN_DOCUMENT_TREE` 一次性授权该目录，原版用户文件免迁移直接读取
- **装配时对三类内容均整体递归复制、不做结构校验**（与原版 `cp -rf` 一致，高级附加的展平及保留名/同名/com.miui.home zip 校验除外）——动态图标在仓库中的实际样本即含 `0/`、`1/`、`quiet/`、`config.xml` 等复杂递归结构，任何“必须含 0.png + 1.png”之类的校验都会拒绝现有有效样本；静态图标目录下扁平 png 与分层目录可并存，扁平 png 在装配步骤 7 被分离至 `miui_mod_icons/`（仅模块；MTZ 留在 icons 内）
- 桌面对同一包名的解析优先级为动态分层 > 静态分层 > 单张（参考实现 PerfectIconsPacker《主题格式》 https://github.com/YifePlayte/PerfectIconsPacker/blob/main/docs/THEME_FORMAT.md 所述，与原版分层/单张两种形态并存的行为一致）：自定义扁平 png 与默认分层图标同名时分层生效、单张不显示但保留在产物中，删除分层后单张重新生效；不改模块行为，仅作说明
- 图标命名遵循官方规则（《图标变换与滤镜》 https://zhuti.designer.xiaomi.com/docs/blog/iconTransformationFilter.html ）：单一桌面图标以包名命名（如 `com.android.camera.png`）；一个 APK 有多个桌面图标时以 Activity 类名命名；两个相同的 Activity 有不同包名时以“类名 @包名”命名；官方图标画布标准为 168x168（系统默认图标画布内实际 156x156）。应用标识的查找优先级（包名 < 类名 < 包名#类名）另见 PerfectIconsPacker《自定义模型》 https://github.com/YifePlayte/PerfectIconsPacker/blob/main/docs/CUSTOMIZATION.md
- 应用内导入：SAF 多选 png 或目录（静态，扁平/分层均可）/ 选目录整体复制（动态，不校验内部结构）/ 任选文件（高级附加）
- 下载图标存档：本地已缓存 → 从缓存 `addition/Icons_Archiving/` 打 zip 导出；未缓存 → 先从 GitHub 下载该目录；产物为自定义图标目录下的 `图标存档.zip`；完成提示：`图标存档已下载到 {显示标签}`
- 查看已存档图标外链：`https://github.com/pzcn/Perfect-Icons-Completion-Project/tree/main/addition/Icons_Archiving`
- 原版“从之前的自定义模块迁移”（root）不做

### 10.5 禁用动态图标页（标题：禁用动态图标）

```
[状态卡] 当前禁用列表：
   （空 → “禁用列表为空”）
   （集合 → 逐行包名；全部 → “所有应用”）
──────────────────────────
禁用部分应用
  {警示长文，基于原版修订：统一“动态图标”用词、补 pangu 空格}
  在下方选择想要禁用动态图标的 app，后续安装时可自动禁用。请按需选择，如需全部禁用，请使用下方的禁用全部，不建议使用列表中的全选功能（会降低安装效率）。列表内会显示全部 app，不代表列表内出现的 app 使用了动态图标。
  再次强调，按需选择，请勿全选！
  [选择应用]（列表行入口，副标题：“支持多选，可按名称或包名搜索”）→ 应用选择页 §10.3
──────────────────────────
禁用所有应用
  [禁用全部]（副标题：“禁用图标库自带的全部动态图标”，带确认：“禁用全部动态图标？”）
  [清空禁用列表]（副标题：“恢复图标库的动态图标”，带确认：“清空禁用列表并恢复图标库动态图标？”）
──────────────────────────
{说明（页面底部）：}
  禁用列表仅作用于图标库自带的动态图标。自定义目录中的动态图标不受影响；如需禁用，
  请从自定义目录中移除。
```

### 10.6 任务面板（生成过程，M3 BottomSheet）

无逐行日志，改为“标题 + 状态文本 + 进度”的形态：

| 元素 | 规格 |
|---|---|
| 标题 | 任务名（“生成 Magisk 模块”/“生成 MTZ 主题包”/“下载图标存档”/“下载图标资源”） |
| 状态文本 | BodyMedium 多行自动换行（onSurfaceVariant），显示当前步骤的详细状态（§5.1/§5.2 步骤文本）；无细分步骤时回退为阶段名（准备中/正在下载图标资源/正在装配/正在保存产物）；失败时显示错误消息；成功时显示结果文本（MTZ 附“需要设计师账号或主题破解才能导入并使用。”警示，换行同屏） |
| 进度 | `LinearProgressIndicator`：待下载文件体积已知时显示确定进度 0–100，存在未知体积或处于准备/装配/保存阶段时显示 indeterminate；仅运行中显示 |
| 进度文本 | `已下载 / 总大小 MB`，仅下载阶段显示 |
| 按钮 | 单行双按钮（左 TextButton + 右 FilledTonalButton），随状态切换：运行中 = 后台运行 / 取消任务；失败（可缓存）= 使用缓存 / 重试；失败（不可缓存）= 关闭 / 重试；成功 = 关闭 / 打开文件 |

“后台运行”收起面板任务转入通知；“取消任务”终止并清理临时文件（§5.4）；“使用缓存”经确认对话框（`GitHub API 限额已用完。是否使用已校验的本地缓存继续生成？`）以缓存继续。

完成：状态文本显示 `MTZ 主题包已保存至 {显示标签}` 等结果并出现“打开文件”按钮；发完成通知（通知带打开动作）；不再弹额外 Snackbar。

### 10.7 系统权限申请场景

仅在需要时（自定义图标目录授权、输出目录 SAF、API 33+ 通知权限）由对应页面触发，首页不弹任何权限。

### 10.8 关于页（标题：关于；从首页文档区底部进入）

1. **页头**：APP 图标（高度随应用名与版本号两行文字自适应）+ 应用名“MIUI 完美图标计划” + 版本号（`版本 {versionName}（{longVersionCode}）`）
2. **原项目致谢**（名单全文照搬原版）：
   - 完美图标计划开发团队：@PedroZ @ZQDesigned @Yife_Playte @YuKongA
   - 图标绘制与主题适配：@PedroZ @Yife_Playte @潮留美海かり @YuKongA @HeMing505 @FEC_Sky @huoyiming @艾欧利娅 @Mastiff_Li @Eirperuier @扫地的喵 @叡煜 @兰博教育 @与病共存 @三滴 @Shahzaib_Ahmed
   - 主题授权与技术支持：@江户川Conan @JellyBeans @栗子汤圆Ria @相见即是缘 @薛定谔の鼠
   - 特别鸣谢：@嘟嘟斯基 @Yife_Playte
   - 捐赠名单：@cnzt66 @E.Ming @heming505 @胡歌_Jay @嘉然今天吃烤串 @禁止吸血 @JKL_0124 @空 @李宗财 @你的瓦龙 @VAY冬冬 @阳阳兮 @樱素华丶
3. **开源信息**：
   - 本项目 GitHub 地址与作者（轻雨 Purainity）
   - MIUI 主题扩展：`https://github.com/purainity/miui-theme-extension`（副标题“MIUI 主题壁纸增强模块，提供主题破解等功能”，同作者出品）
   - 开源许可列表：以项目为单位列出直接依赖、传递依赖与随应用分发的资源（Perfect-Icons-APP（GPL-3.0）、Magisk module_installer.sh、Material Symbols、Remix Icon、AndroidX、Kotlin 等，每项附项目主页链接）

### 10.9 网络设置页（标题：网络设置；从首页配置卡进入）

```
GitHub 访问令牌
  {说明：令牌可将 GitHub API 限额从匿名的 60 次/时提高到 5000 次/时，并降低
   更新检查失败的概率。建议创建无任何权限的令牌（仅公开数据读，足以覆盖
   本 APP 的全部需求且泄露影响最小）。令牌以明文保存在本机，请勿使用高权限令牌。}
  [访问令牌]  值：未设置 / 已设置
──────────────────
镜像
  {说明（始终显示）：镜像由第三方维护，可能随时失效或限速，请自行确认可用性。
   使用 API 镜像时版本和清单由镜像提供，完整性校验不能证明其来自 GitHub 官方，请选择可信镜像。
   GitHub 无法连接时需选同时支持 API 和文件的镜像；测速仅检查文件通道，令牌不会发送给镜像。}
  [使用镜像]（MaterialSwitch，默认关；与其他页共用的两行开关行，副标题
   “默认关闭，开启后 GitHub 请求改经所选镜像”）
  （开启后追加）
  [测速全部镜像]（FilledButton）
  (•) gh-proxy.com        API 与文件下载共用前缀    {测速耗时}
  ( ) github.cnxiaobai.com …（双源候选在前，仅文件候选与 jsDelivr 系在后）
  （……全部候选逐行单选，长按单条测速；见下）
  （选中“自定义”时追加）
  [API 地址前缀]   值：{自定义前缀}/官方默认（api.github.com）
  [文件地址前缀]   值：{自定义前缀}/官方默认（raw.githubusercontent.com）
```

- **开关动效**：开关切换时只重建开关行之后的内容（测速按钮、候选列表、自定义前缀行），开关行与其前的说明不动，MaterialSwitch 的切换动画不被打断；开关行复用图标自定义页的同一布局组件（图标与文案由代码填入）
- **页面内单选**：镜像候选直接以单选列表（MaterialRadioButton + 名称 + 形态说明 + 测速结果列）展示在本页，点选即保存；点选仅更新单选状态与尾部自定义前缀行，列表不重建
- **内置候选**（双源在前，仅文件与 jsDelivr 系在后）：API 与文件共用前缀（gh-proxy.com、github.cnxiaobai.com、gh.noki.icu、github.chenc.dev、ghp.keleyaa.com、gh.nxnow.top，均含主站 Atom 通道）、域名替换式双源（6github：`api.6github.com` + `raw.6githubusercontent.com` + `6github.com` 主站）、仅文件前缀（ghproxy.net、ghfast.top、github.ednovas.xyz、ghproxy.mirror.skybyte.me、github.tianrld.top、github.1ms.xx.kg、github.mlmle.cn）、jsDelivr 引用形态（官方节点 cdn / fastly / gcore / testingcf / quantil / originfastly 与社区镜像 jsd.onmicrosoft.cn、cdn.jsdmirror.com、cdn.jsdmirror.cn、jsdelivr.5210521.xyz）与“自定义”，候选仅作预填选项，APP 不对可用性做任何保证
- **测速**：点击“测速全部镜像”并发（6 线程）下载测速基准——固定 commit 引用的示例图标 `addition/Icons_Addon/icons/miui.icons.example/0.png`（710 字节，耗时主要反映请求延迟而非大文件下载速度；不用分支引用，jsDelivr 的分支引用对本仓库大文件树回源失败）——逐行回显耗时（`{N}s`，右侧对齐便于横向比较）；超时与连接失败分别显示“超时”/“失败”；以 PNG 魔数校验响应内容，防止把镜像错误页计入成功；测速期间按钮禁用并显示“正在测速…”；长按任意候选行可单独测速该行（与全量测速互斥，进行中会提示）；测速仅经当前各候选的 raw 通道且不带令牌
- **自定义项的测速语义**：自定义前缀留空时按配置回退规则走官方直连，测出的是官方直连的延迟，可作为对比基线
- 令牌行点击 → 输入对话框（令牌占位提示 `ghp_… 或 github_pat_…`，中性行“清除”）→ 保存前经官方 API 实测令牌有效性：验证对话框为标准 M3 形态（标题“正在验证令牌…” + 居中的 Material LoadingIndicator + 取消与“跳过验证并保存”按钮，返回键可取消）；跳过时立即保存并提示尚未验证，取消则放弃保存，两者均不再处理验证结果，令牌验证独立于页面其他后台操作；成功 → 保存并回显状态；失败 → 展示错误原因，可选“仍要保存”（仅经镜像联网时无法验证，但不阻断保存）
- 自定义前缀输入对话框：API 与文件分别给出对应形态的填写示例（前缀式 / 域名替换式 / jsDelivr `@` 式，见字符串 `network_mirror_api_hint`、`network_mirror_raw_hint`）；非法地址（非 http/https 开头）当场拦截提示；允许 HTTP 明文镜像，令牌不发往镜像；留空回退官方直连
- 令牌对镜像不生效：镜像转发 API 时使用镜像自身的额度池（§4.2 镜像），令牌仅提升官方直连请求的限额

---

## 11. 通知规格

- 下载/装配任务以前台服务运行（`dataSync` 类型），通知：标题与任务面板任务名一致——“正在下载图标资源”/“正在生成 MTZ 主题包”/“正在生成 Magisk 模块”/“正在下载图标存档”，进度条（体积已知时显示百分比与大小，未知时显示不定进度），完成 → 可点击打开产物，失败 → 重试 Action
- API 33+ 运行时申请通知权限（§10.7 场景触发）

---

## 12. 外链资源表

| 条目 | URL |
|---|---|
| 预览主题图标 | `https://icons-demo.pedroz.eu.org/` |
| 完美图标项目介绍 | `https://www.yuque.com/pedeoz/icons/crit9tn6d1334zt0` |
| HyperOS/MIUI 使用指南 | `https://www.yuque.com/pedeoz/icons/er8ige3ldvc7wn1n` |
| 自定义图标使用指南 | `https://www.yuque.com/pedeoz/icons/bg668dqpli7brqgi` |
| 完美图标适配指南 | `https://www.yuque.com/pedeoz/icons/ao7yvw37irgdghr6` |
| 3.0 版本更新说明 | `https://www.yuque.com/pedeoz/icons/xpqqtku1lkacpal4` |
| 完美图标 GitHub 地址 | `https://github.com/pzcn/Perfect-Icons-Completion-Project` |
| APP GitHub 地址 | `https://github.com/pzcn/MIUI-Icons-APP` |
| Telegram 频道 | `https://t.me/miuiicons` |
| Telegram 群组 | `https://t.me/miui_icons_dev` |
| QQ 群：561180493 | `https://qm.qq.com/cgi-bin/qm/qr?k=_92-RGlaiz3Xm-9i8O3USqTSg29EYg44` |
| 隐私协议 | `https://iconsx.pedroz.eu.org/privacy.html`（声明：仅访问 GitHub 与原公告接口，不收集任何数据） |
| 订阅新版本通知（更新日志页） | `https://iconsx.pedroz.eu.org/push.html` |
| 查看已存档图标（图标自定义页） | `https://github.com/pzcn/Perfect-Icons-Completion-Project/tree/main/addition/Icons_Archiving` |

---

## 13. 与原版行为的差异

| # | 原版 | 本 APP | 原因 |
|---|---|---|---|
| 1 | 资源取自 coding 制品库与 icons2 git 仓库（root git pull / APP 内下载 Activity） | GitHub Trees API 清单 + raw 按文件下载（commit SHA 引用，blob SHA 增量） | 原下载通道已不可用（§3.3） |
| 2 | 版本比对 `.ini` 的 `theme_version` 时间戳 / `git_SHA` | GitHub 最新 commit SHA | 数据同源（CI 从该仓库推送） |
| 3 | 完整性校验 md5 | 逐文件 blob SHA（下载即校验） | 等价强度，免单独 md5 |
| 4 | `themes/common/` 由服务端 CI 合并进主题包 | 本地装配时合并（除 lrone/luck7） | 无服务端 |
| 5 | 更新日志读服务器 changelog2.xml（仅暂存内存） | 沿用 changelog2.xml 接口 + 本地持久缓存 | 接口仍可用且持续更新；不额外消耗 GitHub API 限额 |
| 6 | iconscount 由服务器下发 | 本地统计 `icons/` 一级子目录数 | 口径一致 |
| 7 | 应用列表 root shell `pm list package` | PackageManager | 无 root |
| 8 | 安装模块 / 重启 / Xposed / Beta / 下载兼容模式 | 全部移除，产物到文件即止 | §1.2 |
| 9 | 以 HEAD 探测对象存储作为网络门禁 | 不做连通性预检，直接请求资源端点 | 原门禁单点故障即致 APP 整体失效（§3.3） |
| 10 | 图标存档从服务器 zip 下载 | 从本地缓存导出或从 GitHub 下载，保存位置不变 | 数据同源 |
| 11 | MTZ 高级附加留在产物根 `addons/` | 按文件名递归展平到根（保留名/同名报错，`com.miui.home` 同名可替换） | 原版位置不属 MTZ 组件形态（推断，§6.2 修正 1） |
| 12 | MTZ `com.miui.home` 为 4 字节文本桩（`test`） | 22 字节空 zip，高级附加同名完整可读 zip 优先 | 文本桩非合法 zip（§6.2 修正 2） |
| 13 | MTZ 产物同样分离扁平 png 至 `miui_mod_icons/` | 仅模块分离，MTZ 扁平 png 留在 icons 内 | 官方文档明示静态图标在 `icons/res/drawable-xxhdpi/`（§6.2 修正 3） |
| 14 | 网络仅可直连官方端点 | 可配置 GitHub 访问令牌（仅官方域名附带）与镜像前缀（默认关闭，内容仍按 blob SHA 校验） | 直连网络下 GitHub 端点经常不可达或匿名限额耗尽，镜像作为用户自选备选 |

**原版行为保留清单**（与上表互补，下列行为原样保留）：确认对话框 · 任务面板状态实时刷新 + 后台运行 · 配置值（trailing summary）实时刷新 · 下载进度（大小/百分比/后台/续传）· 完成后路径提示 + 跳转 · 清理缓存（含大小统计）· 已适配数量统计 · denylist“按需选择，请勿全选”警示 · mtz 设计师账号或主题破解警示 · 图标存档下载 · 深色模式 · 公告拉取与缓存回退策略

---

## 14. 文案规范（中文）

文案出处：`docs/original-app-scripts/local-scripts/misc/string.ini` 与 `docs/original-app-strings/`。原版的执行日志行（`-`/`x`/`√`/`×` 前缀条目）不再沿用：任务面板无逐行日志（§10.6），前缀一律去除，状态文本为本项目改写；界面文案部分条目已按本项目规范修订（补 pangu 空格、统一用词），见各条标注：

- **通用**：关闭（非运行态）/ 后台运行 / 取消任务 / 重试 / 使用缓存 / 打开文件
- **下载（进度由面板承担，仅保留状态与错误）**：`正在下载图标资源` / `正在重试 {N} 个失败文件…` / `文件下载失败：{路径}` / `文件下载损坏，请重新尝试下载：{路径}`
- **装配（步骤状态文本）**：`正在导出{装配名}…` / `正在禁用所有动态图标…` / `正在禁用 {N} 个应用的动态图标…` / `正在导入自定义图标…` / `正在打包图标资源…` / `正在合并 MTZ 外壳…` / `正在打包产物…` / `正在保存产物` / `主图标仓库没有更新，无需下载` / `正在使用已校验的本地缓存…` / `正在使用已校验的图标存档缓存…` / `正在打包图标存档…`
- **结果**：`MTZ 主题包已保存至` / `模块已保存至` / `需要设计师账号或主题破解才能导入并使用。`（MTZ 与保存结果换行同屏）/ `图标存档已下载到` / `图标资源已更新`
- **取消/超时**：`正在取消任务…` / `操作已取消` / `后台任务超时，请重试`
- **无网络**：`未检测到网络连接，取消操作。请检查网络后重试`（与 §5.4 错误矩阵一致；原版长文案不再沿用）
- **更新检查（基于原版修订，去前缀与缩进）**：`主图标仓库已是最新` / `主图标仓库有新版本` / `旧版本：` / `新版本：`（恒三行同显，无缓存时旧版本显示 `无`）
- **数量**：`已适配第三方应用图标：` / 无缓存时 `轻触查看资源更新情况`
- **清理缓存**：`缓存清理完成` / `当前缓存大小`
- **确认（基于原版修订）**：`确认上述配置无误后，将生成 mtz 主题包，需要设计师账号或主题破解才能应用。` / `确认上述配置无误后，将生成模块，可自行安装。`（本 APP 为本地生成，原版“下载”改“将生成”）
- **长文（基于原版修订）**：禁用页警示（§10.5 全文：统一“动态图标”、补 pangu 空格）、遮罩开关说明（§9.3 全文：仅补 pangu 空格）、生成说明（§9.4：仅补 pangu 空格）、清理缓存确认（沿用原版）
