package me.purainity.miuiperfecticons.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomDirectoryAccessTest {
    private val directory = "content://example/tree/icons"

    @Test
    fun reusesOnlyTheSavedDirectoryWithReadAndWriteGrants() {
        val readWrite = CustomDirectoryAccess.Grant(directory, canRead = true, canWrite = true)
        assertTrue(CustomDirectoryAccess.canReuse(directory, listOf(readWrite)))
        assertFalse(CustomDirectoryAccess.canReuse(null, listOf(readWrite)))
        assertFalse(CustomDirectoryAccess.canReuse(directory, emptyList()))
        assertFalse(CustomDirectoryAccess.canReuse(directory, listOf(readWrite.copy(uri = "content://example/tree/other"))))
        assertFalse(CustomDirectoryAccess.canReuse(directory, listOf(readWrite.copy(canRead = false))))
        assertFalse(CustomDirectoryAccess.canReuse(directory, listOf(readWrite.copy(canWrite = false))))
        assertFalse(CustomDirectoryAccess.canReuse(directory, listOf(readWrite.copy(canRead = false), readWrite.copy(canWrite = false))))
    }
}
