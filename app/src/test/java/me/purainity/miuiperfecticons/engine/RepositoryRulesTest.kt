package me.purainity.miuiperfecticons.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositoryRulesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cachedBlobRejectsSameLengthCorruption() {
        val file = temporary.newFile("icon")
        val expected = "ce013625030ba8dba906f756967f9e9ca394464a"
        file.writeText("hello\n")
        assertTrue(GitBlob.matches(file, 6, expected))
        file.writeText("world\n")
        assertFalse(GitBlob.matches(file, 6, expected))
        assertFalse(GitBlob.matches(file, 7, expected))
    }

    @Test(expected = InterruptedException::class)
    fun cachedBlobVerificationRespectsCancellation() {
        val file = temporary.newFile("cancelled")
        file.writeText("hello\n")
        GitBlob.matches(file, 6, "ce013625030ba8dba906f756967f9e9ca394464a") {
            throw InterruptedException("取消")
        }
    }

    @Test fun gitBlobDigestIncludesHeader() {
        val bytes = "hello\n".toByteArray()
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", GitBlob.digest(bytes.size.toLong(), bytes.inputStream()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun incorrectFileLengthIsRejected() {
        GitBlob.digest(10, "hello".byteInputStream())
    }

    @Test fun packageSnapshotRequiresIconsThemeAndMtzShell() {
        val paths = setOf("icons/example.png", "themes/default/icons/example.png", "addition/mtz/description.xml", "addition/mtz/com.miui.home")
        assertTrue(RepositoryRules.hasRequiredPackageFiles(paths, "default"))
        assertFalse(RepositoryRules.hasRequiredPackageFiles(paths - "addition/mtz/com.miui.home", "default"))
        assertFalse(RepositoryRules.hasRequiredPackageFiles(paths, "Hyper"))
        assertFalse(RepositoryRules.hasRequiredPackageFiles(paths - "icons/example.png", "default"))
    }

    @Test fun selectionAndPathsAreRestricted() {
        assertTrue(RepositoryRules.validPath("themes/Hyper/icons/org.example.png"))
        assertFalse(RepositoryRules.validPath("icons/../index.json"))
        assertFalse(RepositoryRules.validPath("/icons/test"))
        assertFalse(RepositoryRules.validPath("icons/evil\\path"))
        assertTrue(RepositoryRules.prefixes("default").contains("themes/common/"))
        assertFalse(RepositoryRules.prefixes("lrone").contains("themes/common/"))
        assertTrue(RepositoryRules.contains("icons/", "icons/example/0.png"))
        assertFalse(RepositoryRules.contains("icons/", "icons-other/file"))
    }
}
