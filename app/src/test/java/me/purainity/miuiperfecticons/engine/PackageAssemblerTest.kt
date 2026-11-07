package me.purainity.miuiperfecticons.engine

import me.purainity.miuiperfecticons.config.IconThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Date
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class PackageAssemblerTest {
    @get:Rule val temporary = TemporaryFolder()

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

    @Test fun mtzSeparatesFlatIconsAndCustomDynamicSurvivesDenyAll() {
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
            assertEquals("custom-flat", zip.getInputStream(zip.getEntry("miui_mod_icons/org.custom.png")).reader().readText())
            assertEquals("addon", zip.getInputStream(zip.getEntry("addons/nested/extra")).reader().readText())
            assertEquals("<title>MIUI经典主题</title>", zip.getInputStream(zip.getEntry("description.xml")).reader().readText())
            assertEquals("test", zip.getInputStream(zip.getEntry("com.miui.home")).reader().readText())
            assertTrue(zip.getEntry("module.prop") == null)
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
