package me.purainity.miuiperfecticons.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class UiLayoutRegressionTest {
    private val android = "http://schemas.android.com/apk/res/android"

    private fun elements(layout: String): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(File("src/main/res/layout/$layout.xml"))
        val nodes = document.getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test
    fun openSourceTitleAndUrlHaveNoSingleLineLimitOrEllipsis() {
        val views = elements("item_open_source_project")
        listOf("title", "projectUrl").forEach { id ->
            val view = views.single { it.getAttributeNS(android, "id") == "@+id/$id" }
            assertEquals("wrap_content", view.getAttributeNS(android, "layout_height"))
            assertFalse(view.hasAttributeNS(android, "maxLines"))
            assertFalse(view.hasAttributeNS(android, "singleLine"))
            assertFalse(view.hasAttributeNS(android, "ellipsize"))
        }
    }

    @Test
    fun customIconsSwitchUsesCheckBoxSymbol() {
        val icon = elements("item_switch_setting").single { it.tagName == "ImageView" }
        assertEquals("@drawable/ic_check_box", icon.getAttributeNS(android, "src"))
    }

    @Test
    fun selectionControlsUseThemeDefaultsAndDelegateClicksToTheirRows() {
        listOf(
            "activity_main" to "maskSwitch",
            "item_switch_setting" to "toggle",
            "item_theme_selection" to "radio",
            "item_app_selection" to "checkbox",
        ).forEach { (layout, id) ->
            val view = elements(layout).single { it.getAttributeNS(android, "id") == "@+id/$id" }
            assertFalse(view.hasAttribute("style"))
            assertEquals("false", view.getAttributeNS(android, "clickable"))
            assertEquals("false", view.getAttributeNS(android, "focusable"))
        }
    }

    @Test
    fun appSelectionUsesMaterialLoadingIndicator() {
        val indicator = elements("view_app_selection").single {
            it.getAttributeNS(android, "id") == "@+id/loadingIndicator"
        }
        assertEquals("com.google.android.material.loadingindicator.LoadingIndicator", indicator.tagName)
        assertFalse(indicator.hasAttribute("style"))
    }

    @Test
    fun taskDialogShowsStatusAndControlsWithoutLogHeading() {
        val views = elements("dialog_task_log")
        val ids = views.map { it.getAttributeNS(android, "id") }.toSet()
        listOf("title", "statusText", "progress", "progressText", "buttonSecondary", "buttonPrimary").forEach {
            assertTrue(ids.contains("@+id/$it"))
        }
        assertFalse(views.any { it.getAttributeNS(android, "text") in setOf("执行日志", "@string/task_log") })
        assertFalse(ids.contains("@+id/logText"))
        assertFalse(ids.contains("@+id/logScroll"))
        assertFalse(ids.contains("@+id/copy"))
    }
}
