package me.purainity.miuiperfecticons

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.purainity.miuiperfecticons.databinding.ItemCommitBinding
import me.purainity.miuiperfecticons.engine.OriginalChangelog
import me.purainity.miuiperfecticons.ui.UpdateLogPages

/** 复用日志行，只在滚动到末尾时展示下一批原版日志 */
class UpdateLogAdapter(private val header: View, private val open: (String) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val pages = UpdateLogPages<OriginalChangelog.Entry>()

    fun replace(entries: List<OriginalChangelog.Entry>) {
        val previous = (0 until pages.visibleCount).map(pages::item)
        pages.replace(entries)
        val current = (0 until pages.visibleCount).map(pages::item)
        // Keep the header at position 0; log entries have no guaranteed unique ID.
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = previous.size + 1
            override fun getNewListSize() = current.size + 1
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                if (oldItemPosition == 0 || newItemPosition == 0) oldItemPosition == newItemPosition
                else previous[oldItemPosition - 1] == current[newItemPosition - 1]
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) = true
        }, false).dispatchUpdatesTo(this)
    }

    fun loadNextPage(): Boolean {
        val first = pages.visibleCount + 1
        val added = pages.loadNext()
        if (added > 0) notifyItemRangeInserted(first, added)
        return added > 0
    }

    override fun getItemCount(): Int = pages.visibleCount + 1
    override fun getItemViewType(position: Int): Int = if (position == 0) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == 0) return HeaderHolder(header)
        return EntryHolder(ItemCommitBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder !is EntryHolder) return
        val entry = pages.item(position - 1)
        val row = holder.binding
        row.date.text = OriginalChangelog.localDate(entry.date)
        val (title, details) = OriginalChangelog.splitMessage(entry.message)
        row.message.text = title
        row.details.text = details.orEmpty()
        row.details.visibility = if (details == null) View.GONE else View.VISIBLE
        row.author.text = entry.author?.let { row.root.context.getString(R.string.updates_author, it) }.orEmpty()
        row.author.visibility = if (entry.author == null) View.GONE else View.VISIBLE
        row.external.visibility = if (entry.url == null) View.GONE else View.VISIBLE
        row.root.setOnClickListener(if (entry.url == null) null else View.OnClickListener { open(entry.url) })
        row.root.isClickable = entry.url != null
        row.root.isFocusable = entry.url != null
    }

    private class HeaderHolder(header: View) : RecyclerView.ViewHolder(header)
    private class EntryHolder(val binding: ItemCommitBinding) : RecyclerView.ViewHolder(binding.root)
}

/** 接近列表末尾时调度下一批，避免在布局和滚动回调中修改适配器 */
class UpdateLogPagingListener(
    private val manager: LinearLayoutManager,
    private val adapter: UpdateLogAdapter,
) : RecyclerView.OnScrollListener() {
    private var nextPagePending = false

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        if (!nextPagePending && manager.findLastVisibleItemPosition() >= adapter.itemCount - 3) {
            nextPagePending = true
            recyclerView.post {
                nextPagePending = false
                if (manager.findLastVisibleItemPosition() >= adapter.itemCount - 3) adapter.loadNextPage()
            }
        }
    }
}
