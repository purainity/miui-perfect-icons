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
        val expected = mutableMapOf<String, File>()
        fun overlay(dir: File, prefix: String) {
            if (!dir.isDirectory) return
            dir.walkTopDown().filter { it.isFile }.forEach { expected[prefix + it.relativeTo(dir).invariantSeparatorsPath] = it }
        }
        overlay(File(repo, "icons"), "res/drawable-xxhdpi/")
        overlay(File(repo, "themes/explore/icons"), "res/drawable-xxhdpi/")
        overlay(File(repo, "themes/explore/layer_animating_icons"), "layer_animating_icons/")
        overlay(File(repo, "themes/common/layer_animating_icons"), "layer_animating_icons/")
        val flatPrefix = "res/drawable-xxhdpi/"
        val flat = expected.filterKeys { it.startsWith(flatPrefix) && '/' !in it.removePrefix(flatPrefix) && it.endsWith(".png") }
        flat.keys.forEach { expected.remove(it) }
        expected["transform_config.xml"] = File(templates, "transform_config1.xml")
        assertTrue(expected.size > 1000)
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
                val remaining = expected.toMutableMap()
                ZipInputStream(outer.getInputStream(outer.getEntry("icons"))).use { icons ->
                    while (true) {
                        val entry = icons.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val file = remaining.remove(entry.name) ?: throw AssertionError("多余文件：${entry.name}")
                        assertArrayEquals("内容不一致：${entry.name}", file.readBytes(), icons.readBytes())
                    }
                }
                assertTrue("缺失文件：${remaining.keys.take(5)}", remaining.isEmpty())
                flat.forEach { (path, file) ->
                    val entry = outer.getEntry("miui_mod_icons/${path.removePrefix(flatPrefix)}")
                    assertTrue("缺少扁平图标：$path", entry != null)
                    assertArrayEquals(file.readBytes(), outer.getInputStream(entry).readBytes())
                }
                if (format == PackageFormat.MODULE) {
                    assertArrayEquals(File(templates, "customize.sh").readBytes(), outer.getInputStream(outer.getEntry("customize.sh")).readBytes())
                    assertEquals("#MAGISK\n", outer.getInputStream(outer.getEntry("META-INF/com/google/android/updater-script")).reader().readText())
                } else {
                    assertArrayEquals(File(repo, "addition/mtz/com.miui.home").readBytes(), outer.getInputStream(outer.getEntry("com.miui.home")).readBytes())
                    val description = File(repo, "addition/mtz/description.xml").readText().replace("themename", "MIUI探·界主题")
                    assertEquals(description, outer.getInputStream(outer.getEntry("description.xml")).reader().readText())
                }
            }
        }
    }
}
