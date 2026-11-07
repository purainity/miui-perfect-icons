package me.purainity.miuiperfecticons.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

class ArchivePackerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun packsOnlyVerifiedFiles() {
        val root = temporary.newFolder("archive")
        val selected = File(root, "nested/icon.png").apply { parentFile!!.mkdirs(); writeText("icon") }
        File(root, "download-abandoned.tmp").writeText("临时内容")
        val output = File(temporary.root, "archive.zip")
        ArchivePacker().pack(root, listOf("nested/icon.png"), output)
        ZipFile(output).use { zip ->
            assertEquals(listOf("nested/icon.png"), zip.entries().asSequence().map { it.name }.toList())
            assertArrayEquals(selected.readBytes(), zip.getInputStream(zip.getEntry("nested/icon.png")).readBytes())
        }
    }

    @Test fun rejectsInvalidPathsAndDeletesPartialOutput() {
        val root = temporary.newFolder("archive")
        File(root, "good.png").writeText("icon")
        val output = File(temporary.root, "archive.zip")
        try {
            ArchivePacker().pack(root, listOf("good.png", "invalid/../escape"), output)
            throw AssertionError("无效路径应被拒绝")
        } catch (_: IOException) {
            assertFalse(output.exists())
        }
    }

    @Test fun ioFailureDuringPackingPropagatesAndDeletesPartialOutput() {
        val root = temporary.newFolder("archive")
        val original = File(root, "icon.png").apply { writeBytes(ByteArray(20_000) { it.toByte() }) }
        val output = File(temporary.root, "archive.zip")
        var checks = 0
        try {
            ArchivePacker {
                // 在首段数据写入之后模拟 I/O 失败，验证异常不会被吞掉
                if (++checks == 3) throw IOException("模拟写入失败")
            }.pack(root, listOf("icon.png"), output)
            throw AssertionError("写入失败应向上报告")
        } catch (error: IOException) {
            assertEquals("模拟写入失败", error.message)
            assertFalse(output.exists())
            assertTrue(original.isFile)
        }
    }

    @Test fun cancellationDeletesPartialOutput() {
        val root = temporary.newFolder("archive")
        File(root, "icon.png").writeText("icon")
        val output = File(temporary.root, "archive.zip")
        try {
            ArchivePacker { throw InterruptedException("取消") }.pack(root, listOf("icon.png"), output)
            throw AssertionError("打包应响应取消")
        } catch (_: InterruptedException) {
            assertFalse(output.exists())
        }
        assertTrue(root.isDirectory)
    }
}
