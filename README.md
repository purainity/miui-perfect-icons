# MIUI 完美图标计划

“完美图标计划”的单机版替代客户端——从 GitHub 图标仓库获取资源，在本地装配生成 **MTZ 主题包**与 **Magisk 模块**，为 HyperOS / MIUI 补全第三方应用图标。

## 背景与动机

原“完美图标计划”App（[pzcn/MIUI-Icons-APP](https://github.com/pzcn/MIUI-Icons-APP)）的资源服务器已停止服务：网络门禁探测的对象存储链路间歇性握手超时，门禁一旦失败即终止全部流程，App 因此无法继续使用。

本项目以单机方式复刻其完整装配流程：

- 图标资源仅取自 GitHub 仓库 [pzcn/Perfect-Icons-Completion-Project](https://github.com/pzcn/Perfect-Icons-Completion-Project)（Trees API 清单 + raw 按文件下载，blob SHA 增量比对）
- 清单比对、下载、装配全部在本机完成，不依赖任何自有服务器
- 产物与原版逐文件一致（对照 `docs/sample-outputs/` 中的实物样本逐文件校验）

## 功能特性

**资源获取与更新**

- 图标资源取自 GitHub：Trees API 获取清单，raw 按文件下载，blob SHA 增量比对，只下载有变化的文件
- 更新日志页展示图标库状态（已适配图标数、本地库版本与同步日期），支持检查更新、下载图标库与刷新更新日志
- 首次下载约 100~130 MB；GitHub 匿名 API 触发限流时，可选用已校验的本地缓存继续生成

**产物生成**

- 生成 MTZ 主题包：导入小米主题商店应用（需设计师账号或主题破解）
- 生成 Magisk 模块 ZIP：可在 Magisk / KernelSU 自行刷入
- 产物适用于 Android 10 及以上版本的 HyperOS / MIUI

**个性化配置**

- 19 种图标主题可选（含需配合 HyperOS 桌面的专属主题）
- 图标自定义：支持静态图标（扁平 PNG 或分层目录）、动态图标目录、高级附加文件（addons）三类规则；可直接选择原版自定义目录，现有图标继续可用
- 禁用动态图标：按应用多选（支持按名称或包名搜索），或一键禁用全部
- 新版遮罩开关：区分 HyperOS 新桌面与 MIUI 桌面的遮罩差异，图标大小异常时可尝试切换

**界面与体验**

- Material 3 动态取色，深浅色自适应
- 任务由前台服务执行，附进度通知与实时日志面板，支持取消、重试、后台运行
- 输出目录可自选（SAF，默认 Download），完成后可直接打开产物文件
- 内置缓存管理：显示缓存占用，一键清理
- 首页公告沿用原版接口；文档卡收录图标预览、使用指南、适配指南等常用链接

## 环境要求

- Android 10（API 29）及以上，面向 HyperOS / MIUI 12+
- 可访问 GitHub（`github.com` / `api.github.com` / `raw.githubusercontent.com`）的网络环境
- App 本身无需 root；MTZ 导入需设计师账号或主题破解，模块刷入需 Magisk / KernelSU

## 下载与安装

前往 [Releases](https://github.com/purainity/miui-perfect-icons/releases) 页面下载最新 APK 安装。

## 使用方法

1. 首次使用建议先读首页文档卡中的“HyperOS/MIUI 使用指南”与“自定义图标使用指南”
2. 在配置卡中完成设置：
   - 选择主题图标（19 种）
   - 按需配置图标自定义与禁用动态图标
   - 按系统选择遮罩：HyperOS 新桌面开启“新版遮罩”，MIUI 关闭；图标大小异常时可尝试切换
   - 如需更换保存位置，通过“输出目录”选择文件夹（默认 Download）
3. 点击“生成 MTZ 主题包”或“生成 Magisk 模块 ZIP”，确认提示后开始执行
4. 任务以前台服务执行，通知与日志面板实时显示进度，可取消或转后台运行
5. 完成后在输出目录获得产物：
   - MTZ：通过主题商店导入应用（需设计师账号或主题破解）
   - 模块 ZIP：在 Magisk / KernelSU 中刷入后生效

> App 的职责到产出文件为止：不安装模块、不重启设备、不修改系统任何内容。

## 工作原理

1. 通过 GitHub Trees API 拉取图标仓库的完整文件清单
2. 与本地缓存索引逐文件比对 blob SHA，得出需要下载的文件列表
3. 从 raw.githubusercontent.com 逐文件下载并校验，写入应用私有缓存
4. 在本地按原版 App 内置脚本的流程装配产物（遮罩配置、禁用动态图标过滤、自定义图标导入等，顺序与原版一致）
5. 通过 SAF 将产物写入输出目录

装配行为从原版 `module.sh` 逐行推导，产物与原版逐文件一致；已核实的原版怪癖原样保留，不做“修正”。

## 开发指南

### 环境

- JDK：版本由 Gradle 工具链管理，缺失时由 foojay resolver 自动解析下载
- Android SDK：所需版本以 `app/build.gradle.kts` 为准

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

四项齐备时启用 v2 + v3 签名（debug 构建同样复用该签名）；仓库内不含任何 keystore 文件。

### 测试与检查

```bash
./gradlew testDebugUnitTest     # 单元测试（RepositoryIntegrationTest 会真实连接 GitHub）
./gradlew connectedAndroidTest  # Instrumented 测试（需连接设备或模拟器）
./gradlew lint                  # 静态检查
```

## 文档

- [docs/MIUI完美图标计划开发文档.md](docs/MIUI完美图标计划开发文档.md) — 权威开发规格书
- [docs/original-app-scripts/](docs/original-app-scripts/) — 原版 App 内置脚本全量拷贝（行为推导依据）
- [docs/original-app-strings/](docs/original-app-strings/) — 原版 App 资源文案
- [docs/server-ci/](docs/server-ci/) — 图标仓库服务端 GitHub Actions 工作流
- [docs/sample-outputs/](docs/sample-outputs/) — 原版产物实物样本（验收基准）

## 开源致谢

- **[pzcn/MIUI-Icons-APP](https://github.com/pzcn/MIUI-Icons-APP)（原“完美图标计划”App）**
  本项目的直接蓝本，由 Perfect Icons Project Team 以 GPL-3.0 开源。其资源服务器停服后，本项目从其 v3.0.2 内置脚本逐行推导装配行为，配置项、界面文案与产物格式均与之对齐；其脚本与资源的全量拷贝保存在 `docs/original-app-scripts/`，产物验收基准保存在 `docs/sample-outputs/`。
- **[pzcn/Perfect-Icons-Completion-Project](https://github.com/pzcn/Perfect-Icons-Completion-Project)（完美图标补全计划）**
  本项目的数据来源。全部图标资源与更新日志均取自该仓库（Trees API 清单 + raw 按文件下载，blob SHA 增量比对），其服务端 GitHub Actions 持续打包发布图标资源；本项目按原版口径下载装配，产物与原版逐文件一致。

### 关联项目

- [purainity/miui-theme-extension](https://github.com/purainity/miui-theme-extension) — 同作者的 MIUI 主题扩展

## 许可证

本项目以 [GPL-3.0](./LICENSE) 许可证开源。
