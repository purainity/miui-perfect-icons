package me.purainity.miuiperfecticons.config

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsTest {
    private lateinit var baseContext: Context
    private lateinit var isolatedContext: Context

    @Before
    fun setUp() {
        baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        baseContext.deleteSharedPreferences(PREFERENCES_NAME)
        isolatedContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
                super.getSharedPreferences(PREFERENCES_NAME, mode)
        }
    }

    @After
    fun tearDown() {
        baseContext.deleteSharedPreferences(PREFERENCES_NAME)
    }

    @Test
    fun settingsPersistAcrossInstances() {
        val first = AppSettings(isolatedContext)
        val chosenTheme = IconThemes.find("Hyper")
        val directory = Uri.parse("content://example/tree/primary%3ADocuments")
        first.theme = chosenTheme
        first.useNewMask = true
        first.outputDirectory = directory
        first.customDirectory = directory
        first.customEnabled = true
        first.disableAllDynamic = true
        first.disabledPackages = setOf("org.example.one", "org.example.two")

        val second = AppSettings(isolatedContext)
        assertEquals(chosenTheme, second.theme)
        assertEquals(true, second.useNewMask)
        assertEquals(directory, second.outputDirectory)
        assertEquals(directory, second.customDirectory)
        assertEquals(true, second.customEnabled)
        assertEquals(true, second.disableAllDynamic)
        assertEquals(setOf("org.example.one", "org.example.two"), second.disabledPackages)

        second.useNewMask = false
        second.outputDirectory = null
        second.customEnabled = false
        val third = AppSettings(isolatedContext)
        assertFalse(third.useNewMask)
        assertNull(third.outputDirectory)
        assertFalse(third.customEnabled)
        assertEquals(directory, third.customDirectory)
    }

    private companion object {
        const val PREFERENCES_NAME = "settings_instrumentation_test"
    }
}
