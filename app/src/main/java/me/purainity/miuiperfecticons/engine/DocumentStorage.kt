package me.purainity.miuiperfecticons.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/** 自定义内容的读取与结果的写入均通过系统授权的文档树完成 */
class DocumentStorage(private val context: Context, private val ensureActive: () -> Unit = {}) {
    private val resolver = context.contentResolver
    data class Child(val id: String, val name: String, val mime: String, val uri: Uri) {
        val directory get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }
    data class SavedFile(val uri: Uri, val label: String, val mime: String)

    fun hasWriteAccess(uri: Uri): Boolean = resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    /** 读取目录的直属子项 */
    fun children(tree: Uri, parent: Uri = root(tree)): List<Child> {
        val id = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        return resolver.query(children, projection, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    ensureActive()
                    val childId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2) ?: continue
                    add(Child(childId, name, mime, DocumentsContract.buildDocumentUriUsingTree(tree, childId)))
                }
            }
        } ?: throw IOException("无法读取授权目录")
    }

    fun root(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    /** 确保自定义目录下三个分类子目录与 .nomedia 存在 */
    fun ensureCustomFolders(tree: Uri) {
        if (!hasWriteAccess(tree)) throw IOException("自定义图标目录授权已失效，请重新选择")
        val root = root(tree)
        val existing = children(tree, root)
        listOf("静态图标", "动态图标", "高级附加").forEach { name ->
            val found = existing.firstOrNull { it.name == name }
            if (found == null) {
                DocumentsContract.createDocument(resolver, root, DocumentsContract.Document.MIME_TYPE_DIR, name)
                    ?: throw IOException("无法创建目录：$name")
            } else if (!found.directory) throw IOException("自定义图标目录中存在同名文件：$name")
        }
        if (existing.none { it.name == ".nomedia" }) {
            DocumentsContract.createDocument(resolver, root, "application/octet-stream", ".nomedia")
        }
    }

    /** 把授权目录中的三类自定义内容复制到私有临时目录 */
    fun snapshotCustom(tree: Uri, destination: File) {
        if (resolver.persistedUriPermissions.none { it.uri == tree && it.isReadPermission }) {
            throw IOException("自定义图标目录授权已失效，请重新选择")
        }
        destination.mkdirs()
        children(tree).filter { it.directory && it.name in setOf("静态图标", "动态图标", "高级附加") }.forEach {
            copyDirectory(tree, it.uri, File(destination, it.name))
        }
    }

    // 像原版 cp -rf 一样对目录整体递归复制，不设层级上限
    private fun copyDirectory(tree: Uri, source: Uri, target: File) {
        if (!target.isDirectory && !target.mkdirs()) throw IOException("无法创建临时目录")
        children(tree, source).forEach { child ->
            ensureActive()
            if (!validName(child.name)) throw IOException("自定义图标包含无效文件名")
            val file = File(target, child.name)
            if (child.directory) copyDirectory(tree, child.uri, file)
            else resolver.openInputStream(child.uri)?.use { input ->
                file.outputStream().buffered().use { output -> copy(input, output) }
            } ?: throw IOException("无法读取自定义图标：${child.name}")
        }
    }

    /** 将 SAF 选中的文件或目录导入指定分类 */
    fun importDocuments(destinationTree: Uri, category: String, sources: List<Uri>, sourceIsTree: Boolean) {
        require(category in setOf("静态图标", "动态图标", "高级附加"))
        ensureCustomFolders(destinationTree)
        val target = children(destinationTree).firstOrNull { it.name == category && it.directory }
            ?: throw IOException("自定义图标目录不存在")
        sources.forEach { source ->
            ensureActive()
            if (sourceIsTree) {
                val sourceRoot = root(source)
                val sourceId = DocumentsContract.getDocumentId(sourceRoot)
                if (source.authority == destinationTree.authority &&
                    (target.id == sourceId || target.id.startsWith("$sourceId/") || containsDirectory(source, sourceRoot, target.uri))) {
                    throw IOException("不能将自定义目录导入自身")
                }
                val name = queryName(sourceRoot)
                val existing = children(destinationTree, target.uri).firstOrNull { it.name == name && it.directory }
                if (existing != null && sameDocument(sourceRoot, existing.uri)) throw IOException("不能将目录导入自身")
                val dest = directoryFor(destinationTree, target.uri, name)
                importDirectory(source, sourceRoot, destinationTree, dest)
            } else {
                val name = queryName(source)
                val mime = resolver.getType(source) ?: "application/octet-stream"
                importFile(source, destinationTree, target.uri, name, mime)
            }
        }
    }

    private fun containsDirectory(tree: Uri, source: Uri, target: Uri): Boolean {
        val queue = java.util.ArrayDeque<Uri>()
        val visited = mutableSetOf<String>()
        queue.add(source)
        while (queue.isNotEmpty()) {
            ensureActive()
            val current = queue.removeFirst()
            val id = DocumentsContract.getDocumentId(current)
            if (!visited.add(id)) continue
            if (visited.size > 10000) throw IOException("目录过大，无法检查导入来源")
            if (sameDocument(current, target)) return true
            children(tree, current).filter { it.directory }.forEach { queue.add(it.uri) }
        }
        return false
    }

    // 导入同样不设层级上限，与整体递归复制的语义一致
    private fun importDirectory(sourceTree: Uri, source: Uri, destinationTree: Uri, destination: Uri) {
        if (sameDocument(source, destination)) throw IOException("不能将目录导入自身")
        children(sourceTree, source).forEach { child ->
            ensureActive()
            if (!validName(child.name)) throw IOException("导入内容包含无效文件名")
            if (child.directory) {
                val sub = directoryFor(destinationTree, destination, child.name)
                importDirectory(sourceTree, child.uri, destinationTree, sub)
            } else importFile(child.uri, destinationTree, destination, child.name, child.mime)
        }
    }

    private fun directoryFor(tree: Uri, parent: Uri, name: String): Uri {
        if (!validName(name)) throw IOException("导入内容包含无效文件名")
        val existing = children(tree, parent).firstOrNull { it.name == name }
        if (existing != null) {
            if (!existing.directory) throw IOException("已有同名文件：$name")
            return existing.uri
        }
        return DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw IOException("无法创建目录：$name")
    }

    private fun importFile(source: Uri, tree: Uri, parent: Uri, name: String, mime: String) {
        if (!validName(name)) throw IOException("导入内容包含无效文件名")
        val existing = children(tree, parent).firstOrNull { it.name == name }
        if (existing?.directory == true) throw IOException("已有同名目录：$name")
        if (existing != null && sameDocument(existing.uri, source)) throw IOException("不能将文件导入自身")
        val backup = existing?.let {
            File.createTempFile("icon-backup-", ".tmp", context.cacheDir).also { temporary ->
                try {
                    resolver.openInputStream(it.uri)?.use { input ->
                        temporary.outputStream().buffered().use { output -> copy(input, output) }
                    } ?: throw IOException("无法备份已有文件：$name")
                } catch (error: Throwable) {
                    temporary.delete()
                    throw error
                }
            }
        }
        var destination: Uri? = null
        var overwriteStarted = false
        try {
            val target = existing?.uri ?: DocumentsContract.createDocument(resolver, parent, mime, name)
                ?: throw IOException("无法创建文件：$name")
            destination = target
            resolver.openInputStream(source)?.use { input ->
                overwriteStarted = true
                resolver.openOutputStream(target, "wt")?.use { output -> copy(input, output) }
                    ?: throw IOException("无法写入文件：$name")
            } ?: throw IOException("无法读取文件：$name")
        } catch (error: Throwable) {
            val target = destination
            if (target != null && existing == null) {
                try { DocumentsContract.deleteDocument(resolver, target) } catch (_: Exception) { }
            } else if (target != null && backup != null && overwriteStarted) {
                // 取消或写入失败时尽力恢复被覆盖的原文件
                try {
                    resolver.openOutputStream(target, "wt")?.use { output ->
                        backup.inputStream().use { it.copyTo(output) }
                    } ?: throw IOException("无法重新打开原文件")
                } catch (restoreError: Exception) {
                    throw IOException("导入失败，且无法恢复原文件：$name", restoreError).also { it.addSuppressed(error) }
                }
            }
            throw error
        } finally {
            backup?.delete()
        }
    }

    private fun sameDocument(first: Uri, second: Uri): Boolean {
        if (first == second) return true
        if (first.authority != second.authority) return false
        return try { DocumentsContract.getDocumentId(first) == DocumentsContract.getDocumentId(second) }
        catch (_: IllegalArgumentException) { false }
    }

    private fun queryName(uri: Uri): String = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }?.takeIf(::validName) ?: throw IOException("无法读取导入文件名")

    /** 无 SAF 目录时经 MediaStore 写入公共 Download，否则写入授权目录 */
    fun save(output: File, name: String, mime: String, tree: Uri?): SavedFile {
        require(validName(name))
        val values = ContentValues()
        val uri: Uri
        val directoryLabel: String
        if (tree == null) {
            values.put(MediaStore.Downloads.DISPLAY_NAME, name)
            values.put(MediaStore.Downloads.MIME_TYPE, mime)
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            values.put(MediaStore.Downloads.IS_PENDING, 1)
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("无法创建下载文件")
            directoryLabel = "Download"
        } else {
            if (!hasWriteAccess(tree)) throw IOException("× 目录授权已失效，请重新选择")
            uri = DocumentsContract.createDocument(resolver, root(tree), mime, name) ?: throw IOException("无法在选定目录创建文件")
            directoryLabel = DocumentsContract.getTreeDocumentId(tree)
        }
        try {
            output.inputStream().buffered().use { input ->
                resolver.openOutputStream(uri, "w")?.use { stream -> copy(input, stream) }
                    ?: throw IOException("无法写入输出文件")
            }
            ensureActive()
            if (tree == null) {
                val complete = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                if (resolver.update(uri, complete, null, null) != 1) throw IOException("无法完成输出文件")
            }
            val savedName = try { queryName(uri) } catch (_: Exception) { name }
            return SavedFile(uri, "$directoryLabel/$savedName", mime)
        } catch (error: Throwable) {
            try {
                if (tree == null) resolver.delete(uri, null, null) else DocumentsContract.deleteDocument(resolver, uri)
            } catch (_: Exception) { }
            throw error
        }
    }

    private fun copy(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    private fun validName(name: String): Boolean = name.isNotEmpty() && name != "." && name != ".." &&
        '/' !in name && '\\' !in name && '\u0000' !in name
}
