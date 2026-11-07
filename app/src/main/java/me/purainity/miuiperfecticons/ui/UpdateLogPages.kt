package me.purainity.miuiperfecticons.ui

/** 原版日志是单个完整文件，按批显示以避免一次创建全部列表项 */
class UpdateLogPages<T>(private val pageSize: Int = 25) {
    init { require(pageSize > 0) }

    private var items = emptyList<T>()
    var visibleCount = 0
        private set

    fun replace(values: List<T>) {
        items = values.toList()
        visibleCount = minOf(pageSize, items.size)
    }

    fun loadNext(): Int {
        val previous = visibleCount
        visibleCount = minOf(visibleCount + pageSize, items.size)
        return visibleCount - previous
    }

    fun item(index: Int): T {
        require(index in 0 until visibleCount)
        return items[index]
    }
}
