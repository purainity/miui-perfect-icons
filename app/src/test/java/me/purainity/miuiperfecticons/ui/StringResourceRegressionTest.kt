package me.purainity.miuiperfecticons.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourceRegressionTest {
    @Test fun thanksStringsEscapeLiteralLeadingAtSigns() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val strings = document.getElementsByTagName("string")
        val values = (0 until strings.length).map { strings.item(it) as Element }
            .associate { it.getAttribute("name") to it.textContent }
        listOf("thanks_team", "thanks_drawers", "thanks_license", "thanks_special", "thanks_donors").forEach {
            assertTrue("$it must begin with an escaped literal @", values.getValue(it).startsWith("\\@"))
        }
    }
}
