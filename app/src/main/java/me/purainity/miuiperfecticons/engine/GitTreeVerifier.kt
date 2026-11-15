package me.purainity.miuiperfecticons.engine

import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest

/** 校验递归 Trees 清单与所选数据源提供的根树 SHA 是否一致 */
object GitTreeVerifier {
    private data class Node(val name: String, val path: String, val type: String, val mode: String, val sha: String)

    fun verify(entries: JSONArray, rootSha: String, ensureActive: () -> Unit = {}) {
        fun invalid(): Nothing = throw IOException("镜像文件清单与提交根树不一致")
        fun validSha(sha: String): Boolean = sha.matches(Regex("[0-9a-f]{40}"))
        if (!validSha(rootSha)) invalid()
        val groups = HashMap<String, MutableList<Node>>()
        val paths = HashSet<String>()
        val directories = HashSet<String>()
        for (i in 0 until entries.length()) {
            ensureActive()
            val entry = entries.getJSONObject(i)
            val path = entry.getString("path")
            val parts = path.split('/')
            if (parts.size > 128 || parts.any { it.isEmpty() || it == "." || it == ".." || '\u0000' in it } || !paths.add(path)) invalid()
            val type = entry.getString("type")
            val mode = when (val value = entry.getString("mode")) {
                "040000", "40000" -> if (type == "tree") "40000" else invalid()
                "100644", "100755", "120000" -> if (type == "blob") value else invalid()
                "160000" -> if (type == "commit") value else invalid()
                else -> invalid()
            }
            val sha = entry.getString("sha")
            if (!validSha(sha)) invalid()
            if (type == "tree") directories.add(path)
            val parent = path.substringBeforeLast('/', "")
            groups.getOrPut(parent) { ArrayList() }.add(Node(parts.last(), path, type, mode, sha))
        }
        if (groups.keys.any { it.isNotEmpty() && it !in directories }) invalid()

        fun orderKey(node: Node): ByteArray = (node.name + if (node.type == "tree") "/" else "").toByteArray(Charsets.UTF_8)
        val comparator = Comparator<Node> { left, right ->
            val a = orderKey(left)
            val b = orderKey(right)
            var result = 0
            for (index in 0 until minOf(a.size, b.size)) {
                result = (a[index].toInt() and 0xff) - (b[index].toInt() and 0xff)
                if (result != 0) break
            }
            if (result != 0) result else a.size - b.size
        }
        fun hashDirectory(path: String): String {
            ensureActive()
            val content = ByteArrayOutputStream()
            for ((name, childPath, type, mode, sha) in groups[path].orEmpty().sortedWith(comparator)) {
                ensureActive()
                if (type == "tree" && hashDirectory(childPath) != sha) invalid()
                content.write("$mode $name\u0000".toByteArray(Charsets.UTF_8))
                for (index in sha.indices step 2) content.write(sha.substring(index, index + 2).toInt(16))
            }
            val bytes = content.toByteArray()
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update("tree ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
            return digest.digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
        if (hashDirectory("") != rootSha) invalid()
    }
}
