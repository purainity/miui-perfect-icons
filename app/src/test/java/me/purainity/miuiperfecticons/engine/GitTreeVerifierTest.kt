package me.purainity.miuiperfecticons.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.IOException

/** 使用 git write-tree 生成的固定夹具，独立校验 Git 树对象的计算方式 */
internal object GitTreeFixture {
    const val ROOT = "a2263ed19d8d92cd2a1054da6222722414c96cd6"
    val contents = mapOf(
        "addition/Icons_Archiving/old.png" to "archive-data",
        "addition/mtz/com.miui.home" to "home",
        "addition/mtz/description.xml" to "description",
        "addition/transform_config.xml" to "mask1",
        "addition/transform_config2.xml" to "mask2",
        "icons/pkg/0.png" to "icon-data",
        "themes/default/icons/a.png" to "theme-data",
    )
    private val lines = listOf(
        Triple("addition", "tree", "9bf5ee84278a969ac531415294c5c9a8aaf6940f"),
        Triple("addition/Icons_Archiving", "tree", "f86dff9e17bbbbb3cf18adb0b7250f5812020a5d"),
        Triple("addition/Icons_Archiving/old.png", "blob", "6a1ae7c3b332bc213d4131c5bb9e3c33cb24b121"),
        Triple("addition/mtz", "tree", "35e6941554face62a41249fd8e35b48826bc46d2"),
        Triple("addition/mtz/com.miui.home", "blob", "0247178b6e3882527064d6ba6f0fede512f31d68"),
        Triple("addition/mtz/description.xml", "blob", "b8c440dd3aba59c0e29d167904de374f037a0485"),
        Triple("addition/transform_config.xml", "blob", "b2c8622c2de36d331016b539ad4d852d9e02adcf"),
        Triple("addition/transform_config2.xml", "blob", "d9c3cd1b74079bd81251c654e146cec61a14943f"),
        Triple("icons", "tree", "56abdc5d4709f38fff517b25f2e83f095c39d9c2"),
        Triple("icons/pkg", "tree", "c8410438688863e13a5362f23973f59ebc9da33c"),
        Triple("icons/pkg/0.png", "blob", "c60f459a72ac8ae29c803bf693f2a480b5882123"),
        Triple("themes", "tree", "dc72399f7c458b017934b303fc3d6053593beadd"),
        Triple("themes/default", "tree", "bd8819f0465edad5d0c21b7170d1f02ef18f7ca1"),
        Triple("themes/default/icons", "tree", "f87ab76f47489fa18f26faa34ed3173dd4b21790"),
        Triple("themes/default/icons/a.png", "blob", "978f4945dea94ae2a9587883fc25672e354e67f7"),
    )
    fun entries(): JSONArray = JSONArray().apply {
        lines.reversed().forEach { (path, type, sha) ->
            put(JSONObject().put("path", path).put("type", type).put("sha", sha)
                .put("mode", if (type == "tree") "040000" else "100644")
                .put("size", contents[path]?.toByteArray()?.size ?: 0))
        }
    }
}

class GitTreeVerifierTest {
    @Test fun verifiesGitProducedTreeIndependentlyOfResponseOrder() {
        GitTreeVerifier.verify(GitTreeFixture.entries(), GitTreeFixture.ROOT)
    }

    @Test fun rejectsModifiedMissingOrDuplicatedEntries() {
        val changed = GitTreeFixture.entries()
        changed.getJSONObject(0).put("sha", "a".repeat(40))
        assertThrows(IOException::class.java) { GitTreeVerifier.verify(changed, GitTreeFixture.ROOT) }
        val missing = GitTreeFixture.entries().apply { remove(0) }
        assertThrows(IOException::class.java) { GitTreeVerifier.verify(missing, GitTreeFixture.ROOT) }
        val duplicate = GitTreeFixture.entries().apply { put(getJSONObject(0)) }
        assertThrows(IOException::class.java) { GitTreeVerifier.verify(duplicate, GitTreeFixture.ROOT) }
    }

    @Test fun respondsToCancellation() {
        assertThrows(InterruptedException::class.java) {
            GitTreeVerifier.verify(GitTreeFixture.entries(), GitTreeFixture.ROOT) { throw InterruptedException("取消") }
        }
    }

    @Test fun verifiesOptionalRealGitHubTree() {
        val file = System.getenv("MIUI_TEST_TREE")?.let(::File)
        org.junit.Assume.assumeTrue(file?.isFile == true)
        val fixture = JSONObject(file!!.readText())
        GitTreeVerifier.verify(fixture.getJSONArray("tree"), fixture.getString("root"))
    }
}
