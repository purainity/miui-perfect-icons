package me.purainity.miuiperfecticons.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconThemesTest {
    @Test
    fun themeCatalogContainsAllUniqueSpecifiedIds() {
        assertEquals(19, IconThemes.all.size)
        assertEquals(19, IconThemes.all.map { it.id }.toSet().size)
        assertEquals("MIUI经典", IconThemes.find("default").displayName)
        assertEquals("HyperOS主题", IconThemes.find("Hyper").assemblyName)
        assertEquals("EMUI-未来之翼", IconThemes.find("Reconstruction").displayName)
    }

    // JUnit 4 的 assertEquals 参数顺序是 expected、actual。
    @Suppress("KotlinMisorderedAssertEqualsArguments")
    @Test
    fun unknownOrMissingThemesFallBackToDefault() {
        assertEquals(IconThemes.default, IconThemes.find(null))
        assertEquals(IconThemes.default, IconThemes.find("missing"))
        assertEquals(IconThemes.default, IconThemes.find("hyper"))
    }

    @Test
    fun newMaskDefaultsToEnabledOnlyAboveVersion14() {
        assertFalse(MaskDefaults.useNewMask(null))
        assertFalse(MaskDefaults.useNewMask(""))
        assertFalse(MaskDefaults.useNewMask("unknown"))
        assertFalse(MaskDefaults.useNewMask("14"))
        assertTrue(MaskDefaults.useNewMask("15"))
    }
}
