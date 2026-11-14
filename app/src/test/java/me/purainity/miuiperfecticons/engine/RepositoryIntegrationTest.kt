package me.purainity.miuiperfecticons.engine

import me.purainity.miuiperfecticons.config.IconThemes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Date
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** 使用可选的真实仓库副本验证大批量装配，普通测试不依赖外部文件 */
class RepositoryIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun realRepositoryAssemblesBothFormats() {
        val source = System.getenv("PERFECT_ICONS_SOURCE")?.let(::File)
        assumeTrue(source != null && File(source, "icons").isDirectory && File(source, "themes/explore").isDirectory)
        val repo = source!!
        val templates = File("src/main/assets")
        assertTrue(templates.isDirectory)
        val layered = mutableMapOf<String, File>()
        val flat = mutableMapOf<String, File>()
        fun overlay(dir: File, prefix: String) {
            if (!dir.isDirectory) return
            dir.walkTopDown().filter { it.isFile }.forEach {
                val path = prefix + it.relativeTo(dir).invariantSeparatorsPath
                if (prefix == "res/drawable-xxhdpi/" && '/' !in it.relativeTo(dir).invariantSeparatorsPath && path.endsWith(".png")) {
                    flat[it.relativeTo(dir).invariantSeparatorsPath] = it
                } else {
                    layered[path] = it
                }
            }
        }
        overlay(File(repo, "icons"), "res/drawable-xxhdpi/")
        overlay(File(repo, "themes/explore/icons"), "res/drawable-xxhdpi/")
        overlay(File(repo, "themes/explore/layer_animating_icons"), "layer_animating_icons/")
        overlay(File(repo, "themes/common/layer_animating_icons"), "layer_animating_icons/")
        layered["transform_config.xml"] = File(templates, "transform_config1.xml")
        assertTrue(layered.size > 1000)
        assertTrue(flat.isNotEmpty())
        val assembler = PackageAssembler(template = { File(templates, it).readBytes() })
        for (format in PackageFormat.entries) {
            val output = File(temporary.root, "$format.zip")
            val options = AssemblyOptions(
                theme = IconThemes.find("explore"),
                useNewMask = false,
                disableAll = false,
                disabledPackages = emptySet(),
                customIcons = null,
                format = format,
                timestamp = Date(0),
            )
            assembler.assemble(repo, File(temporary.root, "work-$format"), output, options)
            ZipFile(output).use { outer ->
                // 模块把扁平 png 分离到 miui_mod_icons，MTZ 把扁平 png 留在 icons 内平铺
                val expectedIcons = if (format == PackageFormat.MODULE) {
                    layered
                } else {
                    layered + flat.mapKeys { "res/drawable-xxhdpi/" + it.key }
                }
                val remaining = expectedIcons.toMutableMap()
                ZipInputStream(outer.getInputStream(outer.getEntry("icons"))).use { icons ->
                    while (true) {
                        val entry = icons.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val file = remaining.remove(entry.name) ?: throw AssertionError("多余文件：${entry.name}")
                        assertArrayEquals("内容不一致：${entry.name}", file.readBytes(), icons.readBytes())
                    }
                }
                assertTrue("缺失文件：${remaining.keys.take(5)}", remaining.isEmpty())
                if (format == PackageFormat.MODULE) {
                    flat.forEach { (name, file) ->
                        val entry = outer.getEntry("miui_mod_icons/$name")
                        assertTrue("缺少扁平图标：$name", entry != null)
                        assertArrayEquals(file.readBytes(), outer.getInputStream(entry).readBytes())
                    }
                    assertArrayEquals(File(templates, "customize.sh").readBytes(), outer.getInputStream(outer.getEntry("customize.sh")).readBytes())
                    assertEquals("#MAGISK\n", outer.getInputStream(outer.getEntry("META-INF/com/google/android/updater-script")).reader().readText())
                } else {
                    assertTrue(outer.entries().toList().none { it.name.startsWith("miui_mod_icons") })
                    assertArrayEquals(emptyEocd, outer.getInputStream(outer.getEntry("com.miui.home")).readBytes())
                    val description = File(repo, "addition/mtz/description.xml").readText().replace("themename", "MIUI探·界主题")
                    assertEquals(description, outer.getInputStream(outer.getEntry("description.xml")).reader().readText())
                }
            }
        }
    }

    /** 与 PackageAssembler 生成的 com.miui.home 空 zip 桩（裸 EOCD）逐字节一致 */
    private val emptyEocd = byteArrayOf(
        0x50, 0x4B, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )
}
