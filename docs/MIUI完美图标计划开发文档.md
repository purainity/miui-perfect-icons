# MIUI 完美图标计划 · 替代客户端开发文档

原“完美图标计划”App 依赖的资源服务器已停止服务，但图标资源仓库 `pzcn/Perfect-Icons-Completion-Project` 仍在 GitHub 上持续更新。本项目开发一个单机版替代客户端：从 GitHub 获取图标资源，在本地完成装配，产出 MTZ 主题包与 Magisk 模块 zip。**产出到文件即止**——不做安装、不做重启、不做任何设备修改。

原 App 的全部逻辑由其 assets 内的 shell 脚本实现（核心为 `misc/module.sh`），本 App 的行为规格即由这些脚本推导而来；原版脚本、服务端 CI 与实物产物样本均已存于 `docs/` 下（见 §3.1）。

---

## 1. 目标与范围

### 1.1 产出目标

1. **MTZ 主题包**：用户自行导入小米主题商店，需设计师账号或主题破解环境
2. **Magisk 模块 zip**：用户自行在 Magisk / KernelSU 中刷入

### 1.2 明确不做

- HarmonyOS / EMUI（hwt）相关的一切页面与流程
- 一键安装模块（`magisk --install-module` / `ksud`）、重启设备按钮、电源菜单
- Xposed 模块（原 App 的“主题试用防恢复”hook）
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

- 产物面向 HyperOS / MIUI 12+，其本身要求 Android 10+，生成器与目标设备天然重合
- 免去旧版存储权限与旧通知兼容分支，MediaStore / SAF / 前台服务 / 深色模式全部走现代路径

**UI——XML Views + ViewBinding + Material Design 3：**

- 界面全部按 Material Design 3 重新设计，不照抄原版 kr-script 的视觉样式，仅保留其信息架构与文案语料

**数据源——仅 GitHub：**

- 图标资源仓库仍在 GitHub 上持续更新，清单比对、下载、装配全部在本机完成，不依赖任何自有服务器
- 原版 App 的网络门禁一旦失败即终止全部流程；本项目不做连通性预检，失败由任务级重试兜底

### 2.3 硬性红线

