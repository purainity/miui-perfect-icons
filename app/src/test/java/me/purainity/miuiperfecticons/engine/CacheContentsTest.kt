package me.purainity.miuiperfecticons.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.io.IOException

class CacheContentsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun removesOnlyFilesOutsideSelectedSnapshot() {
        val root = temporary.newFolder("repo")
        val kept = File(root, "icons/current/icon.png").apply { parentFile!!.mkdirs(); writeText("ok") }
        val obsolete = File(root, "icons/old/partial.png").apply { parentFile!!.mkdirs(); writeText("partial") }
        val temp = File(root, "icons/current/download.tmp").apply { writeText("temporary") }
        val otherTheme = File(root, "themes/other/icon.png").apply { parentFile!!.mkdirs(); writeText("other") }
        val expected = setOf("icons/current/icon.png")
        val prefixes = listOf("icons/", "themes/explore/")
        val extras = CacheContents.unexpected(root, prefixes, expected)
        assertEquals(setOf(obsolete.parentFile, temp), extras.toSet())
        CacheContents.removeUnexpected(extras)
        assertTrue(kept.exists())
        assertTrue(otherTheme.exists())
        assertFalse(obsolete.exists())
        assertFalse(temp.exists())
        assertTrue(CacheContents.unexpected(root, prefixes, expected).isEmpty())
    }

    @Test fun rejectsSymbolicLinkAsCacheRoot() {
        val outside = temporary.newFolder("outside")
        val protected = File(outside, "keep.png").apply { writeText("keep") }
        val link = File(temporary.root, "linked-repo")
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
            return
        }
        try {
            CacheContents.unexpected(link, listOf("icons/"), emptySet())
            throw AssertionError("缓存根目录符号链接应被拒绝")
        } catch (_: IOException) {
            assertTrue(protected.exists())
        }
    }

    @Test fun detectsSymbolicLinksInPrefixAncestors() {
        val root = temporary.newFolder("repo")
        val outside = temporary.newFolder("outside")
        val protected = File(outside, "mtz/keep").apply { parentFile!!.mkdirs(); writeText("keep") }
        val link = File(root, "addition")
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
            return
        }
        val extras = CacheContents.unexpected(root, listOf("addition/mtz/"), setOf("addition/mtz/expected"))
        assertEquals(listOf(link), extras)
        CacheContents.removeUnexpected(extras)
        assertTrue(protected.exists())
    }

    @Test fun doesNotFollowOrDeleteSymbolicLinkTargets() {
        val root = temporary.newFolder("repo")
        val outside = temporary.newFolder("outside")
        val protected = File(outside, "keep.png").apply { writeText("keep") }
        val icons = File(root, "icons").apply { mkdirs() }
        val link = File(icons, "untracked")
        try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
        } catch (error: Exception) {
            assumeNoException(error)
            return
        }
        val extras = CacheContents.unexpected(root, listOf("icons/"), setOf("icons/wanted.png"))
        assertEquals(listOf(link), extras)
        CacheContents.removeUnexpected(extras)
        assertFalse(Files.exists(link.toPath()))
        assertTrue(protected.exists())
    }
}
