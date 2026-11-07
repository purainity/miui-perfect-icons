package me.purainity.miuiperfecticons.engine

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.ArrayDeque

/** 查找缓存中不属于当前快照的内容，避免失败的增量下载混入产物 */
object CacheContents {
    fun unexpected(root: File, prefixes: List<String>, expected: Set<String>, ensureActive: () -> Unit = {}): List<File> {
        if (Files.isSymbolicLink(root.toPath())) throw IOException("缓存根目录不能是符号链接")
        val base = root.canonicalFile
        val parentPaths = HashSet<String>()
        expected.forEach { path ->
            var separator = path.lastIndexOf('/')
            while (separator > 0) {
                parentPaths.add(path.substring(0, separator))
                separator = path.lastIndexOf('/', separator - 1)
            }
        }
        val pending = ArrayDeque<File>()
        prefixes.forEach { prefix ->
            val path = prefix.removeSuffix("/")
            require(RepositoryRules.validPath(path))
            var current = base
            for (segment in path.split('/')) {
                current = File(current, segment)
                if (Files.isSymbolicLink(current.toPath())) break
            }
            pending.add(current)
        }
        val extras = mutableListOf<File>()
        while (pending.isNotEmpty()) {
            ensureActive()
            val file = pending.removeFirst()
            val relative = file.relativeTo(base).invariantSeparatorsPath
            when {
                Files.isSymbolicLink(file.toPath()) -> extras.add(file)
                file.isDirectory -> {
                    if (relative !in parentPaths) extras.add(file)
                    else (file.listFiles() ?: throw IOException("无法读取缓存目录：$relative")).forEach(pending::add)
                }
                file.isFile -> if (relative !in expected) extras.add(file)
                file.exists() -> extras.add(file)
            }
        }
        return extras
    }

    /** 逐个删除多余文件，目录递归清空后删除自身 */
    fun removeUnexpected(files: List<File>, ensureActive: () -> Unit = {}) {
        fun remove(file: File) {
            ensureActive()
            if (!Files.isSymbolicLink(file.toPath()) && file.isDirectory) {
                (file.listFiles() ?: throw IOException("无法读取缓存目录：$file")).forEach(::remove)
            }
            if (!file.delete() && (file.exists() || Files.isSymbolicLink(file.toPath()))) {
                throw IOException("无法清理缓存文件：$file")
            }
        }
        files.forEach(::remove)
    }
}
