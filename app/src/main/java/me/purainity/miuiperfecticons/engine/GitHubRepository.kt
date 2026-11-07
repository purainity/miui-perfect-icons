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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class RateLimitException : IOException("GitHub 匿名 API 限额已用完，请稍后重试")

/** 缓存按仓库路径存放，索引中的 blob SHA 只在文件校验成功后更新 */
class GitHubRepository(
    val directory: File,
    private val ensureActive: () -> Unit = {},
    private val log: (String) -> Unit = {},
    private val progress: (Long, Long) -> Unit = { _, _ -> },
) {
    data class Entry(val path: String, val sha: String, val size: Long)

    private val indexFile = File(directory, "index.json")
    private val indexLock = Any()
    private var index = readIndex()
    private var dirty = 0
    val files: File get() = File(directory, "repo")

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
            val items = JSONObject(snapshotFile.readText()).getJSONArray("files")
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

    /** 版本检查：commits 接口取最新 SHA，ETag 命中 304 时复用上次结果以节省限额 */
    fun latestCommit(): String = synchronized(API_LOCK) {
        val metadata = File(directory, "api.json")
        val old = try { JSONObject(metadata.readText()) } catch (_: Exception) { JSONObject() }
        val etag = old.optString("etag").takeIf(String::isNotEmpty)
        val cachedLatest = old.optString("latest").takeIf(String::isNotEmpty)
        var response = requestBytes("$API/commits?per_page=1", if (cachedLatest == null) null else etag)
        if (response.code == 304 && cachedLatest != null) return@synchronized cachedLatest
        if (response.code == 304) response = requestBytes("$API/commits?per_page=1")
        val array = JSONArray(response.body.toString(Charsets.UTF_8))
        val sha = array.getJSONObject(0).getString("sha")
        requireSha(sha)
        directory.mkdirs()
        val temp = File(directory, "api.json.tmp")
        temp.writeText(JSONObject().put("latest", sha).put("etag", response.etag).toString())
        replace(temp, metadata)
        sha
    }

    fun synchronize(themeId: String, archiveOnly: Boolean = false): String {
        val commit = latestCommit()
        val prefixes = RepositoryRules.prefixes(themeId, archiveOnly)
        val response = requestBytes("$API/git/trees/$commit?recursive=1")
        val tree = JSONObject(response.body.toString(Charsets.UTF_8))
        if (tree.optBoolean("truncated")) throw IOException("GitHub 文件清单不完整，请稍后重试")
        val entries = tree.getJSONArray("tree")
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
        if (selected.isEmpty()) throw IOException("仓库文件清单为空")
        if (!archiveOnly && !RepositoryRules.hasRequiredPackageFiles(selected.mapTo(HashSet()) { it.path }, themeId)) {
            throw IOException("仓库中缺少主图标库、所选主题或 MTZ 外壳")
        }
        // 逐文件校验 blob SHA，内容未变的文件直接复用
        val pending = selected.filterNot { isCached(it) }
        val total = pending.sumOf { it.size }
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
            val snapshot = JSONObject().put("commit", commit).put("files", JSONArray().apply {
                selected.forEach { put(JSONObject().put("path", it.path).put("sha", it.sha).put("size", it.size)) }
            })
            val snapshotName = if (archiveOnly) "archive-snapshot.json" else "snapshot-$themeId.json"
            val temporary = File(directory, "$snapshotName.tmp")
            temporary.writeText(snapshot.toString())
            replace(temporary, File(directory, snapshotName))
        }
        return commit
    }

    private data class DownloadFailure(val entry: Entry, val cause: Exception)

    private fun downloadRound(entries: List<Entry>, commit: String, done: AtomicLong, total: Long): List<DownloadFailure> {
        val failures = Collections.synchronizedList(mutableListOf<DownloadFailure>())
        val executor = Executors.newFixedThreadPool(6)
        try {
            val tasks = entries.map { entry -> executor.submit(Callable {
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
                } catch (error: Exception) {
                    ensureActive()
                    failures.add(DownloadFailure(entry, error))
                }
            }) }
            tasks.forEach { future ->
                ensureActive()
                try {
                    future.get()
                } catch (error: java.util.concurrent.ExecutionException) {
                    val cause = error.cause
                    if (cause is InterruptedException) throw cause
                    throw IOException("下载任务失败", cause)
                }
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(35, TimeUnit.SECONDS)
        }
        return failures.toList()
    }

    /** 单文件下载：共 6 次尝试即最多 5 次重试，指数退避上限 16 秒 */
    private fun downloadWithRetry(entry: Entry, commit: String) {
        var last: Exception? = null
        for (attempt in 0 until 6) {
            ensureActive()
            try {
                download(entry, commit)
                return
            } catch (error: InterruptedException) {
                throw error
            } catch (error: Exception) {
                ensureActive()
                last = error
                if (attempt < 5) Thread.sleep(minOf(1000L shl attempt, 16000L))
            }
        }
        throw IOException("下载失败：${entry.path}", last)
    }

    private fun download(entry: Entry, commit: String) {
        val target = safeFile(entry.path)
        target.parentFile?.mkdirs()
        val temporary = File.createTempFile("download-", ".tmp", target.parentFile)
        val encoded = entry.path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        var connection: HttpURLConnection? = null
        try {
            connection = open("$RAW/$commit/$encoded")
            if (connection.responseCode != 200) throw IOException("下载失败：HTTP ${connection.responseCode}")
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
            val sha = temporary.inputStream().buffered().use { GitBlob.digest(entry.size, it, ensureActive) }
            if (sha != entry.sha) throw IOException("文件下载损坏，请重新尝试下载：${entry.path}")
            replace(temporary, target)
        } finally {
            connection?.disconnect()
            temporary.delete()
        }
    }

    private fun isCached(entry: Entry): Boolean {
        val file = safeFile(entry.path)
        if (!GitBlob.matches(file, entry.size, entry.sha, ensureActive)) return false
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
                val code = connection.responseCode
                if (code == 403) throw RateLimitException()
                if (code == 304) return Response(code, byteArrayOf(), connection.getHeaderField("ETag"))
                if (code != 200) throw IOException("GitHub 请求失败：HTTP $code")
                val body = connection.inputStream.use { it.readBytes() }
                return Response(code, body, connection.getHeaderField("ETag"))
            } catch (error: RateLimitException) {
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

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15000
        readTimeout = 30000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", "MIUI-Perfect-Icons")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

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
        private val API_LOCK = Any()
        private const val API = "https://api.github.com/repos/pzcn/Perfect-Icons-Completion-Project"
        private const val RAW = "https://raw.githubusercontent.com/pzcn/Perfect-Icons-Completion-Project"

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
