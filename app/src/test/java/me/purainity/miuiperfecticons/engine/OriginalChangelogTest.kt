package me.purainity.miuiperfecticons.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.ZoneId

class OriginalChangelogTest {
    @Test fun retainsTheOriginalMultilineContentAndOptionalLinks() {
        val source = """
            <group><text><title>2026.10.24 14:08</title><desc>新增两个图标

            更新 A &amp; B
            by 作者</desc><slice link="https://github.com/pzcn/repo/commit/abc" /></text></group>
            <group><text><title>2023.02.14 19:14</title><desc>更新相机图标</desc></text></group>
            <Error><Code>AccessDenied</Code></Error>
        """.trimIndent()
        val result = OriginalChangelog.parse(source)
        assertEquals(2, result.size)
        assertEquals("新增两个图标\n\n更新 A & B", result[0].message)
        assertEquals("作者", result[0].author)
        assertEquals("https://github.com/pzcn/repo/commit/abc", result[0].url)
        assertEquals("2026-10-24", OriginalChangelog.latestDate(result[0].date, ZoneId.of("Asia/Shanghai")))
        assertEquals("更新相机图标", result[1].message)
        assertNull(result[1].author)
        assertNull(result[1].url)
    }

    // Pair 是期望值，而 splitMessage 的结果是实际值
    @Suppress("KotlinMisorderedAssertEqualsArguments")
    @Test fun separatesFirstLineFromOptionalDetails() {
        assertEquals("单行更新" to null, OriginalChangelog.splitMessage("单行更新"))
        assertEquals("新增图标" to "更新详情\n第二行", OriginalChangelog.splitMessage("新增图标\n\n更新详情\n第二行"))
        assertEquals("标题" to "正文\r\n第二行", OriginalChangelog.splitMessage("标题\r\n\r\n正文\r\n第二行"))
        assertEquals("标题" to null, OriginalChangelog.splitMessage("标题\n\n"))
        assertEquals("标题" to null, OriginalChangelog.splitMessage("标题\n  "))
    }

    @Test fun originalShanghaiTimeIsDisplayedInDeviceTimeZone() {
        val date = "2026.10.24 14:08"
        assertEquals("2026.10.24 06:08", OriginalChangelog.localDate(date, ZoneId.of("UTC")))
        assertEquals("2026.10.23 23:08", OriginalChangelog.localDate(date, ZoneId.of("America/Los_Angeles")))
        assertEquals("2026-10-23", OriginalChangelog.latestDate(date, ZoneId.of("America/Los_Angeles")))
        assertEquals("未知时间", OriginalChangelog.localDate("未知时间", ZoneId.of("UTC")))
    }

    @Test fun doesNotTurnUnsafeLinksIntoActions() {
        val source = """<group><text><title>日期</title><desc>内容</desc><slice link="https://example.com/" /></text></group>"""
        val entry = OriginalChangelog.parse(source).single()
        assertNull(entry.url)
        assertNull(OriginalChangelog.latestDate(entry.date))
    }

    @Test fun rejectsDtdAndExternalEntities() {
        val source = """<!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><group><text><title>日期</title><desc>&xxe;</desc></text></group>"""
        try {
            OriginalChangelog.parse(source)
            throw AssertionError("更新日志不应解析外部实体")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("声明"))
        }
    }

    @Test(expected = IOException::class)
    fun rejectsEmptyOrUnusableLog() {
        OriginalChangelog.parse("<Error><Code>AccessDenied</Code></Error>")
    }
}
