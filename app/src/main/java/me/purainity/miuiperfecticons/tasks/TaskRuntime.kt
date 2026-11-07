package me.purainity.miuiperfecticons.tasks

import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/** 单个任务对外的完整状态快照 */
data class TaskSnapshot(
    val running: Boolean = false,
    val kind: String = "",
    val stage: String = "",
    val statusText: String = "",
    val completedBytes: Long = 0,
    val totalBytes: Long = 0,
    val resultUri: Uri? = null,
    val resultMime: String = "application/octet-stream",
    val resultLabel: String? = null,
    val error: String? = null,
    val canUseCache: Boolean = false,
)

/** 服务与页面共享状态，页面销毁后后台任务仍然继续 */
object TaskRuntime {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(TaskSnapshot) -> Unit>()
    private var snapshot = TaskSnapshot()
    private var clearingCache = false
    val cancelled = AtomicBoolean(false)

    @Synchronized fun current(): TaskSnapshot = snapshot

    /** 同一时间仅允许一个任务或一次缓存清理 */
    @Synchronized fun begin(kind: String, stage: String = ""): Boolean {
        if (snapshot.running || clearingCache) return false
        cancelled.set(false)
        snapshot = TaskSnapshot(running = true, kind = kind, stage = stage)
        publish()
        return true
    }

    @Synchronized fun beginCacheClear(): Boolean {
        if (snapshot.running || clearingCache) return false
        clearingCache = true
        return true
    }
    @Synchronized fun endCacheClear() { clearingCache = false }
    @Synchronized fun isCacheClearing(): Boolean = clearingCache

    @Synchronized fun update(transform: (TaskSnapshot) -> TaskSnapshot) {
        snapshot = transform(snapshot)
        publish()
    }

    /** 面板正文显示当前步骤的详细状态文本，无细分步骤时由界面回退到阶段名 */
    fun status(text: String) = update { it.copy(statusText = text) }
    fun observe(listener: (TaskSnapshot) -> Unit) {
        listeners.add(listener)
        main.post { if (listeners.contains(listener)) listener(current()) }
    }
    fun remove(listener: (TaskSnapshot) -> Unit) { listeners.remove(listener) }
    /** 各阶段轮询调用，任务被取消时抛出中断 */
    fun ensureActive() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedException("操作已取消")
    }
    private fun publish() {
        val value = snapshot
        main.post { listeners.forEach { it(value) } }
    }
}
