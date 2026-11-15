package me.purainity.miuiperfecticons.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepositoryCompareTest {
    private val prefixes = RepositoryRules.prefixes("default")
    private fun entry(path: String, sha: String = "a".repeat(40), size: Long = 10) =
        GitHubRepository.Entry(path, sha, size)

    private fun diff(status: String, aheadBy: Int = 1, files: JSONArray): JSONObject =
        JSONObject().put("status", status).put("ahead_by", aheadBy).put("behind_by", 0).put("files", files)

    private fun file(status: String, path: String, sha: String = "b".repeat(40), previous: String? = null): JSONObject =
        JSONObject().put("status", status).put("filename", path).put("sha", sha)
            .apply { previous?.let { put("previous_filename", it) } }

    @Test fun mergesModifiedAndAddedFiles() {
        val base = listOf(entry("icons/a/0.png", sha = "1".repeat(40)), entry("icons/b/0.png", sha = "2".repeat(40)))
        val files = JSONArray()
            .put(file("modified", "icons/a/0.png", sha = "3".repeat(40)))
            .put(file("added", "icons/new/1.png", sha = "4".repeat(40)))
        val merged = RepositoryCompare.merge(base, diff("ahead", files = files), prefixes)!!
        assertEquals(3, merged.size)
        assertEquals("3".repeat(40), merged.first { it.path == "icons/a/0.png" }.sha)
        // Compare 清单不含体积，以 -1 占位
        assertEquals(-1L, merged.first { it.path == "icons/new/1.png" }.size)
        assertEquals(10L, merged.first { it.path == "icons/b/0.png" }.size)
    }

    @Test fun appliesRemovedAndRenamed() {
        val base = listOf(entry("icons/a/0.png"), entry("icons/b/0.png"), entry("icons/c/0.png"))
        val files = JSONArray()
            .put(file("removed", "icons/a/0.png"))
            .put(file("renamed", "icons/d/0.png", previous = "icons/b/0.png"))
        val merged = RepositoryCompare.merge(base, diff("ahead", files = files), prefixes)!!
        assertEquals(listOf("icons/c/0.png", "icons/d/0.png"), merged.map { it.path })
    }

    @Test fun appliesRenamesWithoutDependingOnResponseOrder() {
        val base = listOf(entry("icons/a.png"), entry("icons/b.png"))
        val files = JSONArray()
            .put(file("renamed", "icons/b.png", sha = "1".repeat(40), previous = "icons/a.png"))
            .put(file("renamed", "icons/c.png", sha = "2".repeat(40), previous = "icons/b.png"))
        val merged = RepositoryCompare.merge(base, diff("ahead", files = files), prefixes)!!
        assertEquals(setOf("icons/b.png", "icons/c.png"), merged.map { it.path }.toSet())
        assertEquals("1".repeat(40), merged.first { it.path == "icons/b.png" }.sha)
    }

    @Test fun rejectsAmbiguousRenameAndDuplicateDestination() {
        val base = listOf(entry("icons/a.png"))
        assertNull(RepositoryCompare.merge(base, diff("ahead", files = JSONArray()
            .put(file("renamed", "icons/b.png"))), prefixes))
        assertNull(RepositoryCompare.merge(base, diff("ahead", files = JSONArray()
            .put(file("added", "icons/b.png")).put(file("modified", "icons/b.png"))), prefixes))
    }

    @Test fun ignoresFilesOutsidePrefixes() {
        val base = listOf(entry("icons/a/0.png"))
        val files = JSONArray()
            .put(file("modified", "emui/something.png"))
            .put(file("modified", "icons/a/0.png", sha = "3".repeat(40)))
        val merged = RepositoryCompare.merge(base, diff("ahead", files = files), prefixes)!!
        assertEquals(listOf("icons/a/0.png"), merged.map { it.path })
        assertEquals("3".repeat(40), merged.single().sha)
    }

    @Test fun rejectsUnreliableDiffs() {
        val base = listOf(entry("icons/a/0.png"))
        // 分叉或落后：无法以基线推算，回退全量清单
        assertNull(RepositoryCompare.merge(base, diff("diverged", files = JSONArray()), prefixes))
        assertNull(RepositoryCompare.merge(base, diff("behind", files = JSONArray()), prefixes))
        // 提交跨度过大：变更表可能被截断
        assertNull(RepositoryCompare.merge(base, diff("ahead", aheadBy = RepositoryCompare.MAX_AHEAD + 1, files = JSONArray()), prefixes))
        // 文件数达到 GitHub compare 上限：可能被截断
        val many = JSONArray()
        repeat(RepositoryCompare.MAX_FILES) { many.put(file("removed", "icons/a/$it.png")) }
        assertNull(RepositoryCompare.merge(base, diff("ahead", files = many), prefixes))
    }

    @Test fun rejectsMalformedEntries() {
        val base = listOf(entry("icons/a/0.png"))
        assertNull(RepositoryCompare.merge(base, diff("ahead", files = JSONArray().put(file("modified", "icons/a/0.png", sha = "short"))), prefixes))
        assertNull(RepositoryCompare.merge(base, diff("ahead", files = JSONArray().put(file("unknown", "icons/a/0.png"))), prefixes))
        assertNull(RepositoryCompare.merge(base, diff("ahead", files = JSONArray().put(file("modified", "../escape.png"))), prefixes))
    }
}
