package me.purainity.miuiperfecticons.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RepositoryCacheTest {
    @Test
    fun snapshotRejectsSameLengthCorruptionAndCanBeCancelled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "cache-test-${UUID.randomUUID()}")
        try {
            val paths = listOf("icons/example.png", "themes/default/icons/example.png", "addition/mtz/description.xml", "addition/mtz/com.miui.home")
            val sha = "ce013625030ba8dba906f756967f9e9ca394464a"
            val index = JSONObject()
            val entries = JSONArray()
            paths.forEach { path ->
                val item = File(directory, "repo/$path")
                item.parentFile!!.mkdirs()
                item.writeText("hello\n")
                index.put(path, sha)
                entries.put(JSONObject().put("path", path).put("sha", sha).put("size", 6))
            }
            val file = File(directory, "repo/${paths.first()}")
            val snapshot = File(directory, "snapshot-default.json")
            File(directory, "index.json").writeText(JSONObject().put("paths", index).toString())
            snapshot.writeText(JSONObject().put("files", entries).toString())

            val repository = GitHubRepository(directory)
            val invalidThemeRepository = GitHubRepository(directory, ensureActive = { throw AssertionError("非法主题不应进入缓存校验") })
            listOf("", "../default", "default/other", "default\\other").forEach { themeId ->
                assertFalse(invalidThemeRepository.hasCompleteSnapshot(themeId))
            }
            assertTrue(repository.hasCompleteSnapshot("default"))
            snapshot.writeText(JSONObject().put("files", JSONArray().put(entries.getJSONObject(0))).toString())
            assertFalse(repository.hasCompleteSnapshot("default"))
            snapshot.writeText(JSONObject().put("files", entries).toString())
            val extra = File(file.parentFile, "download.tmp").apply { writeText("残留") }
            assertFalse(repository.hasCompleteSnapshot("default"))
            assertTrue(extra.delete())
            file.writeText("world\n")
            assertFalse(repository.hasCompleteSnapshot("default"))
            try {
                GitHubRepository(directory, { throw InterruptedException("取消") }).hasCompleteSnapshot("default")
                fail("缓存校验应响应取消")
            } catch (_: InterruptedException) {
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun archiveCacheRequiresCompleteVerifiedManifest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "archive-cache-test-${UUID.randomUUID()}")
        try {
            val path = "addition/Icons_Archiving/nested/icon.png"
            val file = File(directory, "repo/$path")
            assertTrue(file.parentFile!!.mkdirs())
            file.writeText("hello\n")
            File(file.parentFile, "untracked.tmp").writeText("临时内容")
            val sha = "ce013625030ba8dba906f756967f9e9ca394464a"
            val entry = JSONObject().put("path", path).put("sha", sha).put("size", 6)
            val snapshot = File(directory, "archive-snapshot.json")
            File(directory, "index.json").writeText(JSONObject().put("paths", JSONObject().put(path, sha)).toString())
            snapshot.writeText(JSONObject().put("files", JSONArray().put(entry)).toString())
            val repository = GitHubRepository(directory)
            assertTrue(repository.cachedArchiveEntries()?.map { it.path } == listOf(path))

            file.writeText("world\n")
            assertTrue(repository.cachedArchiveEntries() == null)
            file.writeText("hello\n")
            snapshot.writeText(JSONObject().put("files", JSONArray().put(entry).put(entry)).toString())
            assertTrue(repository.cachedArchiveEntries() == null)
            snapshot.writeText(JSONObject().put("files", JSONArray().put(
                JSONObject().put("path", "icons/outside.png").put("sha", sha).put("size", 6),
            )).toString())
            assertTrue(repository.cachedArchiveEntries() == null)
        } finally {
            directory.deleteRecursively()
        }
    }

}
