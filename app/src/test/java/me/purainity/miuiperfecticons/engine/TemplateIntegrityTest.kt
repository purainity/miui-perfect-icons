package me.purainity.miuiperfecticons.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class TemplateIntegrityTest {
    @Test fun embeddedInstallScriptsMatchOriginalHeredocs() {
        val original = File("../docs/original-app-scripts/local-scripts/misc/module.sh").readText()
        val assets = File("src/main/assets")
        listOf("META-INF/com/google/android/update-binary", "post-fs-data.sh", "customize.sh").forEach { path ->
            val pattern = Regex($$"cat >\\$TEMP_DIR/moduletmp/$${Regex.escape(path)} <<'EOF'\\n(.*?)\\nEOF", RegexOption.DOT_MATCHES_ALL)
            val expected = pattern.find(original)?.groupValues?.get(1) ?: throw AssertionError("模板不存在：$path")
            assertEquals(expected + "\n", File(assets, path.substringAfterLast('/')).readText())
        }
    }
}
