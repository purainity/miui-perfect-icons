package me.purainity.miuiperfecticons.engine

import org.json.JSONObject

/** Compare API 增量合并：把“快照基线…最新提交”的变更表套用到快照条目
 *  合并不做内容校验（后续逐文件 blob SHA 校验兜底），任何无法可靠解释的输入都返回 null，由调用方回退全量 trees 清单 */
object RepositoryCompare {
    /** 超过该提交跨度回退全量清单，与 GitHub compare 的 250 提交截断保留余量 */
    const val MAX_AHEAD = 200

    /** GitHub compare 最多返回 300 个文件，达到该值视为不可靠（可能被截断） */
    const val MAX_FILES = 300

    /** 返回合并后的完整条目表；变更文件不含体积，以 -1 占位，由调用方在缓存校验或下载完成后补齐 */
    fun merge(
        base: List<GitHubRepository.Entry>,
        diff: JSONObject,
        prefixes: List<String>,
    ): List<GitHubRepository.Entry>? {
        if (diff.optString("status") != "ahead") return null
        if (diff.optInt("ahead_by", Int.MAX_VALUE) > MAX_AHEAD) return null
        val files = diff.optJSONArray("files") ?: return null
        if (files.length() >= MAX_FILES) return null
        val byPath = LinkedHashMap<String, GitHubRepository.Entry>(base.size)
        base.forEach { if (byPath.put(it.path, it) != null) return null }
        val removed = HashSet<String>()
        val changes = ArrayList<GitHubRepository.Entry>()
        val destinations = HashSet<String>()
        for (index in 0 until files.length()) {
            val file = files.getJSONObject(index)
            val path = file.optString("filename")
            val previous = file.optString("previous_filename").takeIf { it.isNotEmpty() }
            val status = file.optString("status")
            if (!RepositoryRules.validPath(path) || !destinations.add(path)) return null
            if (previous != null && !RepositoryRules.validPath(previous)) return null
            if ((status == "renamed") != (previous != null)) return null
            if (previous != null) removed.add(previous)
            when (status) {
                "removed" -> removed.add(path)
                "added", "modified", "changed", "renamed" -> {
                    if (prefixes.none { RepositoryRules.contains(it, path) }) continue
                    val sha = file.optString("sha")
                    if (!sha.matches(Regex("[0-9a-f]{40}"))) return null
                    changes.add(GitHubRepository.Entry(path, sha, -1))
                }
                else -> return null
            }
        }
        // 全部旧路径先移除，再应用新路径，避免重命名互相覆盖
        removed.forEach(byPath::remove)
        changes.forEach { byPath[it.path] = it }
        return byPath.values.toList()
    }
}
