package me.purainity.miuiperfecticons.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 应用不随包分发许可证全文与组件清单，此处仅校验开源项目条目的完整性 */
class OpenSourceLicensesTest {
    @Test
    fun openSourceProjectsAreCompleteAndUnique() {
        val projects = OpenSourceProjects.all
        assertEquals(projects.size, projects.map { it.name }.distinct().size)
        projects.forEach { project ->
            assertTrue(project.name.isNotBlank() && project.developer.isNotBlank() && project.license.isNotBlank())
            assertTrue(project.url.startsWith("https://"))
            assertTrue(listOf(project.name, project.developer, project.license).none { '\n' in it })
            assertTrue(listOf(project.name, project.developer).all { value -> value.all { it.code in 0x20..0x7e } })
        }
        assertTrue(projects.none { it.name.contains("Android robot", ignoreCase = true) || it.name.contains("Android 机器人") })
        val remix = projects.single { it.name == "Remix Icon" }
        assertEquals("Apache License 2.0", remix.license)
        assertTrue(remix.url.endsWith("/tree/v4.6.0"))
    }
}
