package me.purainity.miuiperfecticons.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class RateLimitException(message: String) : IOException(message)

/** 令牌被 GitHub 拒绝（401），属确定性失败，不进入重试 */
private class AuthorizationFailure(message: String) : IOException(message)

/** 缓存按仓库路径存放，索引中的 blob SHA 只在文件校验成功后更新 */
class GitHubRepository(
    val directory: File,
    private val ensureActive: () -> Unit = {},
    private val log: (String) -> Unit = {},
    private val progress: (Long, Long) -> Unit = { _, _ -> },
    private val endpoint: GitHubEndpoint = GitHubEndpoint(),
    private val trustedEndpoint: GitHubEndpoint = GitHubEndpoint(token = endpoint.token),
) {
    /** size 为 -1 表示体积未知（Compare 清单不含体积），校验通过后按实际字节数回填 */
    data class Entry(val path: String, val sha: String, var size: Long)

    private val indexFile = File(directory, "index.json")
    private val indexLock = Any()
    private var index = readIndex()
    private var dirty = 0
    val files: File get() = File(directory, "repo")
    private val mirroredApi: Boolean get() = endpoint.apiBase != trustedEndpoint.apiBase

    fun cachedCommit(): String? = synchronized(indexLock) { index.optString("commit").takeIf { it.isNotEmpty() } }
    fun cachedDate(): Long = synchronized(indexLock) { index.optLong("date", 0) }
    fun hasCompleteSnapshot(themeId: String): Boolean {
        return themeId.matches(Regex("[A-Za-z0-9_-]+")) && try {
            val entries = validatedSnapshot(File(directory, "snapshot-$themeId.json")) ?: return false
            val prefixes = RepositoryRules.prefixes(themeId)
            if (entries.any { entry -> prefixes.none { RepositoryRules.contains(it, entry.path) } }) return false
            val wanted = entries.mapTo(HashSet()) { it.path }
            if (!RepositoryRules.hasRequiredPackageFiles(wanted, themeId)) return false
            CacheContents.unexpected(files, prefixes, wanted, ensureActive).isEmpty()
        } catch (error: InterruptedException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    /** 仅复用已完成且内容校验通过的图标存档，避免将未完成的下载当作完整缓存 */
    fun cachedArchiveEntries(): List<Entry>? =
        validatedSnapshot(File(directory, "archive-snapshot.json"), RepositoryRules.ARCHIVE_PREFIX)

    private fun validatedSnapshot(snapshotFile: File, prefix: String? = null): List<Entry>? {
        return try {
            val snapshot = JSONObject(snapshotFile.readText())
            if (snapshot.optInt("version") != SNAPSHOT_VERSION) return null
            val items = snapshot.getJSONArray("files")
            if (items.length() == 0) return null
            val current = synchronized(indexLock) { index.getJSONObject("paths") }
            val seen = HashSet<String>()
            val result = ArrayList<Entry>(items.length())
            for (i in 0 until items.length()) {
                ensureActive()
                val item = items.getJSONObject(i)
                val path = item.getString("path")
                val sha = item.getString("sha")
                val size = item.getLong("size")
                if (!RepositoryRules.validPath(path) || (prefix != null && !path.startsWith(prefix)) ||
                    !seen.add(path) || !sha.matches(Regex("[0-9a-f]{40}")) || size < 0 ||
                    current.optString(path) != sha || !GitBlob.matches(safeFile(path), size, sha, ensureActive)) return null
                result.add(Entry(path, sha, size))
            }
            result
        } catch (error: InterruptedException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    fun iconCount(): Int? = File(files, "icons").takeIf { it.isDirectory }?.listFiles()?.count { it.isDirectory }

    /** 版本检查：优先主站 Atom feed（不占 API 限额），失败回退 commits 接口（ETag 命中 304 时复用上次结果） */
    fun latestCommit(): String = synchronized(API_LOCK) {
        // 自定义 API 镜像没有主站镜像时直接使用镜像 API，避免访问官方主站
        if (!mirroredApi || endpoint.siteBase != trustedEndpoint.siteBase) {
            probeAtomFeed(endpoint.atomUrl())?.let { return@synchronized it }
        }
        val versionEndpoint = endpoint
        // ETag 只能与同一 API 地址及其响应的 SHA 配对复用
        val metadata = readApiMetadata().takeIf { it.optString("base") == versionEndpoint.apiBase } ?: JSONObject()
        val etag = metadata.optString("etag").takeIf(String::isNotEmpty)
        val cachedLatest = metadata.optString("latest").takeIf(String::isNotEmpty)
        val url = versionEndpoint.apiUrl("commits?per_page=1")
        var response = requestBytes(url, if (cachedLatest == null) null else etag)
        if (response.code == 304 && cachedLatest != null) return@synchronized cachedLatest
        if (response.code == 304) response = requestBytes(url)
        val array = JSONArray(response.body.toString(Charsets.UTF_8))
        val sha = array.getJSONObject(0).getString("sha")
        requireSha(sha)
        saveLatest(versionEndpoint.apiBase, sha, response.etag)
        sha
    }

    /** Atom feed 属优化路径：单次请求不重试，任何失败都回退 commits 接口 */
    private fun probeAtomFeed(url: String): String? {
        return try {
            ensureActive()
            val connection = open(url)
            try {
                if (connection.responseCode != 200) return null
                // feed 顶部条目即最新提交，条目链接以 /commit/{sha} 结尾
                Regex("/commit/([0-9a-f]{40})").find(connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
                    ?.groupValues?.get(1)?.also { requireSha(it) }
            } finally {
                connection.disconnect()
            }
        } catch (error: InterruptedException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun readApiMetadata(): JSONObject = try {
        JSONObject(File(directory, "api.json").readText())
    } catch (_: Exception) {
        JSONObject()
    }

    private fun saveLatest(base: String, sha: String, etag: String?) {
        directory.mkdirs()
        val temp = File(directory, "api.json.tmp")
        temp.writeText(JSONObject().put("base", base).put("latest", sha).put("etag", etag ?: "").toString())
        replace(temp, File(directory, "api.json"))
    }

    /** 必须直接请求 API，不能以不携带令牌的 Atom 结果代替令牌验证 */
    fun verifyToken() {
        val response = requestBytes(endpoint.apiUrl("commits?per_page=1"))
        val sha = JSONArray(response.body.toString(Charsets.UTF_8)).getJSONObject(0).getString("sha")
        requireSha(sha)
    }

    private fun mirrorTreeSha(commit: String): String {
        val response = requestBytes(endpoint.apiUrl("git/commits/$commit"))
        val metadata = JSONObject(response.body.toString(Charsets.UTF_8))
        if (metadata.getString("sha") != commit) throw IOException("GitHub 返回的提交不一致")
        return metadata.getJSONObject("tree").getString("sha").also(::requireSha)
    }

    fun synchronize(themeId: String, archiveOnly: Boolean = false): String {
        val commit = latestCommit()
        val prefixes = RepositoryRules.prefixes(themeId, archiveOnly)
        val snapshotName = if (archiveOnly) "archive-snapshot.json" else "snapshot-$themeId.json"
        // 官方 API 优先 Compare 增量，镜像 API 校验完整清单与其根树的一致性
        val selected = selectEntries(commit, prefixes, snapshotName)
        if (selected.isEmpty()) throw IOException("仓库文件清单为空")
        if (!archiveOnly && !RepositoryRules.hasRequiredPackageFiles(selected.mapTo(HashSet()) { it.path }, themeId)) {
            throw IOException("仓库中缺少主图标库、所选主题或 MTZ 外壳")
        }
        // 逐文件校验 blob SHA，体积未知时优先以本地文件大小验证缓存
        val pending = selected.filterNot { isCached(it) }
        // 任一文件体积未知时显示不定进度，避免串行 HEAD 阻塞下载
        val total = if (pending.any { it.size < 0 }) 0L else pending.sumOf { it.size }
        if (pending.isEmpty()) {
            log("主图标仓库没有更新，无需下载")
        } else {
            val done = AtomicLong(0)
            progress(0, total)
            try {
                // 重试耗尽的文件不中断整体，结束后统一补一轮，仍失败才报错
                var failures = downloadRound(pending, commit, done, total)
                if (failures.isNotEmpty()) {
                    log("正在重试 ${failures.size} 个失败文件…")
                    failures = downloadRound(failures.map { it.entry }, commit, done, total)
                }
                if (failures.isNotEmpty()) {
                    val failure = failures.first()
                    val damaged = generateSequence(failure.cause as Throwable?) { it.cause }
                        .any { it.message?.contains("下载损坏") == true || it.message?.contains("文件大小与清单不一致") == true }
                    val message = if (damaged) "文件下载损坏，请重新尝试下载" else "文件下载失败"
                    throw IOException("$message：${failure.entry.path}", failure.cause)
                }
            } finally {
                synchronized(indexLock) { writeIndex() }
            }
        }
        ensureActive()
        val wanted = selected.mapTo(HashSet()) { it.path }
        // 清除中断下载遗留的文件，避免其混入随后装配的产物
        CacheContents.removeUnexpected(CacheContents.unexpected(files, prefixes, wanted, ensureActive), ensureActive)
        if (selected.any { entry ->
                ensureActive()
                val file = safeFile(entry.path)
                !file.isFile || file.length() != entry.size
            }) throw IOException("缓存文件不完整，请重新下载资源")
        synchronized(indexLock) {
            val paths = index.getJSONObject("paths")
            val old = paths.keys().asSequence().toList()
            old.filter { path -> prefixes.any { RepositoryRules.contains(it, path) } && path !in wanted }.forEach { path ->
                val removed = safeFile(path)
                removed.delete()
                var parent = removed.parentFile
                val root = files.canonicalFile
                while (parent != null && parent != root && parent.isDirectory && parent.list()?.isEmpty() == true) {
                    if (!parent.delete()) break
                    parent = parent.parentFile
                }
                paths.remove(path)
            }
            if (!archiveOnly) {
                index.put("commit", commit)
                index.put("date", System.currentTimeMillis())
            }
            writeIndex()
            val snapshot = JSONObject().put("version", SNAPSHOT_VERSION).put("commit", commit)
                .put("apiBase", endpoint.apiBase).put("files", JSONArray().apply {
                    selected.forEach { put(JSONObject().put("path", it.path).put("sha", it.sha).put("size", it.size)) }
                })
            val temporary = File(directory, "$snapshotName.tmp")
            temporary.writeText(snapshot.toString())
            replace(temporary, File(directory, snapshotName))
        }
        return commit
    }

    private data class DownloadFailure(val entry: Entry, val cause: Exception)

    /** 文件清单选择：快照基线与最新提交一致时直接复用（零额外请求）；
     *  有基线且可比较时套用 Compare 变更表；否则拉取全量 trees 清单 */
    private fun selectEntries(commit: String, prefixes: List<String>, snapshotName: String): List<Entry> {
        val base = readSnapshotBase(File(directory, snapshotName))
        if (base != null) {
            if (base.first == commit) return base.second
            // 镜像 Compare 无法证明变更表完整，更新时改用可验证的完整树
            val merged = if (mirroredApi) null else probeCompare(base.first, commit)?.let { diff ->
                try { RepositoryCompare.merge(base.second, diff, prefixes) } catch (_: Exception) { null }
            }
            if (merged != null) return merged
            log("正在拉取完整文件清单…")
        }
        return listFromTrees(commit, prefixes)
    }

    /** 轻量读取快照基线：基线提交与条目表，格式异常返回 null；内容校验由后续逐文件 blob SHA 比对兜底 */
    private fun readSnapshotBase(snapshotFile: File): Pair<String, List<Entry>>? {
        return try {
            val snapshot = JSONObject(snapshotFile.readText())
            if (snapshot.optInt("version") != SNAPSHOT_VERSION || snapshot.optString("apiBase") != endpoint.apiBase) return null
            val base = snapshot.optString("commit")
            if (!base.matches(Regex("[0-9a-f]{40}"))) return null
            val files = snapshot.getJSONArray("files")
            if (files.length() == 0) return null
            val entries = ArrayList<Entry>(files.length())
            for (index in 0 until files.length()) {
                val item = files.getJSONObject(index)
                val path = item.getString("path")
                val sha = item.getString("sha")
                val size = item.getLong("size")
                if (!RepositoryRules.validPath(path) || !sha.matches(Regex("[0-9a-f]{40}")) || size < 0) return null
                entries.add(Entry(path, sha, size))
            }
            base to entries
        } catch (error: InterruptedException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    /** Compare 属优化路径：单次请求不重试，任何失败都回退全量清单 */
    private fun probeCompare(base: String, commit: String): JSONObject? {
        return try {
            ensureActive()
            val connection = open(api("compare/$base...$commit"))
            try {
                if (connection.responseCode != 200) return null
                JSONObject(connection.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
            } finally {
                connection.disconnect()
            }
        } catch (error: InterruptedException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    /** 全量文件清单：与 trees API 的返回结构一致，体积信息完整 */
    private fun listFromTrees(commit: String, prefixes: List<String>): List<Entry> {
        val mirrorRoot = if (mirroredApi) mirrorTreeSha(commit) else null
        val response = requestBytes(api("git/trees/$commit?recursive=1"))
        val tree = JSONObject(response.body.toString(Charsets.UTF_8))
        if (tree.optBoolean("truncated")) throw IOException("GitHub 文件清单不完整，请稍后重试")
        val entries = tree.getJSONArray("tree")
        if (mirrorRoot != null) GitTreeVerifier.verify(entries, mirrorRoot, ensureActive)
        val selected = ArrayList<Entry>()
        for (i in 0 until entries.length()) {
            ensureActive()
            val item = entries.getJSONObject(i)
            val path = item.getString("path")
            if (item.optString("type") != "blob" || item.optString("mode") == "120000" ||
                prefixes.none { RepositoryRules.contains(it, path) }) continue
            if (!RepositoryRules.validPath(path)) throw IOException("仓库包含无效路径")
            val sha = item.getString("sha")
            requireSha(sha)
            val size = item.optLong("size", -1)
            if (size < 0) throw IOException("文件清单缺少大小：$path")
            selected.add(Entry(path, sha, size))
        }
        return selected
    }

    private fun downloadRound(entries: List<Entry>, commit: String, done: AtomicLong, total: Long): List<DownloadFailure> {
        val failures = Collections.synchronizedList(mutableListOf<DownloadFailure>())
        val executor = Executors.newFixedThreadPool(6)
        val completed = ExecutorCompletionService<Unit>(executor)
        try {
            entries.forEach { entry -> completed.submit {
                ensureActive()
                try {
                    downloadWithRetry(entry, commit)
                    synchronized(indexLock) {
                        index.getJSONObject("paths").put(entry.path, entry.sha)
                        if (++dirty >= 32) writeIndex()
                    }
                    progress(done.addAndGet(entry.size), total)
                } catch (error: InterruptedException) {
                    throw error
                } catch (error: AuthorizationFailure) {
                    throw error
                } catch (error: RateLimitException) {
                    throw error
                } catch (error: Exception) {
                    ensureActive()
                    failures += DownloadFailure(entry, error)
                }
            } }
            repeat(entries.size) {
                ensureActive()
                try {
                    completed.take().get()
                } catch (error: java.util.concurrent.ExecutionException) {
                    when (val cause = error.cause) {
                        is InterruptedException -> throw cause
                        is AuthorizationFailure -> throw cause
                        is RateLimitException -> throw cause
                        else -> throw IOException("下载任务失败", cause)
                    }
                }
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(35, TimeUnit.SECONDS)
        }
        return failures.toList()
    }

    /** 单文件下载：raw 通道共 6 次尝试即最多 5 次重试（指数退避上限 16 秒），耗尽后经 API Blob 通道兜底 2 次 */
    private fun downloadWithRetry(entry: Entry, commit: String) {
        var last: Exception? = null
        for (attempt in 0 until 6) {
            ensureActive()
            try {
                download(entry, commit)
                return
            } catch (error: InterruptedException) {
                throw error
            } catch (error: AuthorizationFailure) {
                throw error
            } catch (error: RateLimitException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                last = error
                if (attempt < 5) Thread.sleep(minOf(1000L shl attempt, 16000L))
            }
        }
        // raw 端点偶发对合法文件瞬时报错，经 Blob API（raw 媒体类型）兜底，内容仍按 blob SHA 校验
        for (attempt in 0 until 2) {
            ensureActive()
            try {
                downloadViaBlob(entry)
                return
            } catch (error: InterruptedException) {
                throw error
            } catch (error: AuthorizationFailure) {
                throw error
            } catch (error: RateLimitException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                last = error
                if (attempt < 1) Thread.sleep(2000L)
            }
        }
        throw IOException("下载失败：${entry.path}", last)
    }

    private fun download(entry: Entry, commit: String) = fetchToFile(entry, endpoint.rawUrl(commit, encode(entry.path)))

    /** Blob API 兜底：走 API 地址基址（可用镜像与令牌），以 raw 媒体类型返回原始文件内容 */
    private fun downloadViaBlob(entry: Entry) = fetchToFile(entry, api("git/blobs/${entry.sha}"), "application/vnd.github.raw")

    private fun encode(path: String): String =
        path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun fetchToFile(entry: Entry, url: String, accept: String? = null) {
        val target = safeFile(entry.path)
        target.parentFile?.mkdirs()
        val temporary = File.createTempFile("download-", ".tmp", target.parentFile)
        var connection: HttpURLConnection? = null
        try {
            connection = open(url)
            if (accept != null) connection.setRequestProperty("Accept", accept)
            val code = connection.responseCode
            // 携带令牌的请求被 401 拒绝属确定性失败，直接终止
            if (code == 401 && endpoint.authorize(url) != null) throw AuthorizationFailure("GitHub 令牌无效或已过期")
            if (code == 403 && connection.getHeaderField("X-RateLimit-Remaining") == "0") throw rateLimitFailure()
            if (code != 200) throw IOException("下载失败：HTTP $code")
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            // 未知体积仅在摘要校验通过后回填，失败响应不得污染后续重试
            val size = if (entry.size < 0) temporary.length() else entry.size
            val sha = temporary.inputStream().buffered().use { GitBlob.digest(size, it, ensureActive) }
            if (sha != entry.sha) throw IOException("文件下载损坏，请重新尝试下载：${entry.path}")
            replace(temporary, target)
            entry.size = size
        } finally {
            connection?.disconnect()
            temporary.delete()
        }
    }

    private fun isCached(entry: Entry): Boolean {
        val file = safeFile(entry.path)
        val size = if (entry.size < 0 && file.isFile) file.length() else entry.size
        if (!GitBlob.matches(file, size, entry.sha, ensureActive)) return false
        entry.size = size
        // 内容校验通过但索引缺失时顺手补写索引
        synchronized(indexLock) {
            val paths = index.getJSONObject("paths")
            if (paths.optString(entry.path) != entry.sha) {
                paths.put(entry.path, entry.sha)
                dirty++
            }
        }
        return true
    }

    private fun safeFile(path: String): File {
        require(RepositoryRules.validPath(path))
        val root = files.canonicalFile
        val result = File(root, path).canonicalFile
        require(result.path.startsWith(root.path + File.separator))
        return result
    }

    private class Response(val code: Int, val body: ByteArray, val etag: String?)

    private fun requestBytes(url: String, etag: String? = null): Response {
        var last: IOException? = null
        repeat(6) { attempt ->
            ensureActive()
            val connection = open(url)
            try {
                if (etag != null) connection.setRequestProperty("If-None-Match", etag)
                val authorized = endpoint.authorize(url) != null
                val code = connection.responseCode
                // 携带令牌的请求被 401 拒绝属确定性失败，不重试
                if (code == 401 && authorized) throw AuthorizationFailure("GitHub 令牌无效或已过期")
                // 仅限额耗尽的 403 视为限额异常；镜像自身策略等其他 403 按普通失败重试
                if (code == 403 && connection.getHeaderField("X-RateLimit-Remaining") == "0") {
                    throw rateLimitFailure()
                }
                if (code == 304) return Response(code, byteArrayOf(), connection.getHeaderField("ETag"))
                if (code != 200) throw IOException("GitHub 请求失败：HTTP $code")
                val body = connection.inputStream.use { it.readBytes() }
                return Response(code, body, connection.getHeaderField("ETag"))
            } catch (error: RateLimitException) {
                throw error
            } catch (error: AuthorizationFailure) {
                throw error
            } catch (error: IOException) {
                last = error
                ensureActive()
                if (attempt < 5) Thread.sleep(minOf(1000L shl attempt, 16000L))
            } finally {
                connection.disconnect()
            }
        }
        val error = last ?: IOException("GitHub 请求失败")
        // 网络类错误统一报告为未检测到网络连接，与错误矩阵口径一致
        if (error is java.net.UnknownHostException || error is java.net.SocketTimeoutException ||
            error is java.net.ConnectException || error is javax.net.ssl.SSLException) {
            throw IOException("未检测到网络连接，取消操作。请检查网络后重试", error)
        }
        throw error
    }

    private fun rateLimitFailure(): RateLimitException = RateLimitException(
        if (endpoint.token == null) "GitHub 匿名 API 限额已用完，请稍后重试" else "GitHub API 限额已用完，请稍后重试",
    )

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        // 镜像可能以 30x 跳转回源，官方地址维持不重定向语义
        instanceFollowRedirects = endpoint.followRedirectsFor(url)
        setRequestProperty("User-Agent", "MIUI-Perfect-Icons")
        setRequestProperty("Accept", "application/vnd.github+json")
        // 令牌仅发送官方域名，镜像一律不附带
        endpoint.authorize(url)?.let { setRequestProperty("Authorization", "Bearer $it") }
    }

    /** API 请求地址：地址基址（已含仓库路径）+ 端点路径 */
    private fun api(path: String): String = endpoint.apiUrl(path)

    private fun readIndex(): JSONObject = try {
        JSONObject(indexFile.readText()).also { if (!it.has("paths")) it.put("paths", JSONObject()) }
    } catch (_: Exception) {
        JSONObject().put("paths", JSONObject())
    }

    private fun writeIndex() {
        directory.mkdirs()
        val temp = File(directory, "index.json.tmp")
        temp.writeText(index.toString())
        replace(temp, indexFile)
        dirty = 0
    }

    private fun replace(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun requireSha(sha: String) {
        if (!sha.matches(Regex("[0-9a-f]{40}"))) throw IOException("GitHub 返回了无效的 SHA")
    }

    companion object {
        private const val SNAPSHOT_VERSION = 2
        private val API_LOCK = Any()

        /** 资源缓存在其根目录下管理的文件名；清理与统计只触碰这些，不动同目录的其他持久数据 */
        private fun isManaged(name: String): Boolean =
            name == "repo" || name == "index.json" || name == "api.json" ||
                name == "index.json.tmp" || name == "api.json.tmp" || name == "archive-snapshot.json" ||
                name.startsWith("snapshot-")

        fun cacheSize(directory: File): Long =
            directory.listFiles()?.filter { isManaged(it.name) }?.sumOf { file ->
                if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
            } ?: 0L

        fun clear(directory: File): Boolean =
            directory.listFiles()?.filter { isManaged(it.name) }?.all { it.deleteRecursively() } ?: true
    }
}
