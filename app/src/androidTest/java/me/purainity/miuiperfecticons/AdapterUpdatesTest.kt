package me.purainity.miuiperfecticons

import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.purainity.miuiperfecticons.engine.OriginalChangelog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdapterUpdatesTest {
    private class Updates : RecyclerView.AdapterDataObserver() {
        var allChanged = false
        val events = mutableListOf<Pair<Int, Int>>()
        override fun onChanged() { allChanged = true }
        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) { events += positionStart to itemCount }
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { events += positionStart to itemCount }
        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) { events += positionStart to itemCount }
        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            events += fromPosition to itemCount
            events += toPosition to itemCount
        }
    }

    @Test fun filteringUpdatesOnlyChangedRowsAndPreservesSelection() {
        // 复用 Activity 实际主题，否则独立加载的 Material 布局缺少主题属性
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MIUI完美图标计划)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val selected = mutableSetOf("com.example.second")
            val adapter = AppSelectionAdapter(context.packageManager, selected) { }
            val updates = Updates()
            adapter.registerAdapterDataObserver(updates)
            val info = ApplicationInfo(context.applicationInfo)
            val first = AppSelectionAdapter.App("com.example.first", "First", info)
            val second = AppSelectionAdapter.App("com.example.second", "Second", info)
            adapter.setApps(listOf(first, second))
            assertEquals(2, adapter.itemCount)
            adapter.filter("SECOND")
            assertEquals(1, adapter.itemCount)
            val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
            adapter.onBindViewHolder(holder, 0)
            assertEquals("Second", holder.binding.title.text.toString())
            assertTrue(holder.binding.checkbox.isChecked)
            adapter.filter("not found")
            assertEquals(0, adapter.itemCount)
            adapter.filter("")
            assertEquals(2, adapter.itemCount)
            assertEquals(setOf("com.example.second"), selected)
            adapter.setApps(listOf(first, second.copy(label = "Updated")))
            adapter.onBindViewHolder(holder, 1)
            assertEquals("Updated", holder.binding.title.text.toString())
            assertFalse(updates.allChanged)
            assertTrue(updates.events.isNotEmpty())
        }
    }

    @Test fun refreshAndPaginationKeepHeaderAndUseGranularUpdates() {
        // 复用 Activity 实际主题，否则独立加载的 Material 布局缺少主题属性
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_MIUI完美图标计划)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val header = View(context)
            val adapter = UpdateLogAdapter(header) { }
            val updates = Updates()
            adapter.registerAdapterDataObserver(updates)
            val entries = (1..30).map { OriginalChangelog.Entry("date $it", "message $it", null, null) }
            adapter.replace(entries)
            assertEquals(26, adapter.itemCount)
            assertTrue(adapter.loadNextPage())
            assertEquals(31, adapter.itemCount)
            assertFalse(adapter.loadNextPage())
            adapter.replace(entries.take(2))
            assertEquals(3, adapter.itemCount)
            val parent = FrameLayout(context)
            assertEquals(header, adapter.onCreateViewHolder(parent, 0).itemView)
            val holder = adapter.onCreateViewHolder(parent, 1)
            adapter.onBindViewHolder(holder, 1)
            assertEquals("message 1", holder.itemView.findViewById<TextView>(R.id.message).text.toString())
            assertFalse(updates.allChanged)
            assertTrue(updates.events.isNotEmpty())
            assertTrue(updates.events.all { (start, _) -> start > 0 })
        }
    }

    @Test fun thanksStringsKeepTheirLiteralLeadingAtSigns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expected = mapOf(
            R.string.thanks_team to "@PedroZ",
            R.string.thanks_drawers to "@PedroZ",
            R.string.thanks_license to "@江户川Conan",
            R.string.thanks_special to "@嘟嘟斯基",
            R.string.thanks_donors to "@cnzt66",
        )
        expected.forEach { (resource, prefix) -> assertTrue(context.getString(resource).startsWith(prefix)) }
    }
}
