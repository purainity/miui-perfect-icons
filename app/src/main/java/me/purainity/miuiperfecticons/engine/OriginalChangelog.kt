package me.purainity.miuiperfecticons.engine

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** 保留原版更新日志的发布时间、完整正文、作者和可选详情链接 */
object OriginalChangelog {
    data class Entry(val date: String, val message: String, val author: String?, val url: String?)

    fun parse(source: String): List<Entry> {
        // 原版文件由多个 group 拼接而成，末尾还可能包含旧服务返回的错误节点
        if (Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            throw IOException("更新日志包含不受支持的声明")
        }
        val document = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.newDocumentBuilder().parse(InputSource(StringReader("<root>$source</root>")))
        } catch (error: Exception) {
            throw IOException("更新日志格式无效", error)
        }
        val groups = document.documentElement.getElementsByTagName("group")
        val result = ArrayList<Entry>(groups.length)
        for (index in 0 until groups.length) {
            val group = groups.item(index) as? Element ?: continue
            val text = group.getElementsByTagName("text").item(0) as? Element ?: continue
            val date = text.getElementsByTagName("title").item(0)?.textContent?.trim().orEmpty()
            val description = text.getElementsByTagName("desc").item(0)?.textContent?.trim().orEmpty()
            if (date.isEmpty() || description.isEmpty()) continue
            val lines = description.lines()
            val authorMatch = Regex("^by\\s+(.+)$").matchEntire(lines.last().trim())
            val message = if (authorMatch == null) description else lines.dropLast(1).joinToString("\n").trimEnd()
            val author = authorMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            val link = (text.getElementsByTagName("slice").item(0) as? Element)?.getAttribute("link")
            val url = try {
                link?.takeIf { value ->
                    val uri = URI(value)
                    uri.scheme.equals("https", true) && uri.host.equals("github.com", true)
                }
            } catch (_: Exception) { null }
            result.add(Entry(date, message, author, url))
        }
        if (result.isEmpty()) throw IOException("更新日志没有可显示的内容")
        return result
    }

    /** 第一行作为标题，剩余非空内容作为正文 */
    fun splitMessage(message: String): Pair<String, String?> {
        val separator = message.indexOf('\n')
        if (separator < 0) return message.trimEnd('\r') to null
        val title = message.substring(0, separator).trimEnd('\r')
        val details = message.substring(separator + 1).trim('\r', '\n').takeUnless { it.isBlank() }
        return title to details
    }

    /** 原版工作流使用上海时间，显示时换算为设备时区 */
    fun localDate(date: String, zone: ZoneId = ZoneId.systemDefault()): String = try {
        val formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.ROOT)
        LocalDateTime.parse(date, formatter).atZone(ZoneId.of("Asia/Shanghai"))
            .withZoneSameInstant(zone).format(formatter)
    } catch (_: Exception) { date }

    fun latestDate(date: String, zone: ZoneId = ZoneId.systemDefault()): String? = localDate(date, zone).take(10).takeIf {
        it.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}"))
    }?.replace('.', '-')
}

/** 原版服务返回完整文件，因此下载一次后由界面按批展示 */
class OriginalChangelogRepository(private val directory: File, private val url: String = SOURCE) {
    private val cache = File(directory, "original-changelog.xml")

    fun cached(): List<OriginalChangelog.Entry>? = try {
        OriginalChangelog.parse(cache.readText(Charsets.UTF_8))
    } catch (_: Exception) { null }

    fun refresh(): List<OriginalChangelog.Entry> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MIUI-Perfect-Icons")
        }
        val content = try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("更新日志请求失败：HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("操作已取消")
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_BYTES) throw IOException("更新日志文件过大")
                    output.write(buffer, 0, count)
                }
                output.toByteArray().toString(Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
        val entries = OriginalChangelog.parse(content)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建更新日志缓存目录")
        val temporary = File.createTempFile("changelog-", ".tmp", directory)
        try {
            temporary.writeText(content, Charsets.UTF_8)
            try {
                Files.move(temporary.toPath(), cache.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
        return entries
    }

    private companion object {
        const val SOURCE = "https://miuiicons-generic.pkg.coding.net/icons/files/changelog2.xml?version=latest"
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}
