package me.purainity.miuiperfecticons.engine

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference

class OriginalChangelogRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun followsRedirectAndKeepsLastValidCacheOnFailure() {
        val source = AtomicReference("<group><text><title>2026.10.24 14:08</title><desc>第一行\n第二行\nby 作者</desc></text></group>")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/changelog")
            exchange.sendResponseHeaders(307, -1)
            exchange.close()
        }
        server.createContext("/changelog") { exchange ->
            val bytes = source.get().toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val repository = OriginalChangelogRepository(temporary.root, "http://127.0.0.1:${server.address.port}/redirect")
            val first = repository.refresh()
            assertEquals("第一行\n第二行", first.single().message)
            assertEquals(first, repository.cached())
            source.set("<Error><Code>AccessDenied</Code></Error>")
            try {
                repository.refresh()
                fail("无效日志不能覆盖有效缓存")
            } catch (_: IOException) {
                assertEquals(first, repository.cached())
            }
        } finally {
            server.stop(0)
        }
        assertNotNull(OriginalChangelogRepository(temporary.root).cached())
    }
}
