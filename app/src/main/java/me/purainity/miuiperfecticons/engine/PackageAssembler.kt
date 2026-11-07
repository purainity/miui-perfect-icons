package me.purainity.miuiperfecticons.engine

import me.purainity.miuiperfecticons.config.IconTheme
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 装配输入必须是已完成校验的仓库快照与私有目录中的自定义图标副本 */
data class AssemblyOptions(
    val theme: IconTheme,
    val useNewMask: Boolean,
    val disableAll: Boolean,
    val disabledPackages: Set<String>,
    val customIcons: File?,
    val format: PackageFormat,
    val timestamp: Date,
)

enum class PackageFormat { MTZ, MODULE }

class PackageAssembler(
    private val template: (String) -> ByteArray,
    private val ensureActive: () -> Unit = {},
    private val log: (String) -> Unit = {},
) {
    /** 结果文件位于工作目录之外，调用者负责导出与清理 */
    fun assemble(repo: File, workspace: File, output: File, options: AssemblyOptions) {
        require(!workspace.exists()) { "工作目录已存在" }
        require(repo.isDirectory) { "图标缓存不存在" }
        require(options.theme.id.matches(Regex("[A-Za-z0-9_-]+"))) { "主题标识无效" }
        require(options.disabledPackages.all { it.matches(Regex("[A-Za-z0-9_][A-Za-z0-9._-]*")) && it != "." && it != ".." }) {
            "禁用列表中存在无效包名"
        }
        workspace.mkdirsOrThrow()
        try {
            val iconDir = File(workspace, "icon-content")
            val resultDir = File(workspace, "result")
            val drawable = File(iconDir, "res/drawable-xxhdpi")
            val dynamic = File(iconDir, "layer_animating_icons")
            drawable.mkdirsOrThrow()
            resultDir.mkdirsOrThrow()
            log("正在导出${options.theme.assemblyName}…")

            val base = File(repo, "icons")
            val theme = File(repo, "themes/${options.theme.id}")
            require(base.isDirectory && theme.isDirectory) { "缓存缺少主图标库或主题资源" }
            // 装配顺序与原版一致：基础库 → 主题覆盖 → 共用动态图标 → denylist → 遮罩 → 自定义 → 扁平分离
            copyContents(base, drawable)
            copyContents(File(theme, "icons"), drawable)
            copyContents(File(theme, "layer_animating_icons"), dynamic)
            // 共用动态图标由服务端 CI 合并进主题，本地复刻该步骤，lrone 与 luck7 除外
            if (options.theme.id !in setOf("lrone", "luck7")) {
                copyContents(File(repo, "themes/common/layer_animating_icons"), dynamic)
            }
            // 过滤先于自定义导入，自定义动态图标不受禁用列表约束（原版怪癖，不得顺手修正）
            if (options.disableAll) {
                log("正在禁用所有动态图标…")
                if (dynamic.exists() && !dynamic.deleteRecursively()) throw IOException("无法清除动态图标")
            } else if (options.disabledPackages.isNotEmpty()) {
                log("正在禁用 ${options.disabledPackages.size} 个应用的动态图标…")
                options.disabledPackages.sorted().forEach { name ->
                    ensureActive()
                    val target = File(dynamic, name)
                    if (target.exists() && !target.deleteRecursively()) throw IOException("无法删除动态图标：$name")
                }
            }
            File(iconDir, "transform_config.xml").writeBytes(
                template(if (options.useNewMask) "transform_config2.xml" else "transform_config1.xml"),
            )
            options.customIcons?.let { custom ->
                require(custom.isDirectory) { "自定义图标目录不可访问" }
                log("正在导入自定义图标…")
                copyContents(File(custom, "静态图标"), drawable)
                copyContents(File(custom, "动态图标"), dynamic)
                copyContents(File(custom, "高级附加"), File(resultDir, "addons"))
            }
            // 原版两种产物都会把顶层扁平 PNG 从 icons 中分离
            val flat = File(resultDir, "miui_mod_icons")
            flat.mkdirsOrThrow()
            drawable.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".png") }.forEach { source ->
                ensureActive()
                moveOrCopy(source, File(flat, source.name))
            }
            log("正在打包图标资源…")
            zipContents(iconDir, File(resultDir, "icons"))

            if (options.format == PackageFormat.MTZ) {
                // MTZ 外壳原样复制，description 中的占位串全部替换为装配名
                val shell = File(repo, "addition/mtz")
                require(shell.isDirectory && File(shell, "description.xml").isFile && File(shell, "com.miui.home").isFile) {
                    "缓存缺少 MTZ 外壳"
                }
                copyContents(shell, resultDir)
                log("正在合并 MTZ 外壳…")
                val description = File(resultDir, "description.xml")
                description.writeText(description.readText(Charsets.UTF_8).replace("themename", options.theme.assemblyName), Charsets.UTF_8)
            } else {
                // module.prop 固定 7 行，version 取设备时区
                val date = SimpleDateFormat("yyyyMMddHHmm", Locale.ROOT).format(options.timestamp)
                File(resultDir, "module.prop").writeText(
                    "id=MIUIiconsplus\nname=MIUI 完美图标补全\nauthor=@PedroZ\n" +
                        "description=使用${options.theme.assemblyName}并补全MIUI完美图标\n" +
                        "version=$date\ntheme=${options.theme.assemblyName}\nthemeid=${options.theme.id}\n",
                )
                listOf("post-fs-data.sh", "customize.sh").forEach { File(resultDir, it).writeBytes(template(it)) }
                val meta = File(resultDir, "META-INF/com/google/android")
                meta.mkdirsOrThrow()
                File(meta, "updater-script").writeText("#MAGISK\n")
                File(meta, "update-binary").writeBytes(template("update-binary"))
            }
            log("正在打包产物…")
            zipContents(resultDir, output)
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally {
            workspace.deleteRecursively()
        }
    }

    private fun copyContents(source: File, destination: File) {
        if (!source.exists()) return
        if (!source.isDirectory) throw IOException("不是目录：$source")
        source.listFiles()?.sortedBy { it.name }?.forEach { copyTree(it, File(destination, it.name)) }
            ?: throw IOException("无法读取目录：$source")
    }

    private fun copyTree(source: File, destination: File) {
        ensureActive()
        if (source.isDirectory) {
            if (destination.exists() && !destination.isDirectory && !destination.delete()) throw IOException("无法覆盖文件：$destination")
            destination.mkdirsOrThrow()
            copyContents(source, destination)
        } else if (source.isFile) {
            if (destination.isDirectory && !destination.deleteRecursively()) throw IOException("无法覆盖目录：$destination")
            destination.parentFile?.mkdirsOrThrow()
            source.inputStream().buffered().use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
        } else throw IOException("无法读取文件：$source")
    }

    private fun moveOrCopy(source: File, destination: File) {
        if (!source.renameTo(destination)) {
            copyTree(source, destination)
            if (!source.delete()) throw IOException("无法移动文件：$source")
        }
    }

    private fun zipContents(root: File, target: File) {
        target.parentFile?.mkdirsOrThrow()
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            fun add(file: File, name: String) {
                ensureActive()
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$name/"))
                    zip.closeEntry()
                    file.listFiles()?.sortedBy { it.name }?.forEach { add(it, "$name/${it.name}") }
                        ?: throw IOException("无法读取目录：$file")
                } else {
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().buffered().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
            root.listFiles()?.sortedBy { it.name }?.forEach { add(it, it.name) }
                ?: throw IOException("无法读取打包目录：$root")
        }
    }

    private fun File.mkdirsOrThrow() {
        if (!isDirectory && !mkdirs()) throw IOException("无法创建目录：$this")
    }
}