| 项 | 禁止 |
|---|---|
| UI 框架 | Compose；DataBinding |
| 依赖 | `com.android.support.*` 旧支持库；任何携带 so 原生库的依赖 |
| 内置文件 | git / curl / busybox / zip 等可执行二进制（仅作为 `docs/original-app-scripts/` 中的行为参考，不随 App 分发） |
| 数据源 | 任何第三方 CDN 或镜像；连通性预检逻辑 |
| 权限 | 任何需要 root 权限的功能 |

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
├─ original-app-scripts/   原版 App assets 全量拷贝，与发布的 v3.0.2 APK 内 assets 逐字节一致
│   ├─ local-scripts/      kr-script 页面定义与逻辑脚本：
│   │   ├─ install.xml          首页“下载安装”页结构（主题选择/自定义图标/遮罩开关/生成入口）
│   │   ├─ more.xml             App 首页结构（公告/更新/各页面入口）
│   │   ├─ addon.xml            图标自定义页
│   │   ├─ disabledynamicicons.xml  禁用动态图标页
│   │   ├─ page/about.xml       设置与帮助页（外链、清理缓存）
│   │   ├─ misc/module.sh       ★核心：资源下载、装配、打包、安装的全部逻辑
│   │   ├─ misc/string.ini      全部中文文案
│   │   ├─ misc/transform_config{1,2}.xml   遮罩模板 V1/V2
│   │   └─ misc/*.sh            其余配置读写/检查脚本
│   ├─ script/             before_start.sh（初始化、公告拉取、遮罩默认值）等启动脚本；
│   │                      bin/ 内置 busybox/git/curl/toybox/zip 等二进制，仅为原版行为参考，新 App 不携带
│   └─ kr-script.conf、easygo.json、xposed_init
├─ original-app-strings/   原版 App 资源文案（pio 与 krscript 的 strings.xml，含“关于”致谢名单）
├─ server-ci/              图标仓库服务端 CI（见 §3.4）
└─ sample-outputs/         实物样本：模块产物内文件、产物文件清单（验收 diff 基准）、
                           App 私有数据中的文本配置、mtz 外壳与 explore 主题的服务端原件（tar.xz）
```

App 私有数据备份中的 `miui/` 图标缓存（约 800MB，内含嵌有原服务器凭据的 `.git` 目录）不纳入参考文件。

### 3.2 资源构成（原版三段式）

| 资源 | 原版来源 | 内容 |
|---|---|---|
| 主图标库 | 制品库 `iconsrepo.tar.xz` / 增量 `git pull`（git 仓库 `e.coding.net/miuiicons/icons/icons2.git`） | `icons/` 全部内容 → `res/drawable-xxhdpi/` |
| 主题包 | 制品库 `{主题ID}.tar.xz` | `icons/`（扁平 png 与分层目录并存）与 `layer_animating_icons/` |
| mtz 外壳 | 制品库 `mtz.tar.xz` | `description.xml`、`preview/`、`wallpaper/`、`com.miui.home` |

### 3.3 原版网络接口现状（逐一实测）

对原 App 脚本中出现的全部网络接口做了完整探测，结论：**多数数据接口仍在响应且仍在更新，App 整体失效的直接原因在网络前置检查环节**。

| 接口 | 用途 | 状态 |
|---|---|---|
| `https://miuiicons-generic.pkg.coding.net/icons/files/link.ini?version=latest` | 下载地址分发 | 200 正常（内容指向 oracle 对象存储） |
| `https://miuiicons-generic.pkg.coding.net/icons/files/announce3000.txt?version=latest` | 中文公告 | 200 正常（内容为 3.0 版本升级说明） |
| `https://miuiicons-generic.pkg.coding.net/icons/files/announce3000eng.txt?version=latest` | 英文公告 | 404 失效（本 App 仅中文界面，不使用） |
| `https://miuiicons-generic.pkg.coding.net/icons/files/changelog2.xml?version=latest` | 更新日志 | 200 正常（仍有新提交条目，持续更新） |
| `https://miuiicons-generic.pkg.coding.net/icons/files/check?version=latest` | 服务器检查 | 200 正常 |
| `https://miuiicons-generic.pkg.coding.net/icons/files/Icons_Archiving.zip` | 图标存档 | 200 正常 |
| `https://miuiicons-generic.pkg.coding.net/icons/files/before_start_2100.sh?version=latest` | Beta 脚本热更 | 200 正常 |
| `https://miuiicons-generic.pkg.coding.net/icons/hyper/iconsrepo.ini?version=latest`、`https://miuiicons-generic.pkg.coding.net/icons/hyper/iconscount.txt?version=latest`、`https://miuiicons-generic.pkg.coding.net/icons/hyper/mtz.ini?version=latest` | 版本描述 | 200 正常 |
| `https://miuiicons-generic.pkg.coding.net/icons/hyper/{主题ID}.ini?version=latest`（全部 19 个主题） | 主题版本描述 | 全部 200 正常（iconsrepo 版本新于图标仓库本地 clone，`git_SHA` 与 git 仓库 HEAD 一致，CI 仍在推送） |
| `https://miuiicons-generic.pkg.coding.net/icons/hyper/{主题ID}.tar.xz?version=latest`、`https://miuiicons-generic.pkg.coding.net/icons/hyper/mtz.tar.xz?version=latest` | 主题包与 mtz 外壳直链下载 | 200 正常（xz 文件头校验通过；此为原版 ≤6MB 小文件通道） |
| `https://miuiicons-generic.pkg.coding.net/icons/hyper/iconsrepo.tar.xz?version=latest` | 主图标库直链 | 404 不存在（仅存于 oracle 对象存储；本 App 资源改走 GitHub，不使用） |
| `https://miuiicons-generic.pkg.coding.net/hyper/files/*` | update_check.sh 使用的路径 | 404 失效（原 App 内部已不一致的旧路径，不使用） |
| `https://objectstorage.ap-seoul-1.oraclecloud.com/n/cnkkaulgjby7/b/icons/o/index.html` | network_check 的探测目标（`link_check`） | **链路极不稳定**：多次探测仅约两至三成成功，失败均为 TCP 握手超时（与请求方法无关）；经独立网络通道验证服务端仍在线并正常返回内容 |
| `https://objectstorage.ap-seoul-1.oraclecloud.com/n/cnkkaulgjby7/b/icons/o/hyper/iconsrepo.tar.xz` | 主图标库大文件唯一下载通道（120MB） | **事实不可用**：链路间歇性握手超时，大文件抽样下载反复失败；即便连通，实测速度也仅 KB/s 级 |
| `https://e.coding.net/miuiicons/icons/icons2.git` | 主图标库增量更新 | 匿名可读，HEAD 与制品库 `.ini` 一致，CI 仍在推送 |
| `https://emuiicons-generic.pkg.coding.net/files/zip/{主题ID}.ini?version=latest` | EMUI 资源 | 200 正常（不在本项目范围） |
| 语雀 7 篇文档、QQ 群、push.html、icons-demo（两个域名）、Telegram 频道/群组、GitHub 两个仓库 | 文档外链 | 全部可达 |
| `https://iconsx.vercel.app/privacy.html` | 隐私协议（vercel 部署） | 404 失效；同站 `https://iconsx.pedroz.eu.org/privacy.html` 正常，外链改用该地址 |
| `https://status.pedroz.eu.org/status/icons` | 原版“查看服务器状态”链接 | 已失效；本 App 不使用 |

**原 App 失效根因**：`module.sh` 的 `network_check()` 以 `curl -I`、`--connect-timeout 1` 探测 oracle 对象存储上的 `link_check` 地址，重试三次全败即输出“× 未检测到网络连接”并终止。实测该对象存储的服务端仍在线（独立通道可正常取回内容），但链路质量极差——间歇性 TCP 握手超时、连通时速度仅 KB/s 级。对这样一条国际链路，“1 秒超时 + 仅三次重试”的门禁设计必然频繁全败，而门禁一败即终止全部流程，数据平面（coding 制品库、git 仓库实际仍在服务且持续更新）完全无从使用。主图标库 120MB 大文件更是只有这条链路可下，双重不可用。

对本 App 的两条结论：其一，图标资源改由 GitHub 获取，不再依赖这条死链；其二，**不做任何独立的连通性预检**，直接请求真实资源端点并按实际失败处理（§5.1），避免重蹈原版“门禁端点单点故障拖垮全部功能”的覆辙。

### 3.4 服务端 CI 行为（需本地复刻的关键点）

图标仓库的 `.github/workflows/` 揭示了制品库内容的生成方式，存于 `docs/server-ci/`：

- **`miui_theme.yml`**：主题包打包前执行 `cp -rf common/* <主题ID>`——**`themes/common/` 的共用动态图标在此阶段合并进主题**，lrone、luck7 除外。新 App 装配时须执行同样的合并（§5.2 步骤 3）
- **`miui_res.yml`**：将 `icons/**` 推送至 icons2 git 仓库并生成 `iconsrepo.tar.xz`；`iconscount` = `icons/` 一级子目录数（`ls -l | grep "^d" | wc -l`）；changelog2.xml 由 GitHub 提交记录自动拼装——**更新日志与 commits 同源**
- **`archiving_icons.yml`**：图标存档 = `addition/Icons_Archiving/` 打 zip

### 3.5 实现要点（易错点）

以下结论来自对 `module.sh` 的逐行分析与实物比对，实现时须注意：

1. **两类主题名称**：界面显示名不带后缀（如 `MIUI经典`），装配用名称 = 显示名 + `主题`（如 `MIUI经典主题`）。后者用于产物文件命名、module.prop、mtz description 替换与导出日志（§4.4）。
2. **module.prop 共 7 行**：`module.sh` 中另有一处向临时目录的 module.prop 追加 `iconsrepo=` 的语句，但该文件从未打入产物，属死代码，不复刻。
3. **自定义图标“高级附加”（addons）位于产物根目录、与 icons 同级**（两种模式一致）——`addon()` 复制到打包根的 `addons/`，不在 icons 内。
4. **扁平 png 分离对模块与 MTZ 两种模式均执行**：原版 `pack()` 以无参调用，函数内 `[ "$1" != mtz ]` 恒真；分离发生在全部 res 来源（基础库/主题/自定义）并入之后，分离出的产物根 `miui_mod_icons/` 同时包含主题与自定义的扁平静态图标（§5.2 步骤 7）。
5. **denylist 过滤先于自定义图标导入**：自定义动态图标不受禁用列表约束，已禁用的包名可经自定义目录重新进入产物（§5.2 怪癖说明）。
6. `customize.sh` 与 `updater-script` 在 Magisk 安装时即被消费，不随已装模块保留；模块备份样本中的 `update-binary` 已被导出工具改写、`post-fs-data.sh` 被移动位置，**权威文本一律以 `module.sh` heredoc 为准**（§6.1 已全文收录）。
7. 原版装配日志中无“请稍候”字样（对应 string 变量未定义）；`x 文件下载失败` / `x 文件下载损坏` 为小写 x。
8. 遮罩模板在仓库 `addition/`、App 内置 assets、实际产物三处逐字节一致，可放心内置为常量（§6.3）。
9. mtz 外壳中的 `com.miui.home` 为 4 字节桩文件（内容 `test`），按原样复制。
10. 原版版本检查比对 `.ini` 中的 `theme_version` 时间戳 / 主图标库 `git_SHA`；新 App 改比对 GitHub commit SHA，数据同源。

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
| `addition/transform_config.xml` / `transform_config2.xml` | 遮罩 V1 / V2 | 2 文件 | 装配主来源（App 另内置 assets 模板兜底） |
| `addition/Icons_Archiving/` | 历史图标存档 | 465 文件 | “下载图标存档”功能（懒加载） |
| `emui/`、`.github/`、`addition/{demo,Icons_Addon,module,old_action,pages}` | 华为资源 / CI / 素材 | — | 不获取 |

### 4.2 接口

- **清单**：`GET https://api.github.com/repos/pzcn/Perfect-Icons-Completion-Project/git/trees/{commit_sha}?recursive=1`
  返回全量文件清单（当前约 2.8 万条目，JSON 约 9MB，`truncated=false`），含每个文件的 **blob SHA** 与大小——即增量比对所需的全部信息。
- **文件下载**：`https://raw.githubusercontent.com/pzcn/Perfect-Icons-Completion-Project/{commit_sha}/{path}`
  下载引用必须用 **commit SHA**（blob SHA 会 404）；增量比对用 blob SHA。
- **版本检查**：`GET https://api.github.com/repos/pzcn/Perfect-Icons-Completion-Project/commits?per_page=1`，取最新 commit SHA，支持 ETag 缓存。
- **限额**：匿名核心 API 60 次/小时，仅版本检查与清单拉取消耗（每次 1~2 次）；raw 不占核心限额。403 处理见 §5.4。
- 整仓 zipball（codeload）约 600MB 且不支持 Range 断点续传，不采用。

### 4.3 获取与缓存策略

- **按文件获取**：首次下载 `icons/**` + 所选主题 `themes/<ID>/**` + `themes/common/**`（如适用）+ `addition/mtz/**` + 两个遮罩 xml，约 105~130MB。切换主题时按需补下该主题目录。
- **增量更新**：Trees 清单与本地索引（路径 → blob SHA）比对，只下载变化/新增文件，删除远端已删除的文件。
- **并发与可靠性**：4~8 并发；单文件失败自动重试（指数退避，重试上限 5 次即共 6 次尝试；无代理网络下 GitHub 端点存在间歇性握手超时，需留足重试余量）；重试耗尽的文件跳过并记录，整体下载结束后对跳过文件再统一补一轮，仍失败才报错；先写 `.tmp` 再原子改名；以单文件为断点单位（仓库最大文件为 3.4MB 壁纸，无需字节级续传）。
- **`addition/Icons_Archiving/`**：仅在用户触发“下载图标存档”时下载或从缓存导出。
- **本地缓存布局**：资源缓存直接位于私有 `files/` 目录下——`repo/` 子目录镜像仓库结构（`icons/`、`themes/<ID>/`〔按下载过的主题累积保留〕、`addition/`），同级另有 `index.json`（commit SHA、每文件 blob SHA、拉取日期）、`api.json`（最新 commit + ETag）、`snapshot-<主题ID>.json` 与 `archive-snapshot.json`（完整快照清单）。更新日志缓存（`files/original-changelog.xml`）与资源缓存同目录但独立管理。“清理缓存”按资源缓存自有清单删除（`repo/` 与各索引/快照文件，不触碰更新日志缓存），大小统计范围与之相同。装配工作区、产物暂存、自定义快照等瞬态内容置于系统 `cache/` 目录（系统存储紧张时可自动清空，全部可重建）。
- **网络提示**：无代理直连网络下 GitHub 各端点（api / raw / codeload / 主站）均可能出现间歇性连接超时，属用户侧网络环境问题；App 以“单文件重试 + 并发请求”的获取策略应对（§4.3 并发与可靠性），不引入第三方镜像。首次下载前提示体量（约 100~130MB）与“下载缓慢或失败时可尝试切换网络”。

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

下载/装配以前台服务通知承载（§11），支持后台运行。**不做独立的连通性预检**：原版以 HEAD 探测第三方对象存储作为网络门禁，该端点停止响应 HEAD 后拖垮全部功能（§3.3）；本 App 直接请求真实资源端点，按实际失败进入 §5.4 处理。

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
   高级附加/*   → 产物根 addons/（与 icons 同级，两种模式一致）
7. 扁平 png 分离（模块与 MTZ 两种模式均执行）：
   icons/res/drawable-xxhdpi/ 顶层扁平 *.png（含主题与自定义静态图标）
   → 产物根 miui_mod_icons/（目录形分层图标留在 res；
   分离在全部 res 来源并入之后执行）
8. icons/{res/, layer_animating_icons/, transform_config.xml} 打为 zip，命名 icons
```

两条必须注意的原版怪癖（均已在 `module.sh` 逐行核实，实现不得“顺手修正”）：

- **步骤 7 对 MTZ 同样执行**：原版 `pack()` 在主流程中以无参形式调用（`module.sh` 第 384 行），函数内 `$1` 为空，`[ "$1" != mtz ]` 恒为真，分离无条件执行。MTZ 产物根因此同样含 `miui_mod_icons/`，`icons` 内不含扁平 png。
- **denylist 盖不住自定义动态图标**：原版顺序为“过滤（第 205 行）→ 导入自定义（第 207 行）”，自定义动态图标在过滤**之后**并入 `layer_animating_icons/`，已禁用的包名若存在于自定义目录会被重新加入，“禁用全部”亦然。保留此顺序以维持产物与原版逐文件一致；在禁用动态图标页对此例外作明确提示（§10.5）。

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
| GitHub API 403（限额） | 提示匿名限额（`GitHub 匿名 API 限额已用完，请稍后重试`），稍后重试；本地已有缓存时询问是否使用缓存继续 |
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

### 6.2 MTZ 主题包

```
{装配名}完美图标补全-{MMddHHmm}.mtz   (= zip)
├─ icons               ← §5.2 装配产物（res 不含扁平 png + layer_animating_icons + transform_config.xml）
├─ miui_mod_icons/     ← 步骤7 分离的扁平静态图标（MTZ 同样执行分离，与模块一致）
├─ addons/**           ← 自定义“高级附加”（可选，位于根目录、与 icons 同级）
├─ description.xml     ← 取 repo/addition/mtz/description.xml，字符串“themename”全部替换为装配名
├─ preview/**          ← 原样（12 张预览图）
├─ wallpaper/**        ← 原样（default_wallpaper.png）
└─ com.miui.home       ← 原样（4 字节桩文件，内容“test”）
```

即：除 `module.prop`、`post-fs-data.sh`、`customize.sh`、`META-INF/` 四项模块专属内容外，MTZ 与模块 zip 的其余结构完全一致（icons + miui_mod_icons + addons + mtz 外壳四件套）。description.xml（仓库模板，`uiVersion=10`，title 为 `themename&MIUI完美图标补全`，含项目介绍与图标绘制者署名 @潮留美海かり @PedroZ @Yife Playte @Eirperuier @Mastiff Li）原样使用；服务端原件样本见 `docs/sample-outputs/mtz.tar.xz`。完成提示附警示：`需要设计师账号或主题破解才能导入并使用。`（与保存结果同屏多行显示）

### 6.3 遮罩配置（两版；装配取仓库缓存文件，App 内置 assets 模板兜底）

- **V1（MIUI 12–14）**：`SupportLayerIcon=true` + `ConfigIconMask`，全文见 `app/src/main/assets/transform_config1.xml`（与仓库 `addition/transform_config.xml` 及原版 assets 逐字节一致）
- **V2（HyperOS）**：`SupportLayerIcon=true` + `UseDynamicIcon=true` + `ConfigIconMask`，全文见 `app/src/main/assets/transform_config2.xml`

### 6.4 验收口径

- 同配置下与原 App 产物逐文件 diff 一致（允许时间戳差异）；实物对照清单：`docs/sample-outputs/icons-zip-filelist.txt`（9440 条）与 `module-backup-filelist.txt`。注意：现存实物样本仅 Magisk 模块（爱玩机备份），**MTZ 产物无实物对照**，其结构规格由 `module.sh` 逐行推导（§5.2、§6.2 已注明两处关键怪癖），MTZ 验收以脚本推导复核 + 破解环境实际导入为准
- mtz 可被破解环境主题商店导入；模块 zip 可被 Magisk v20.4+ / KernelSU 刷入

---

## 7. 配置项（持久化）

| 配置 | 默认 | 原版对应 | 说明 |
|---|---|---|---|
| 主题 ID | `default` | `theme_files/theme_config`（`sel_theme=`） | |
| 新版遮罩 | 按 `ro.miui.ui.version.code` 推断：≤14 → 关（V1），> 14 → 开（V2）；读取失败默认关 | `theme_files/new_transform_config` | §15.2 |
| 自定义图标开关 | 关 | `theme_files/addon_config`（`addon=`） | |
| denylist | 空集 | `theme_files/denylist`（文件不存在 = 空；`all` = 全部；包名以空白分隔） | |
| 输出目录 | 公共 Download | `zipoutdir_config` | SAF 选目录 |
| 资源缓存 commit SHA / 日期 | 空 | `iconsrepo.ini` | 本地索引 |
| 公告缓存 | 空 | `theme_files/announce.txt` | §9.1 |
| 更新日志缓存 | 空 | changelog2.xml（原版仅暂存） | 持久缓存最近一次结果，离线可看 |

---

## 8. UI 设计规范（Material Design 3）

原版界面由 kr-script 引擎渲染（位图行图标 + 自绘开关/选择器）。新 App 不复用其任何视觉元素与布局语言（原 `local-scripts/pic/` 位图全部弃用），以 Material Design 3 组件体系重新设计；具体视觉参数（配色值、字号、字重、间距等）遵循 M3 规范与系统动态取色，开发时按 M3 指南取默认值即可，本文档不逐项规定。

### 8.1 主题

- 基础主题 `Theme.Material3.DayNight.NoActionBar`，深浅色跟随系统
- Android 12+ 默认动态取色（Dynamic Color），低版本使用 M3 内置色板
- 不得出现 `android.support.*` 视觉类

### 8.2 组件映射规则（替代 kr-script 元素）

| 原版 kr-script 元素 | 新 App M3 组件 |
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

### 8.3 布局骨架

- 首页：TopAppBar（仅标题“MIUI 完美图标计划”，无菜单动作）+ 纵向滚动内容
- 二级页面：TopAppBar + 返回箭头，内容随页面而定
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

一张 Filled 卡片内含五行：

| ListItem | leading | 当前值（副标题位置；trailing 仅图标） | 交互 |
|---|---|---|---|
| 选择主题图标 | `palette` | 值：`{显示名}`（实时，无前缀）+ chevron | → 主题选择页（§10.2） |
| 图标自定义 | `tune` | 值：`未启用`/`已启用` + chevron | → 图标自定义页（§10.4） |
| 禁用动态图标 | `animation` | 值：`未启用`/`已选 N 个应用`/`已禁用全部` + chevron | → 禁用动态图标页（§10.5） |
| 输出目录 | `folder` | 值：`当前目录：{路径}`（默认 `Download（默认）`；授权失效显示 `目录授权已失效，请重新选择`）+ chevron | SAF `OPEN_DOCUMENT_TREE` 选目录并持久化授权 |
| 是否启用新版遮罩 | `layers` | `MaterialSwitch` | 切换即持久化；默认值见 §15.2 |

遮罩行副标题（固定文案，基于原版修订，仅补 pangu 空格）：
> 如果你使用的是 HyperOS 的新桌面，请开启这个选项。如果是 MIUI 那就关闭。如果图标大小异常，可尝试切换。

### 9.4 区块④：生成按钮

按钮上方说明文字（基于原版修订，补 pangu 空格）：
> 提供适用于 Magisk 或 KernelSU 的模块，提供 mtz 主题包（需要设计师账号或主题破解）。首次使用前请仔细阅读使用指南。产物适用于 Android 10 及以上版本的 HyperOS / MIUI。

两枚等宽并排 FilledButton（无按钮下小字，说明文字仅位于按钮上方）：

- 生成 MTZ 主题包
- 生成 Magisk 模块 ZIP

点击任一按钮 → `MaterialAlertDialog` 确认（基于原版修订：本 App 为本地生成，“下载”改为“将生成”）→ 进入生成流程（§5），弹出任务面板（§10.6）：
- MTZ：`确认上述配置无误后，将生成 mtz 主题包，需要设计师账号或主题破解才能应用。`
- 模块：`确认上述配置无误后，将生成模块，可自行安装。`
- 追加：`产物适用于 Android 10 及以上版本的 HyperOS / MIUI。首次下载约 100~130 MB，下载缓慢或失败时可尝试切换网络。`

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
   高级附加：文件原样放入主题或模块根目录的 addons
   自定义目录：{当前目录路径，未选择显示“未选择”}
▸ 导入静态图标文件（选择一个或多个 PNG 文件）
▸ 导入静态图标目录（适用于分层图标）
▸ 导入动态图标目录（保留目录内的全部文件）
▸ 导入高级附加文件
▸ 下载图标存档   保存到自定义图标目录
▸ 查看已存档图标（GitHub 外链）
```

- 目录机制兼容原版：`/sdcard/Documents/MIUI完美图标自定义/` 下设 `静态图标`、`动态图标`、`高级附加` 三个子目录（+ `.nomedia`）；首次启用经 `OPEN_DOCUMENT_TREE` 一次性授权该目录，原版用户文件免迁移直接读取
- **装配时对三类内容均整体递归复制、不做结构校验**（与原版 `cp -rf` 一致）——动态图标在仓库中的实际样本即含 `0/`、`1/`、`quiet/`、`config.xml` 等复杂递归结构，任何“必须含 0.png + 1.png”之类的校验都会拒绝现有有效样本；静态图标目录下扁平 png 与分层目录可并存，扁平 png 在装配步骤 7 被分离至 `miui_mod_icons/`
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
| 进度 | `LinearProgressIndicator`：下载阶段确定进度 0–100；准备/装配/保存阶段 indeterminate；仅运行中显示 |
| 进度文本 | `已下载 / 总大小 MB`，仅下载阶段显示 |
| 按钮 | 单行双按钮（左 TextButton + 右 FilledTonalButton），随状态切换：运行中 = 后台运行 / 取消任务；失败（可缓存）= 使用缓存 / 重试；失败（不可缓存）= 关闭 / 重试；成功 = 关闭 / 打开文件 |

“后台运行”收起面板任务转入通知；“取消任务”终止并清理临时文件（§5.4）；“使用缓存”经确认对话框（`GitHub 匿名 API 请求次数已达上限。是否使用已校验的本地缓存继续生成？`）以缓存继续。

完成：状态文本显示 `MTZ 主题包已保存至 {显示标签}` 等结果并出现“打开文件”按钮；发完成通知（通知带打开动作）；不再弹额外 Snackbar。

### 10.7 系统权限申请场景

仅在需要时（自定义图标目录授权、输出目录 SAF、API 33+ 通知权限）由对应页面触发，首页不弹任何权限。

### 10.8 关于页（标题：关于；从首页文档区底部进入）

1. **页头**：App 图标 + 应用名“MIUI 完美图标计划” + 版本号（`版本 {versionName}（{longVersionCode}）`）
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

---

## 11. 通知规格

- 下载/装配任务以前台服务运行（`dataSync` 类型），通知：标题与任务面板任务名一致——“正在下载图标资源”/“正在生成 MTZ 主题包”/“正在生成 Magisk 模块”/“正在下载图标存档”，进度条（百分比 + 大小），完成 → 可点击打开产物，失败 → 重试 Action
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

| # | 原版 | 本 App | 原因 |
|---|---|---|---|
| 1 | 资源取自 coding 制品库与 icons2 git 仓库（root git pull / App 内下载 Activity） | GitHub Trees API 清单 + raw 按文件下载（commit SHA 引用，blob SHA 增量） | 原下载通道已不可用（§3.3） |
| 2 | 版本比对 `.ini` 的 `theme_version` 时间戳 / `git_SHA` | GitHub 最新 commit SHA | 数据同源（CI 从该仓库推送） |
| 3 | 完整性校验 md5 | 逐文件 blob SHA（下载即校验） | 等价强度，免单独 md5 |
| 4 | `themes/common/` 由服务端 CI 合并进主题包 | 本地装配时合并（除 lrone/luck7） | 无服务端 |
| 5 | 更新日志读服务器 changelog2.xml（仅暂存内存） | 沿用 changelog2.xml 接口 + 本地持久缓存 | 接口仍可用且持续更新；不额外消耗 GitHub API 限额 |
| 6 | iconscount 由服务器下发 | 本地统计 `icons/` 一级子目录数 | 口径一致 |
| 7 | 应用列表 root shell `pm list package` | PackageManager | 无 root |
| 8 | 安装模块 / 重启 / Xposed / Beta / 下载兼容模式 | 全部移除，产物到文件即止 | §1.2 |
| 9 | 以 HEAD 探测对象存储作为网络门禁 | 不做连通性预检，直接请求资源端点 | 原门禁单点故障即致 App 整体失效（§3.3） |
| 10 | 图标存档从服务器 zip 下载 | 从本地缓存导出或从 GitHub 下载，保存位置不变 | 数据同源 |

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
- **确认（基于原版修订）**：`确认上述配置无误后，将生成 mtz 主题包，需要设计师账号或主题破解才能应用。` / `确认上述配置无误后，将生成模块，可自行安装。`（本 App 为本地生成，原版“下载”改“将生成”）
- **长文（基于原版修订）**：禁用页警示（§10.5 全文：统一“动态图标”、补 pangu 空格）、遮罩开关说明（§9.3 全文：仅补 pangu 空格）、生成说明（§9.4：仅补 pangu 空格）、清理缓存确认（沿用原版）

---

## 15. 行为细节备注

### 15.1 权限最小化

Manifest 仅声明 `INTERNET`、`QUERY_ALL_PACKAGES`（应用选择页需列出全部已安装应用）与前台服务/通知相关权限；输出公共 Download 走 MediaStore（免存储权限）；输出目录与自定义图标目录走 SAF 一次性授权；无任何 root 相关声明。

### 15.2 新版遮罩默认值

按 `ro.miui.ui.version.code` 推断：≤ 14 → V1（关），> 14 → V2（开）。通过 `android.os.SystemProperties` 反射读取；失败时默认 V1（关），并在开关副标题提示用户自行确认。

### 15.3 系统要求提示

说明文字与生成确认对话框中提示：产物适用于 Android 10+ 的 HyperOS / MIUI；不做强制校验（模块模式的版本校验在 customize.sh 安装时进行）。

### 15.4 原版行为保留清单

确认对话框 · 任务面板状态实时刷新 + 后台运行 · 配置值（trailing summary）实时刷新 · 下载进度（大小/百分比/后台/续传）· 完成后路径提示 + 跳转 · 清理缓存（含大小统计）· 已适配数量统计 · denylist“按需选择，请勿全选”警示 · mtz 设计师账号或主题破解警示 · 图标存档下载 · 深色模式 · 公告拉取与缓存回退策略。
