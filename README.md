# MIUI 完美图标计划

“完美图标计划”App 停服后的单机替代客户端：为 MIUI / HyperOS 桌面补全第三方应用图标，适配分层图标特性，去除图标上的营销“牛皮癣”。资源取自 GitHub 图标仓库，在本地装配生成 MTZ 主题包与 Magisk 模块。

## 背景与动机

原“完美图标计划”App（[pzcn/MIUI-Icons-APP](https://github.com/pzcn/MIUI-Icons-APP)）的资源服务器已停止服务：网络门禁探测的对象存储链路间歇性握手超时，门禁一旦失败即终止全部流程，App 因此无法继续使用。

本项目以单机方式复刻其完整装配流程：

- 图标资源仅取自 GitHub 仓库 [pzcn/Perfect-Icons-Completion-Project](https://github.com/pzcn/Perfect-Icons-Completion-Project)（Trees API 清单 + raw 按文件下载，blob SHA 增量比对）
- 清单比对、下载、装配全部在本机完成，不依赖任何自有服务器
- 产物与原版逐文件一致（对照 `docs/sample-outputs/` 中的实物样本逐文件校验）

## 功能特性

**资源获取与更新**

- 图标资源取自 GitHub：Trees API 获取全量文件清单（约 2.8 万条目），与本地索引逐文件比对 blob SHA，只下载有变化的文件，远端已删除的文件同步清理
- 版本检查走 commits API 并以 ETag 缓存；首次下载约 100~130 MB（约 2400 个应用的基础图标 + 所选主题），切换主题时按需补下该主题目录
- 下载 4~8 并发，单文件失败自动指数退避重试，整轮结束后对失败文件统一补下
- 更新日志页展示图标库状态（已适配图标数、本地库版本与同步日期），支持检查更新、下载图标库与刷新更新日志
- GitHub 匿名 API 触发限流时，可选用已校验的本地缓存继续生成

**产物生成**

- 生成 MTZ 主题包：导入小米主题商店应用（需设计师账号或主题破解）
- 生成 Magisk 模块 ZIP：可在 Magisk / KernelSU 自行刷入
- 产物适用于 Android 10 及以上版本的 HyperOS / MIUI
- 产物按时间戳命名（`{装配名}完美图标补全-{时间}.mtz` / `{装配名}模块-{时间}.zip`），输出目录可自选（SAF，默认 Download）

**个性化配置**

- 19 种图标主题可选：MIUI经典、HyperOS、MIUI探·界、iOS、ColorOS / Flyme 系列、Lrone、Luck7、完美曲线、鸿蒙-润泽及 EMUI 系列等（HyperOS 主题需配合 HyperOS 桌面）
- 图标自定义：支持静态图标（扁平 PNG 或分层目录）、动态图标目录、高级附加文件（addons）三类规则；可直接选择原版自定义目录，现有图标继续可用
- 禁用动态图标：按应用多选（支持按名称或包名搜索），或一键禁用全部
- 新版遮罩开关：区分 HyperOS 新桌面与 MIUI 桌面的遮罩差异，图标大小异常时可尝试切换
- 附加功能：按需下载图标存档（历史图标合集）

**界面与体验**

- Material 3 动态取色，深浅色自适应
- 任务由前台服务执行：任务面板以状态文本逐段显示当前步骤（正在下载图标资源 → 正在装配 → 正在保存产物），下载阶段显示已下载 / 总大小与进度条，通知栏同步进度
- 支持后台运行与取消任务；失败可重试，API 限流时可选用本地缓存继续；完成后可从任务面板或通知直接打开产物文件
- 内置缓存管理：显示缓存占用，一键清理（不触碰更新日志缓存）
- 首页公告沿用原版接口；文档卡收录图标预览、使用指南、适配指南等常用链接

## 环境要求

- Android 10（API 29）及以上
- HyperOS / MIUI 12 及以上
- 可访问 GitHub（`github.com` / `api.github.com` / `raw.githubusercontent.com`）的网络环境
- App 本身无需 root；MTZ 导入需设计师账号或主题破解，模块刷入需 Magisk / KernelSU

## 使用方法

1. 前往 [Releases](https://github.com/purainity/miui-perfect-icons/releases) 页面下载最新 APK 安装
2. 首次使用建议先读首页文档卡中的“HyperOS/MIUI 使用指南”与“自定义图标使用指南”
3. 在配置卡中完成设置：
   - 选择主题图标（19 种）
   - 按需配置图标自定义与禁用动态图标
   - 按系统选择遮罩：HyperOS 新桌面开启“新版遮罩”，MIUI 关闭；图标大小异常时可尝试切换
   - 如需更换保存位置，通过“输出目录”选择文件夹（默认 Download）
4. 点击“生成 MTZ 主题包”或“生成 Magisk 模块 ZIP”，确认提示后开始执行；首次执行会先下载图标资源（约 100~130 MB）
5. 任务以前台服务执行：任务面板与通知实时显示进度，可后台运行或取消；下载失败自动重试，API 限流时可选用本地缓存继续
6. 完成后在输出目录获得产物，可从任务面板或通知直接打开：
   - MTZ：通过主题商店导入应用，需设计师账号或主题破解（推荐使用 [MIUI 主题扩展](https://github.com/purainity/miui-theme-extension)）
   - 模块 ZIP：在 Magisk / KernelSU 中刷入后重启生效，也可从中提取制作官改包内置所需的资源文件

## 工作原理

1. 版本检查：commits API 获取图标仓库最新 commit SHA（ETag 缓存），与本地索引比对判断是否需要更新
2. 通过 GitHub Trees API 拉取仓库完整文件清单，含每个文件的 blob SHA 与大小
3. 与本地缓存索引逐文件比对 blob SHA，得出需要下载与删除的文件列表
4. 从 raw.githubusercontent.com 逐文件下载（以 commit SHA 为引用）并按 blob SHA 校验，写入应用私有缓存；4~8 并发，失败指数退避重试
5. 在本地按原版 App 内置脚本的流程装配：基础图标库 → 主题覆盖 → 共用动态图标 → 禁用动态图标过滤 → 遮罩配置 → 自定义图标导入 → 扁平 png 分离 → 打包
6. 通过 SAF 将产物写入输出目录

装配行为从原版 `module.sh` 逐行推导，产物与原版逐文件一致；已核实的原版怪癖（MTZ 同样执行扁平 png 分离；禁用动态图标的过滤先于自定义图标导入）原样保留，不做“修正”。

- 完整设计规格见 [docs/MIUI完美图标计划开发文档.md](docs/MIUI完美图标计划开发文档.md)
- 原版脚本全量拷贝（`original-app-scripts/`）、原版资源文案（`original-app-strings/`）、服务端 CI（`server-ci/`）与产物实物样本（`sample-outputs/`）均在 `docs/` 下

App 的职责到产出文件为止：不安装模块、不重启设备、不修改系统任何内容。

## 开发指南

### 构建

```bash
./gradlew assembleDebug      # Debug APK
./gradlew assembleRelease    # Release APK（需签名配置）
```

Release 签名通过 Gradle 属性注入（如 `~/.gradle/gradle.properties`）：

```properties
ANDROID_RELEASE_KEYSTORE_FILE=/path/to/keystore
ANDROID_RELEASE_KEYSTORE_PASSWORD=******
ANDROID_RELEASE_KEY_ALIAS=******
ANDROID_RELEASE_KEY_PASSWORD=******
```

四项齐备时启用 v2 + v3 签名（debug 构建同样复用该签名），未配置时 Release 输出未签名 APK；仓库内不含任何 keystore 文件。

### 测试与检查

```bash
./gradlew testDebugUnitTest     # 单元测试
./gradlew connectedAndroidTest  # Instrumented 测试（需连接设备或模拟器）
./gradlew lint                  # 静态检查
```

## 开源致谢

- **[pzcn/MIUI-Icons-APP](https://github.com/pzcn/MIUI-Icons-APP)（原“完美图标计划”App）**  
  本项目的直接蓝本，由 Perfect Icons Project Team 以 GPL-3.0 开源。其资源服务器停服后，本项目从其 v3.0.2 内置脚本逐行推导装配行为，配置项、界面文案与产物格式均与之对齐；其脚本与资源的全量拷贝保存在 `docs/original-app-scripts/`，产物验收基准保存在 `docs/sample-outputs/`。
- **[pzcn/Perfect-Icons-Completion-Project](https://github.com/pzcn/Perfect-Icons-Completion-Project)（完美图标补全计划）**  
  本项目的数据来源。全部图标资源与更新日志均取自该仓库（Trees API 清单 + raw 按文件下载，blob SHA 增量比对），其服务端 GitHub Actions 持续打包发布图标资源；本项目按原版口径下载装配，产物与原版逐文件一致。

## 许可证

本项目以 [GPL-3.0](./LICENSE) 许可证开源。

## 免责声明

本项目由 AI 生成，仅供个人学习与技术研究，请勿用于非法用途。使用本项目造成的任何后果由使用者自行承担，与作者无关。
