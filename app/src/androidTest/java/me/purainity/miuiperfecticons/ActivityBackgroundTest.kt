package me.purainity.miuiperfecticons

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.color.MaterialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityBackgroundTest {
    @Test
    fun windowSuppliesSurfaceColorWithoutDuplicateRootBackground() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (nightMode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            val themed = ContextThemeWrapper(
                context.createConfigurationContext(configuration),
                R.style.Theme_MIUI完美图标计划,
            )
            val windowAttributes = themed.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
            val windowBackground = try {
                windowAttributes.getDrawable(0)
            } finally {
                windowAttributes.recycle()
            }
            assertTrue("窗口背景应为纯色", windowBackground is ColorDrawable)
            assertEquals(
                MaterialColors.getColor(themed, com.google.android.material.R.attr.colorSurface, Color.MAGENTA),
                (windowBackground as ColorDrawable).color,
            )
            for (layout in listOf(R.layout.activity_main, R.layout.activity_feature, R.layout.activity_theme_selection)) {
                val root = LayoutInflater.from(themed).inflate(layout, null, false)
                assertNull("Activity 根布局不应重复绘制背景：$layout", root.background)
            }
        }
    }
}
