package me.purainity.miuiperfecticons.config

import me.purainity.miuiperfecticons.engine.GitHubEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorPresetsTest {
    @Test fun presetAddressesAreWellFormed() {
        // 内置候选仅作为预填选项，地址本身必须能通过规范化且含仓库路径（域名替换式或官方前缀式）
        MirrorPresets.all.filter { it.id != MirrorPresets.custom.id }.forEach { preset ->
            preset.apiBase?.let {
                assertEquals(it, GitHubEndpoint.normalize(it))
                assertTrue(it.endsWith("/repos/pzcn/Perfect-Icons-Completion-Project"))
            }
            preset.rawBase?.let {
                // jsDelivr 形态以 @ 结尾（拼接 {base}{commit}/{path}），其余为官方地址前缀
                val normalized = GitHubEndpoint.normalize(it)
                if (it.endsWith("@")) assertEquals(it, normalized)
                else assertTrue(normalized!!.endsWith("githubusercontent.com/pzcn/Perfect-Icons-Completion-Project"))
            }
            preset.siteBase?.let {
                assertEquals(it, GitHubEndpoint.normalize(it))
                assertTrue(it.endsWith("/pzcn/Perfect-Icons-Completion-Project"))
            }
        }
    }

    @Test fun dualSourcePresetsComeBeforeRawOnly() {
        // 双源镜像排在仅文件镜像与自定义之前
        val types = MirrorPresets.all.map { it.id != MirrorPresets.custom.id && it.apiBase != null }
        val lastDual = types.lastIndexOf(true)
        val firstRawOnly = types.indexOf(false)
        assertTrue(lastDual < firstRawOnly)
    }

    @Test fun customPresetHasNoFixedAddresses() {
        assertNull(MirrorPresets.custom.apiBase)
        assertNull(MirrorPresets.custom.rawBase)
    }

    @Test fun findFallsBackToDefault() {
        assertEquals("gh-proxy", MirrorPresets.default.id)
        assertEquals("gh-proxy", MirrorPresets.find(null).id)
        assertEquals("gh-proxy", MirrorPresets.find("不存在").id)
        assertEquals("jsdelivr", MirrorPresets.find("jsdelivr").id)
    }
}
