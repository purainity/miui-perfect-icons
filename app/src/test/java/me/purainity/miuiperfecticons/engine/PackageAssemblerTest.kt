package me.purainity.miuiperfecticons.engine

import me.purainity.miuiperfecticons.config.IconThemes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class PackageAssemblerTest {
    @get:Rule val temporary = TemporaryFolder()

    /** 与 PackageAssembler 生成的 com.miui.home 空 zip 桩（裸 EOCD）逐字节一致 */
    private val emptyEocd = byteArrayOf(
        0x50, 0x4B, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    private fun fixture(): File = temporary.newFolder("repo").apply {
        put("icons/org.example/0.png", "base")
        put("icons/org.example/1.png", "front")
        put("themes/default/icons/org.example/0.png", "theme")
        put("themes/default/icons/org.flat.png", "flat")
        put("themes/default/layer_animating_icons/org.motion/config.xml", "theme")
        put("themes/common/layer_animating_icons/org.motion/config.xml", "common")
        put("themes/lrone/icons/org.flat.png", "lrone")
        put("themes/lrone/layer_animating_icons/org.motion/config.xml", "lrone")
        put("addition/mtz/description.xml", "<title>themename</title>")
        put("addition/mtz/com.miui.home", "test")
        put("addition/mtz/preview/preview.png", "preview")
    }

    private fun File.put(path: String, content: String) {
        val file = resolve(path)
        file.parentFile!!.mkdirs()
        file.writeText(content)
    }

    private fun File.putBytes(path: String, bytes: ByteArray) {
        val file = resolve(path)
        file.parentFile!!.mkdirs()
        file.writeBytes(bytes)
    }

    private fun zipBytes(entryName: String, content: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    /** STORED 条目：数据原样存储，便于构造“数据损坏但中央目录与本地头完好”的样本 */
    private fun storedZipBytes(entryName: String, content: String): ByteArray {
        val data = content.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val entry = ZipEntry(entryName).apply {
                method = ZipEntry.STORED
                size = data.size.toLong()
                compressedSize = data.size.toLong()
                crc = CRC32().apply { update(data) }.value
            }
            zip.putNextEntry(entry)
            zip.write(data)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun options(format: PackageFormat, custom: File? = null) = AssemblyOptions(
        theme = IconThemes.default,
        useNewMask = true,
        disableAll = false,
        disabledPackages = emptySet(),
        customIcons = custom,
        format = format,
        timestamp = Date(0),
    )

    private fun assembler() = PackageAssembler(template = { "template:$it".toByteArray() })

    private fun nested(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return result
    }

    @Test fun moduleAppliesOverridesAndSeparatesFlatIcons() {
        val repo = fixture()
        val output = temporary.root.resolve("module.zip")
        assembler().assemble(repo, temporary.root.resolve("work"), output, options(PackageFormat.MODULE))
        ZipFile(output).use { zip ->
            val icons = nested(zip.getInputStream(zip.getEntry("icons")).readBytes())
            assertEquals("theme", icons["res/drawable-xxhdpi/org.example/0.png"])
            assertEquals("front", icons["res/drawable-xxhdpi/org.example/1.png"])
            assertEquals("common", icons["layer_animating_icons/org.motion/config.xml"])
            assertFalse(icons.containsKey("res/drawable-xxhdpi/org.flat.png"))
            assertEquals("flat", zip.getInputStream(zip.getEntry("miui_mod_icons/org.flat.png")).reader().readText())
            assertEquals("template:transform_config2.xml", icons["transform_config.xml"])
            assertTrue(zip.getInputStream(zip.getEntry("module.prop")).reader().readText().contains("themeid=default\n"))
            assertEquals("#MAGISK\n", zip.getInputStream(zip.getEntry("META-INF/com/google/android/updater-script")).reader().readText())
        }
        assertFalse(temporary.root.resolve("work").exists())
    }

    @Test fun mtzKeepsFlatIconsInsideIconsAndCustomDynamicSurvivesDenyAll() {
        val repo = fixture()
        val custom = temporary.newFolder("custom").apply {
            put("静态图标/org.custom.png", "custom-flat")
            put("动态图标/org.motion/config.xml", "custom-dynamic")
            put("高级附加/nested/extra", "addon")
        }
        val output = temporary.root.resolve("theme.mtz")
        assembler().assemble(repo, temporary.root.resolve("work"), output, options(PackageFormat.MTZ, custom).copy(disableAll = true))
        ZipFile(output).use { zip ->
            val icons = nested(zip.getInputStream(zip.getEntry("icons")).readBytes())
            assertEquals("custom-dynamic", icons["layer_animating_icons/org.motion/config.xml"])
            // 扁平 png 留在 icons 内平铺，MTZ 产物不再携带 miui_mod_icons
            assertEquals("flat", icons["res/drawable-xxhdpi/org.flat.png"])
            assertEquals("custom-flat", icons["res/drawable-xxhdpi/org.custom.png"])
            assertTrue(zip.entries().toList().none { it.name.startsWith("miui_mod_icons") })
            // 高级附加按文件名递归展平到根作为主题组件，子目录仅作分类、不进入产物
            assertEquals("addon", zip.getInputStream(zip.getEntry("extra")).reader().readText())
            assertTrue(zip.entries().toList().none { it.name.startsWith("addons/") })
            assertTrue(zip.entries().toList().none { it.name.startsWith("nested/") })
            assertEquals("<title>MIUI经典主题</title>", zip.getInputStream(zip.getEntry("description.xml")).reader().readText())
            // com.miui.home 为合法空 zip（裸 EOCD），不再是外壳的文本桩
            assertEquals(22L, zip.getEntry("com.miui.home").size)
            assertArrayEquals(emptyEocd, zip.getInputStream(zip.getEntry("com.miui.home")).readBytes())
            assertTrue(zip.getEntry("module.prop") == null)
        }
    }

    @Test fun mtzAddonReplacesComMiuiHome() {
        val repo = fixture()
        val custom = temporary.newFolder("custom").apply {
            putBytes("高级附加/com.miui.home", zipBytes("res/drawable-xhdpi/icon_notification_bg.png", "my-home"))
        }
        val output = temporary.root.resolve("theme.mtz")
        assembler().assemble(repo, temporary.root.resolve("work"), output, options(PackageFormat.MTZ, custom))
        ZipFile(output).use { zip ->
            val home = nested(zip.getInputStream(zip.getEntry("com.miui.home")).readBytes())
            assertEquals("my-home", home["res/drawable-xhdpi/icon_notification_bg.png"])
        }
    }

    @Test fun mtzAddonInvalidComMiuiHomeAbortsAssembly() {
        val repo = fixture()
        val custom = temporary.newFolder("custom").apply {
            put("高级附加/com.miui.home", "not a zip")
        }
        val output = temporary.root.resolve("theme.mtz")
        try {
            assembler().assemble(repo, temporary.root.resolve("work"), output, options(PackageFormat.MTZ, custom))
            throw AssertionError("预期无效 com.miui.home 附加时中止装配")
        } catch (error: IOException) {
            assertEquals("高级附加的 com.miui.home 不是有效的 zip 文件", error.message)
            assertFalse(output.exists())
            assertFalse(temporary.root.resolve("work").exists())
        }
    }

    @Test fun mtzAddonCorruptComMiuiHomeAbortsAssembly() {
        val repo = fixture()
        // 破坏本地文件头签名、保留中央目录：仅构造 ZipFile 时可通过，完整读取条目时才失败
        val corrupt = zipBytes("res/drawable-xhdpi/icon_notification_bg.png", "my-home")
        corrupt[0] = 'X'.code.toByte()
        val custom = temporary.newFolder("custom").apply {
            putBytes("高级附加/com.miui.home", corrupt)
        }
        val output = temporary.root.resolve("theme.mtz")
        try {
            assembler().assemble(repo, temporary.root.resolve("work"), output, options(PackageFormat.MTZ, custom))
            throw AssertionError("预期损坏的 com.miui.home 附加时中止装配")
        } catch (error: IOException) {
            assertEquals("高级附加的 com.miui.home 不是有效的 zip 文件", error.message)
            assertFalse(output.exists())
            assertFalse(temporary.root.resolve("work").exists())
        }
    }

    @Test fun mtzAddonCrcCorruptComMiuiHomeAbortsAssembly() {
        val repo = fixture()
        // STORED 条目数据损坏、中央目录与本地头完好：读取本身不报错，须由 CRC 核对拦截
        val corrupt = storedZipBytes("res/x", "content")
        corrupt[35] = (corrupt[35] + 1).toByte()
        val custom = temporary.newFolder("custom").apply {
            putBytes("高级附加/com.miui.home", corrupt)
        }
        val output = temporary.root.resolve("theme.mtz")
        try {
            assembler().assemble(repo, temporary.root.resolve("work"), output, options(PackageFormat.MTZ, custom))
            throw AssertionError("预期 CRC 损坏的 com.miui.home 附加时中止装配")
        } catch (error: IOException) {
            assertEquals("高级附加的 com.miui.home 不是有效的 zip 文件", error.message)
            assertFalse(output.exists())
            assertFalse(temporary.root.resolve("work").exists())
        }
    }

    @Test fun mtzAddonDuplicateNameAbortsAssembly() {
        val repo = fixture()
        val custom = temporary.newFolder("custom").apply {
            put("高级附加/catA/com.android.systemui", "a")
            put("高级附加/catB/com.android.systemui", "b")
        }
        val output = temporary.root.resolve("theme.mtz")
        try {
            assembler().assemble(repo, temporary.root.resolve("work"), output, options(PackageFormat.MTZ, custom))
            throw AssertionError("预期高级附加同名文件时中止装配")
        } catch (error: IOException) {
            assertEquals("高级附加存在同名文件：com.android.systemui", error.message)
            assertFalse(output.exists())
            assertFalse(temporary.root.resolve("work").exists())
        }
    }

    @Test fun mtzAddonReservedNameAbortsAssembly() {
        val repo = fixture()
        val custom = temporary.newFolder("custom").apply {
            put("高级附加/PREVIEW", "collision")
        }
        val output = temporary.root.resolve("theme.mtz")
        try {
            assembler().assemble(repo, temporary.root.resolve("work"), output, options(PackageFormat.MTZ, custom))
            throw AssertionError("预期高级附加占用保留名时中止装配")
        } catch (error: IOException) {
            assertEquals("高级附加占用 MTZ 保留名：PREVIEW", error.message)
            assertFalse(output.exists())
            assertFalse(temporary.root.resolve("work").exists())
        }
    }

    @Test fun excludedThemesDoNotReceiveCommonDynamicIcons() {
        val repo = fixture()
        val output = temporary.root.resolve("module.zip")
        assembler().assemble(repo, temporary.root.resolve("work"), output, options(PackageFormat.MODULE).copy(theme = IconThemes.find("lrone")))
        ZipFile(output).use { zip ->
            assertEquals("lrone", nested(zip.getInputStream(zip.getEntry("icons")).readBytes())["layer_animating_icons/org.motion/config.xml"])
        }
    }

    @Test fun interruptedAssemblyRemovesTemporaryAndOutputFiles() {
        val repo = fixture()
        val output = temporary.root.resolve("module.zip")
        var calls = 0
        val assembler = PackageAssembler(template = { byteArrayOf() }, ensureActive = {
            if (++calls > 3) throw InterruptedException()
        })
        try {
            assembler.assemble(repo, temporary.root.resolve("work"), output, options(PackageFormat.MODULE))
            throw AssertionError("预期装配被取消")
        } catch (_: InterruptedException) {
            assertFalse(output.exists())
            assertFalse(temporary.root.resolve("work").exists())
        }
    }
}
