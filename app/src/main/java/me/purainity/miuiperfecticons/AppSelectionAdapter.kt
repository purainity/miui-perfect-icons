package me.purainity.miuiperfecticons

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import me.purainity.miuiperfecticons.databinding.ItemAppSelectionBinding
import me.purainity.miuiperfecticons.ui.exposeSelectionState

/** 复用应用列表项，避免在筛选时一次性创建所有图标视图 */
class AppSelectionAdapter(
    private val packageManager: PackageManager,
    private val selected: MutableSet<String>,
    private val onSelectionChanged: () -> Unit,
) : RecyclerView.Adapter<AppSelectionAdapter.Holder>() {
    data class App(val packageName: String, val label: String, val info: ApplicationInfo)

    private var allApps = emptyList<App>()
    private var visibleApps = emptyList<App>()
    private var query = ""

    fun setApps(apps: List<App>) {
        allApps = apps
        filter(query)
    }

    fun filter(value: String) {
        query = value.trim()
        val previous = visibleApps
        val filtered = allApps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
        val changes = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size
            override fun getNewListSize() = filtered.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previous[oldItemPosition].packageName == filtered[newItemPosition].packageName
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                previous[oldItemPosition] == filtered[newItemPosition]
        }, false)
        visibleApps = filtered
        changes.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = visibleApps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemAppSelectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.root.exposeSelectionState(binding.checkbox)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = visibleApps[position]
        val row = holder.binding
        row.title.text = app.label
        row.subtitle.text = app.packageName
        row.icon.setImageDrawable(try { packageManager.getApplicationIcon(app.info) } catch (_: Exception) { packageManager.defaultActivityIcon })
        row.checkbox.setOnCheckedChangeListener(null)
        row.checkbox.isChecked = app.packageName in selected
        row.root.contentDescription = row.root.context.getString(R.string.select_app_accessibility, app.label, app.packageName)
        row.checkbox.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(app.packageName) else selected.remove(app.packageName)
            onSelectionChanged()
        }
        row.root.setOnClickListener { row.checkbox.isChecked = !row.checkbox.isChecked }
    }

    class Holder(val binding: ItemAppSelectionBinding) : RecyclerView.ViewHolder(binding.root)
}
