package app.appfredo

import app.appfredo.data.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import org.junit.Test
import org.junit.Assert.*

class GitHubReleaseTest {
    private val manifest = """{"schemaVersion":1,"packageName":"example.app","name":"Example","versionName":"1.0","versionCode":1,
        "apk":{"asset":"app.apk","sha256":"${"a".repeat(64)}","size":42},"patches":[]}"""
    private fun client(apkSize: Long = 42, includeManifest: Boolean = true, assetHost: String = "github.com"): GitHubClient {
        val metadata = """{"draft":false,"prerelease":false,"tag_name":"v1","body":"New release","assets":[
            {"name":"app.apk","size":$apkSize,"browser_download_url":"https://$assetHost/owner/repo/releases/download/v1/app.apk"}
            ${if (includeManifest) ",{\"name\":\"appfredo.json\",\"size\":${manifest.length},\"browser_download_url\":\"https://github.com/owner/repo/releases/download/v1/appfredo.json\"}" else ""}
        ]}"""
        return GitHubClient { url -> object : HttpURLConnection(URL(url)) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = 200
            override fun getInputStream() = ByteArrayInputStream((if (url.endsWith("appfredo.json")) manifest else metadata).toByteArray())
        } }
    }
    @Test fun resolvesManifestAndRoundTripsCachedRelease() {
        val release = client().latest(RepoId("owner", "repo"))
        assertEquals("example.app", release.manifest.packageName)
        assertEquals("https://github.com/owner/repo/releases/download/v1/app.apk", release.assets["app.apk"])
        assertEquals(release, GitHubClient.decodeCached(release.raw))
    }
    @Test fun missingManifestHasActionableError() {
        val error = assertThrows(Exception::class.java) { client(includeManifest = false).latest(RepoId("owner", "repo")) }
        assertTrue(error.message!!.contains("appfredo.json"))
    }
    @Test fun mismatchedSizesAndForeignAssetsAreRejected() {
        assertThrows(Exception::class.java) { client(apkSize = 12).latest(RepoId("owner", "repo")) }
        assertThrows(Exception::class.java) { client(assetHost = "example.com").latest(RepoId("owner", "repo")) }
    }
}
