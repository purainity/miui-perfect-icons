package me.purainity.miuiperfecticons.engine

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Git 对象的摘要包含类型与长度，不能直接对文件内容计算 SHA-1 */
object GitBlob {
    /** 校验文件内容本身，而非仅依据索引和文件大小 */
    fun matches(file: File, size: Long, sha: String, ensureActive: () -> Unit = {}): Boolean {
        return try {
            ensureActive()
            if (!file.isFile || file.length() != size) return false
            file.inputStream().buffered().use { digest(size, it, ensureActive) == sha }
        } catch (error: InterruptedException) {
            throw error
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun digest(size: Long, input: InputStream, ensureActive: () -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob $size\u0000".toByteArray(Charsets.UTF_8))
        var actual = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
            actual += count
        }
        require(actual == size) { "文件大小与清单不一致" }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/** 仓库路径的获取范围、合法性与必需文件规则 */
object RepositoryRules {
    const val ARCHIVE_PREFIX = "addition/Icons_Archiving/"

    fun validPath(path: String): Boolean = path.isNotEmpty() && !path.startsWith('/') &&
        !path.contains('\\') && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." && '\u0000' !in it }

    /** 按主题计算获取前缀；lrone 与 luck7 不并入共用动态图标 */
    fun prefixes(themeId: String, archiveOnly: Boolean = false): List<String> {
        require(themeId.matches(Regex("[A-Za-z0-9_-]+")))
        if (archiveOnly) return listOf(ARCHIVE_PREFIX)
        return buildList {
            add("icons/")
            add("themes/$themeId/")
            if (themeId !in setOf("lrone", "luck7")) add("themes/common/")
            add("addition/mtz/")
            add("addition/transform_config.xml")
            add("addition/transform_config2.xml")
        }
    }

    /** 判断文件清单是否覆盖装配所需的核心文件 */
    fun hasRequiredPackageFiles(paths: Set<String>, themeId: String): Boolean =
        paths.any { it.startsWith("icons/") } && paths.any { it.startsWith("themes/$themeId/") } &&
            "addition/mtz/description.xml" in paths && "addition/mtz/com.miui.home" in paths

    /** 以 / 结尾的前缀按目录前缀匹配，否则要求路径全等 */
    fun contains(prefix: String, path: String): Boolean =
        if (prefix.endsWith('/')) path.startsWith(prefix) else path == prefix
}
