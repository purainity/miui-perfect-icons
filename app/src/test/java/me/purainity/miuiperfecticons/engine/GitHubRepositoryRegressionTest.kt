package me.purainity.miuiperfecticons.engine

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import me.purainity.miuiperfecticons.config.IconThemes
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.Date
import java.util.zip.ZipFile
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class GitHubRepositoryRegressionTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun blobSha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-1")
        .digest("blob ${bytes.size}\u0000".toByteArray() + bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class Sources : AutoCloseable {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val pool = Executors.newCachedThreadPool()
        val treeRequests = AtomicInteger()
        val mirrorTreeRequests = AtomicInteger()
        val compareRequests = AtomicInteger()
        val headRequests = AtomicInteger()
        val rawRequests = AtomicInteger()
        val atomRequests = AtomicInteger()
        val apiCommitRequests = AtomicInteger()
        val mirrorAtomRequests = AtomicInteger()
        val mirrorCommitRequests = AtomicInteger()
        val mirrorRootRequests = AtomicInteger()
        val officialRequests = AtomicInteger()
        val mirrorAuthHeaders = AtomicInteger()
        var commit = "a".repeat(40)
        var officialUnavailable = false
        var mirrorAtomUnavailable = false
        var forgedMirror = false
        var rawRateLimited = false
        var atomUnavailable = false
        var conditionalEtag: String? = null
        var compare: JSONObject? = null
        var updatedIcon: String? = null
        val base: String get() = "http://127.0.0.1:${server.address.port}"
        val trusted: GitHubEndpoint get() = GitHubEndpoint(apiBase = "$base/api", rawBase = "$base/raw", siteBase = "$base/site")
        val mirror: GitHubEndpoint get() = GitHubEndpoint(token = "secret", apiBase = "$base/mirror", rawBase = "$base/raw", siteBase = "$base/mirror-site")
        val customMirror: GitHubEndpoint get() = GitHubEndpoint(apiBase = "$base/mirror", rawBase = "$base/raw", siteBase = trusted.siteBase)

        init {
            server.executor = pool
            server.createContext("/") { exchange -> respond(exchange) }
            server.start()
        }

        private fun respond(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            if ((path.startsWith("/mirror/") || path.startsWith("/mirror-site/") || path.startsWith("/raw/")) &&
                exchange.requestHeaders.getFirst("Authorization") != null) mirrorAuthHeaders.incrementAndGet()
            if (path.startsWith("/api/") || path.startsWith("/site/")) {
                officialRequests.incrementAndGet()
                if (officialUnavailable) {
                    exchange.sendResponseHeaders(503, -1)
                    exchange.close()
                    return
                }
            }
            if (path == "/mirror-site/commits/main.atom") {
                mirrorAtomRequests.incrementAndGet()
                if (mirrorAtomUnavailable) {
                    exchange.sendResponseHeaders(503, -1)
                    exchange.close()
                    return
                }
            }
            if (path == "/site/commits/main.atom" && atomUnavailable) {
                exchange.sendResponseHeaders(503, -1)
                exchange.close()
                return
            }
            if (path == "/api/commits" && conditionalEtag != null && exchange.requestHeaders.getFirst("If-None-Match") == conditionalEtag) {
                apiCommitRequests.incrementAndGet()
                exchange.sendResponseHeaders(304, -1)
                exchange.close()
                return
            }
            if (path.startsWith("/raw/$commit/") && rawRateLimited) {
                rawRequests.incrementAndGet()
                exchange.responseHeaders.add("X-RateLimit-Remaining", "0")
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
                return
            }
            if (exchange.requestMethod == "HEAD") {
                headRequests.incrementAndGet()
                exchange.sendResponseHeaders(405, -1)
                exchange.close()
                return
            }
            val body: String? = when {
                path == "/site/commits/main.atom" -> {
                    atomRequests.incrementAndGet()
                    "<feed><entry><link href=\"https://github.com/a/b/commit/$commit\"/></entry></feed>"
                }
                path == "/mirror-site/commits/main.atom" -> "<feed><entry><link href=\"https://github.com/a/b/commit/$commit\"/></entry></feed>"
                path == "/mirror/commits" -> {
                    mirrorCommitRequests.incrementAndGet()
                    JSONArray().put(JSONObject().put("sha", commit)).toString()
                }
                path == "/mirror/git/commits/$commit" -> {
                    mirrorRootRequests.incrementAndGet()
                    JSONObject().put("sha", commit).put("tree", JSONObject().put("sha", GitTreeFixture.ROOT)).toString()
                }
                path == "/api/commits" -> {
                    apiCommitRequests.incrementAndGet()
                    JSONArray().put(JSONObject().put("sha", commit)).toString()
                }
                path == "/api/git/commits/$commit" -> JSONObject().put("sha", commit)
                    .put("tree", JSONObject().put("sha", GitTreeFixture.ROOT)).toString()
                path.startsWith("/api/git/trees/") -> {
                    treeRequests.incrementAndGet()
                    JSONObject().put("truncated", false).put("tree", GitTreeFixture.entries()).toString()
                }
                path.startsWith("/mirror/git/trees/") -> {
                    mirrorTreeRequests.incrementAndGet()
                    val entries = GitTreeFixture.entries()
                    if (forgedMirror) entries.getJSONObject(0).put("sha", "b".repeat(40))
                    JSONObject().put("truncated", false).put("tree", entries).toString()
                }
                path.startsWith("/api/compare/") || path.startsWith("/mirror/compare/") -> {
                    compareRequests.incrementAndGet()
                    compare?.toString()
                }
                path.startsWith("/raw/$commit/") -> {
                    val file = path.removePrefix("/raw/$commit/")
                    if (file == "icons/pkg/0.png" && updatedIcon != null) updatedIcon else GitTreeFixture.contents[file]
                }
                else -> null
            }
            if (body == null) {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            } else {
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }

        override fun close() {
            server.stop(0)
            pool.shutdownNow()
        }
    }

    @Test fun tokenValidationAlwaysCallsApiInsteadOfAtom() {
        Sources().use { sources ->
            GitHubRepository(temporary.newFolder(), endpoint = sources.trusted).verifyToken()
            assertEquals(1, sources.apiCommitRequests.get())
            assertEquals(0, sources.atomRequests.get())
        }
    }

    @Test fun atomResultDoesNotOverwriteApiEtagPair() {
        Sources().use { sources ->
            val directory = temporary.newFolder()
            val old = "d".repeat(40)
            sources.conditionalEtag = "\"old\""
            File(directory, "api.json").writeText(JSONObject().put("base", sources.trusted.apiBase)
                .put("latest", old).put("etag", sources.conditionalEtag).toString())
            val repo = GitHubRepository(directory, endpoint = sources.trusted, trustedEndpoint = sources.trusted)
            assertEquals(sources.commit, repo.latestCommit())
            sources.atomUnavailable = true
            assertEquals(old, repo.latestCommit())
            assertEquals(1, sources.apiCommitRequests.get())
        }
    }

    @Test fun forgedMirrorTreeIsRejectedBeforeAnyFileIsAccepted() {
        Sources().use { sources ->
            sources.forgedMirror = true
            val repo = GitHubRepository(temporary.newFolder(), endpoint = sources.mirror, trustedEndpoint = sources.trusted)
            assertThrows(IOException::class.java) { repo.synchronize("default") }
            assertEquals(1, sources.mirrorTreeRequests.get())
            assertFalse(File(repo.files, "icons/pkg/0.png").exists())
        }
    }

    @Test fun mirrorWorksWithoutAnyOfficialRequests() {
        Sources().use { sources ->
            sources.officialUnavailable = true
            val repo = GitHubRepository(temporary.newFolder(), endpoint = sources.mirror, trustedEndpoint = sources.trusted)
            assertEquals(sources.commit, repo.latestCommit())
            assertEquals(sources.commit, repo.synchronize("default"))
            assertEquals("icon-data", File(repo.files, "icons/pkg/0.png").readText())
            assertTrue(repo.hasCompleteSnapshot("default"))
            sources.commit = "c".repeat(40)
            assertEquals(sources.commit, repo.synchronize("default"))
            assertEquals(2, sources.mirrorTreeRequests.get())
            assertEquals(2, sources.mirrorRootRequests.get())
            assertEquals(3, sources.mirrorAtomRequests.get())
            assertEquals(0, sources.officialRequests.get())
            assertEquals(0, sources.mirrorAuthHeaders.get())
            assertEquals(0, sources.compareRequests.get())
        }
    }

    @Test fun mirrorCanSupplyBothPackageFormatsAndArchiveWithoutGithub() {
        Sources().use { sources ->
            sources.officialUnavailable = true
            val directory = temporary.newFolder()
            val repo = GitHubRepository(directory, endpoint = sources.mirror, trustedEndpoint = sources.trusted)
            repo.synchronize("default")
            val assembler = PackageAssembler(moduleTemplate = { name ->
                File("src/main/assets", name).readBytes()
            })
            for (format in PackageFormat.entries) {
                val output = File(directory, "${format.name}.zip")
                val options = AssemblyOptions(
                    theme = IconThemes.default,
                    useNewMask = true,
                    disableAll = false,
                    disabledPackages = emptySet(),
                    customIcons = null,
                    format = format,
                    timestamp = Date(0),
                )
                assembler.assemble(repo.files, File(directory, "work-${format.name}"), output, options)
                ZipFile(output).use { zip ->
                    assertTrue(zip.getEntry("icons") != null)
                    assertTrue(zip.getEntry(if (format == PackageFormat.MTZ) "description.xml" else "module.prop") != null)
                }
            }
            repo.synchronize("default", archiveOnly = true)
            val entries = requireNotNull(repo.cachedArchiveEntries())
            val archive = File(directory, "archive.zip")
            ArchivePacker().pack(File(repo.files, RepositoryRules.ARCHIVE_PREFIX),
                entries.map { it.path.removePrefix(RepositoryRules.ARCHIVE_PREFIX) }, archive)
            ZipFile(archive).use { zip ->
                assertEquals("archive-data", zip.getInputStream(zip.getEntry("old.png")).reader().readText())
            }
            assertEquals(0, sources.officialRequests.get())
            assertEquals(0, sources.mirrorAuthHeaders.get())
        }
    }

    @Test fun customMirrorSkipsOfficialAtomAndUsesMirrorApi() {
        Sources().use { sources ->
            sources.officialUnavailable = true
            val repo = GitHubRepository(temporary.newFolder(), endpoint = sources.customMirror, trustedEndpoint = sources.trusted)
            assertEquals(sources.commit, repo.synchronize("default"))
            assertEquals(1, sources.mirrorCommitRequests.get())
            assertEquals(0, sources.mirrorAtomRequests.get())
            assertEquals(0, sources.officialRequests.get())
        }
    }

    @Test fun unavailableMirrorAtomFallsBackToMirrorApi() {
        Sources().use { sources ->
            sources.officialUnavailable = true
            sources.mirrorAtomUnavailable = true
            val repo = GitHubRepository(temporary.newFolder(), endpoint = sources.mirror, trustedEndpoint = sources.trusted)
            assertEquals(sources.commit, repo.synchronize("default"))
            assertEquals(1, sources.mirrorCommitRequests.get())
            assertEquals(0, sources.officialRequests.get())
        }
    }

    @Test fun switchingApiSourcesRebuildsSnapshotEvenAtSameCommit() {
        Sources().use { sources ->
            val directory = temporary.newFolder()
            val mirror = GitHubRepository(directory, endpoint = sources.mirror, trustedEndpoint = sources.trusted)
            assertEquals(sources.commit, mirror.synchronize("default"))
            val official = GitHubRepository(directory, endpoint = sources.trusted, trustedEndpoint = sources.trusted)
            assertEquals(sources.commit, official.synchronize("default"))
            assertEquals(1, sources.treeRequests.get())
            assertEquals(sources.trusted.apiBase, JSONObject(File(directory, "snapshot-default.json").readText()).getString("apiBase"))
        }
    }

    @Test fun snapshotWithoutSourceIsRebuilt() {
        Sources().use { sources ->
            val directory = temporary.newFolder()
            val repo = GitHubRepository(directory, endpoint = sources.trusted, trustedEndpoint = sources.trusted)
            repo.synchronize("default")
            val snapshot = File(directory, "snapshot-default.json")
            snapshot.writeText(JSONObject(snapshot.readText()).apply { remove("apiBase") }.toString())
            repo.synchronize("default")
            assertEquals(2, sources.treeRequests.get())
        }
    }

    @Test fun oldSnapshotIsRebuiltEvenWhenCommitIsUnchanged() {
        Sources().use { sources ->
            val directory = temporary.newFolder()
            val repo = GitHubRepository(directory, endpoint = sources.trusted, trustedEndpoint = sources.trusted)
            repo.synchronize("default")
            val snapshot = File(directory, "snapshot-default.json")
            snapshot.writeText(JSONObject(snapshot.readText()).apply { remove("version") }.toString())
            assertFalse(repo.hasCompleteSnapshot("default"))
            repo.synchronize("default")
            assertEquals(2, sources.treeRequests.get())
            assertEquals(2, JSONObject(snapshot.readText()).getInt("version"))
            assertTrue(repo.hasCompleteSnapshot("default"))
        }
    }

    @Test fun compareUpdatesWithoutSerialHeadRequests() {
        Sources().use { sources ->
            val repo = GitHubRepository(temporary.newFolder(), endpoint = sources.trusted, trustedEndpoint = sources.trusted)
            repo.synchronize("default")
            sources.commit = "c".repeat(40)
            sources.updatedIcon = "new-icon-data"
            val change = JSONObject().put("filename", "icons/pkg/0.png").put("status", "modified")
                .put("sha", blobSha(sources.updatedIcon!!.toByteArray()))
            sources.compare = JSONObject().put("status", "ahead").put("ahead_by", 1).put("files", JSONArray().put(change))
            repo.synchronize("default")
            assertEquals("new-icon-data", File(repo.files, "icons/pkg/0.png").readText())
            assertEquals(1, sources.compareRequests.get())
            assertEquals(0, sources.headRequests.get())
            assertTrue(repo.hasCompleteSnapshot("default"))
        }
    }

    @Test fun downloadRateLimitStopsRetriesAndRemainsRecognizable() {
        Sources().use { sources ->
            sources.rawRateLimited = true
            val repo = GitHubRepository(temporary.newFolder(), endpoint = sources.trusted, trustedEndpoint = sources.trusted)
            assertThrows(RateLimitException::class.java) { repo.synchronize("default") }
            assertTrue(sources.rawRequests.get() in 1..GitTreeFixture.contents.size)
        }
    }

    @Test fun invalidUnknownSizeResponseDoesNotPoisonNextAttempt() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        val valid = "valid icon content".toByteArray()
        server.createContext("/") { exchange ->
            val body = if (requests.getAndIncrement() == 0) "bad".toByteArray() else valid
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val repo = GitHubRepository(temporary.newFolder())
            val entry = GitHubRepository.Entry("icons/a.png", blobSha(valid), -1)
            val method = repo.javaClass.getDeclaredMethod("fetchToFile", GitHubRepository.Entry::class.java, String::class.java, String::class.java)
                .apply { isAccessible = true }
            val url = "http://127.0.0.1:${server.address.port}/file"
            assertThrows(InvocationTargetException::class.java) { method.invoke(repo, entry, url, null) }
            assertEquals(-1, entry.size)
            method.invoke(repo, entry, url, null)
            assertEquals(valid.size.toLong(), entry.size)
            assertEquals("valid icon content", File(repo.files, entry.path).readText())
        } finally {
            server.stop(0)
        }
    }
}
