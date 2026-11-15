package me.purainity.miuiperfecticons.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubEndpointTest {
    @Test fun normalizesUserInput() {
        assertEquals("https://example.com", GitHubEndpoint.normalize(" https://example.com/ "))
        assertEquals("https://example.com/a/b", GitHubEndpoint.normalize("https://example.com/a/b/"))
        assertEquals("http://example.com", GitHubEndpoint.normalize("http://example.com"))
        assertNull(GitHubEndpoint.normalize("ftp://example.com"))
        assertNull(GitHubEndpoint.normalize("example.com"))
        assertNull(GitHubEndpoint.normalize("https://a b.com"))
        assertNull(GitHubEndpoint.normalize(""))
    }

    @Test fun buildsOfficialUrlsByDefault() {
        val endpoint = GitHubEndpoint()
        assertEquals(
            "https://api.github.com/repos/pzcn/Perfect-Icons-Completion-Project/commits?per_page=1",
            endpoint.apiUrl("commits?per_page=1"),
        )
        assertEquals(
            "https://github.com/pzcn/Perfect-Icons-Completion-Project/commits/main.atom",
            endpoint.atomUrl(),
        )
        assertEquals(
            "https://raw.githubusercontent.com/pzcn/Perfect-Icons-Completion-Project/abc123/icons/x/0.png",
            endpoint.rawUrl("abc123", "icons/x/0.png"),
        )
    }

    @Test fun buildsMirrorUrls() {
        val site = "https://github.com/pzcn/Perfect-Icons-Completion-Project"
        val endpoint = GitHubEndpoint(
            null,
            "https://mirror.example/https://api.github.com/repos/pzcn/Perfect-Icons-Completion-Project",
            "https://mirror.example/https://raw.githubusercontent.com/pzcn/Perfect-Icons-Completion-Project",
            "https://mirror.example/$site",
        )
        assertEquals(
            "https://mirror.example/https://api.github.com/repos/pzcn/Perfect-Icons-Completion-Project/git/trees/abc?recursive=1",
            endpoint.apiUrl("git/trees/abc?recursive=1"),
        )
        assertEquals(
            "https://mirror.example/$site/commits/main.atom",
            endpoint.atomUrl(),
        )
        assertEquals(
            "https://mirror.example/https://raw.githubusercontent.com/pzcn/Perfect-Icons-Completion-Project/abc123/icons/x/0.png",
            endpoint.rawUrl("abc123", "icons/x/0.png"),
        )
    }

    @Test fun buildsJsDelivrUrls() {
        val endpoint = GitHubEndpoint(rawBase = "https://cdn.jsdelivr.net/gh/pzcn/Perfect-Icons-Completion-Project@")
        assertEquals(
            "https://cdn.jsdelivr.net/gh/pzcn/Perfect-Icons-Completion-Project@abc123/icons/x/0.png",
            endpoint.rawUrl("abc123", "icons/x/0.png"),
        )
    }

    @Test fun authorizesOnlyOfficialHosts() {
        val endpoint = GitHubEndpoint("ghp_test")
        assertEquals("ghp_test", endpoint.authorize("https://api.github.com/repos/pzcn/x"))
        assertEquals("ghp_test", endpoint.authorize("https://raw.githubusercontent.com/pzcn/Perfect-Icons-Completion-Project/main/a.png"))
        assertNull(endpoint.authorize("https://mirror.example/https://api.github.com/repos/pzcn/x"))
        assertNull(endpoint.authorize("https://api.github.com.evil.example/repos/pzcn/x"))
        assertNull(endpoint.authorize("https://raw.githubusercontent.example.com/x"))
        assertNull(GitHubEndpoint().authorize("https://api.github.com/repos/pzcn/x"))
    }

    @Test fun followsRedirectsOnlyForMirrors() {
        assertFalse(GitHubEndpoint().followRedirects)
        assertFalse(GitHubEndpoint("ghp_test").followRedirects)
        assertTrue(GitHubEndpoint(rawBase = "https://cdn.jsdelivr.net/gh/pzcn/x@").followRedirects)
        assertTrue(GitHubEndpoint(apiBase = "https://mirror.example/https://api.github.com").followRedirects)
        val mixed = GitHubEndpoint(rawBase = "https://mirror.example/raw")
        assertTrue(mixed.followRedirectsFor("https://mirror.example/raw/a"))
        assertFalse(mixed.followRedirectsFor("https://api.github.com/repos/pzcn/a"))
        assertFalse(mixed.followRedirectsFor("https://raw.githubusercontent.com/pzcn/a"))
        assertFalse(mixed.followRedirectsFor("https://github.com/pzcn/a"))
    }
}
