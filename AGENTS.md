# AGENTS.md

## 项目定位

MIUI 完美图标计划：原“完美图标计划”App（pzcn/MIUI-Icons-APP）停服后的单机替代客户端，从 GitHub 仓库 pzcn/Perfect-Icons-Completion-Project 获取图标资源，本地装配 MTZ 主题包与 Magisk 模块，产出文件即止（不安装、不重启、不修改设备）。

技术栈：纯 Kotlin（XML Views + ViewBinding + Material 3），包名 `me.purainity.miuiperfecticons`。

`docs/MIUI完美图标计划开发文档.md` 是唯一权威规格书。修改代码前先读相关章节；**修改代码后必须同步修改文档，保持文档-代码一致**。

## 常用命令

```bash
./gradlew assembleDebug         # Debug 构建
./gradlew assembleRelease       # Release 构建（需下方签名属性）
./gradlew testDebugUnitTest     # 单元测试（改码后的既定验证步骤；RepositoryIntegrationTest 会真实连接 GitHub）
./gradlew testDebugUnitTest --tests "me.purainity.miuiperfecticons.engine.TemplateIntegrityTest"   # 单测试类
./gradlew connectedAndroidTest  # Instrumented 测试（需连接设备或模拟器）
./gradlew lint                  # 静态检查
```

Release 签名通过 Gradle 属性注入：`ANDROID_RELEASE_KEYSTORE_FILE` / `ANDROID_RELEASE_KEYSTORE_PASSWORD` / `ANDROID_RELEASE_KEY_ALIAS` / `ANDROID_RELEASE_KEY_PASSWORD`，四项齐备才启用（仓库内无 keystore 文件）。

## 项目结构

```
miui-perfect-icons/
├── app/src/
│   ├── main/
│   │   ├── java/me/purainity/miuiperfecticons/    # 主包
│   │   │   ├── MainActivity.kt                    # 首页：公告、更新入口、配置卡、生成入口
│   │   │   ├── FeatureActivity.kt                 # 二级功能页容器：更新日志、图标自定义、禁用动态图标、关于
│   │   │   ├── ThemeSelectionActivity.kt          # 图标主题单选（19 种）
│   │   │   ├── PerfectIconsApplication.kt         # Application，启用动态取色
│   │   │   ├── AppSelectionAdapter.kt             # 应用选择列表适配器
│   │   │   ├── UpdateLogAdapter.kt                # 更新日志列表适配器
│   │   │   ├── config/
│   │   │   │   ├── AppSettings.kt                 # 全部配置项
│   │   │   │   ├── IconThemes.kt                  # 19 种图标主题表
│   │   │   │   └── MaskDefaults.kt                # 遮罩默认值
│   │   │   ├── engine/
│   │   │   │   ├── GitHubRepository.kt            # Trees API 清单、raw 下载、blob SHA 增量
│   │   │   │   ├── PackageAssembler.kt            # 与原版 pack() 等价的装配
│   │   │   │   ├── DocumentStorage.kt             # SAF 读写
│   │   │   │   ├── ArchivePacker.kt               # 存档打包
│   │   │   │   ├── CacheContents.kt               # 缓存内容索引
│   │   │   │   ├── OriginalChangelog.kt           # 原版更新日志解析
│   │   │   │   └── RepositoryRules.kt             # 仓库文件清单规则
│   │   │   ├── tasks/
│   │   │   │   ├── TaskService.kt                 # dataSync 前台服务，承载下载与装配
│   │   │   │   ├── TaskRuntime.kt                 # 任务共享状态快照
│   │   │   │   └── TaskLogDialog.kt               # 执行日志面板
│   │   │   └── ui/
│   │   │       ├── EdgeToEdge.kt                  # edge-to-edge 与系统栏 inset
│   │   │       ├── OpenSourceProjects.kt          # 开源项目清单
│   │   │       ├── UpdateLogPages.kt              # 更新日志分页数据
│   │   │       ├── SelectionAccessibility.kt      # 列表选中态无障碍支持
│   │   │       └── CustomDirectoryAccess.kt / CustomDirectoryLocation.kt    # 自定义目录 SAF 访问
│   │   ├── assets/                                # customize.sh、post-fs-data.sh、update-binary、transform_config*.xml，与原版 module.sh heredoc 逐字节一致
│   │   └── res/                                   # XML 布局、Material Symbols 风格矢量图标、M3 主题
│   ├── test/                                      # 15 个单元测试类（JUnit 4），含 TemplateIntegrityTest 等回归测试
│   └── androidTest/                               # 5 个 Instrumented 测试类（Espresso）
└── docs/
    ├── MIUI完美图标计划开发文档.md                 # 唯一权威规格书
    ├── original-app-scripts/                      # 原版 App 内置脚本与资源全量拷贝（行为推导依据）
    ├── original-app-strings/                      # 原版 App 资源文案
    ├── server-ci/                                 # 图标仓库服务端 GitHub Actions 工作流
    └── sample-outputs/                            # 原版产物实物样本（验收基准）
```

## 行为红线

- 产物必须与原版逐文件一致（对照 `docs/sample-outputs/` 验收）；两条已核实的原版怪癖（MTZ 同样执行扁平 png 分离；denylist 过滤先于自定义图标导入）不得“顺手修正”
- `app/src/main/assets/` 内脚本受 `TemplateIntegrityTest` 逐字节校验，不得改动内容与格式
- 不引入：Compose、携带 so 原生库的依赖、内置 git/curl/busybox 等二进制、第三方 CDN 或镜像、连通性预检逻辑
- MTZ 无实物对照样本，相关改动须依据原版脚本逐行推导复核

## 代码与文档规范

- 文档一律中文
- 引号一律使用中文双引号“”，不使用方引号「」
- 代码注释使用中文；单行注释末尾默认不加句号，长句之间的分隔仍允许句号；一句话不拆成多行，不是同一句话的内容可以分行书写
- 引号、注释句号这类写作与注释风格规范只写入本文件（AGENTS.md），不进开发文档

## 版本与 Git

- 除非用户明确要求，不修改 `versionCode` / `versionName`
- 完成修改后主动暂存本次会话实际改动的文件：仅使用显式路径，禁止 `git add -A` / `git add .`
- 除非用户明确要求，不创建提交
- 提交消息统一中文，建议格式 `类型: 简述`（功能 / 修复 / 文档 / 构建），首行保持一行
- `push` / `reset --hard` / `rebase` / 删分支等操作需用户明示
