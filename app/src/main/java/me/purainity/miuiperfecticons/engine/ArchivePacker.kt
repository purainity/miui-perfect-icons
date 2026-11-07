package me.purainity.miuiperfecticons.engine

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 只打包缓存清单中已校验的文件，不包含临时文件或其他额外内容 */
class ArchivePacker(private val ensureActive: () -> Unit = {}) {
    fun pack(root: File, paths: List<String>, output: File) {
        val canonicalRoot = root.canonicalFile
        require(paths.isNotEmpty() && paths.toSet().size == paths.size) { "图标存档清单无效" }
        try {
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                paths.sorted().forEach { path ->
                    ensureActive()
                    if (!RepositoryRules.validPath(path)) throw IOException("图标存档包含无效路径")
                    val file = File(canonicalRoot, path).canonicalFile
                    if (!file.path.startsWith(canonicalRoot.path + File.separator) || !file.isFile) {
                        throw IOException("图标存档文件不存在：$path")
                    }
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().buffered().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            zip.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: Throwable) {
            output.delete()
            throw error
        }
    }
}
