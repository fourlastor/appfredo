package app.appfredo

import app.appfredo.data.*
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DownloadTest {
    private fun client(bytes: ByteArray, status: Int = 200): GitHubClient = GitHubClient { address ->
        object : HttpURLConnection(URL(address)) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = status
            override fun getInputStream() = ByteArrayInputStream(bytes)
        }
    }
    @Test fun onlyExactVerifiedDownloadsArePublished() {
        val dir = Files.createTempDirectory("appfredo-download").toFile()
        try {
            val bytes = "signed APK bytes".toByteArray()
            val source = dir.resolve("source").apply { writeBytes(bytes) }
            val expected = Artifact("app.apk", sha256(source), bytes.size.toLong())
            val output = dir.resolve("download.apk")
            for ((data, artifact) in listOf(
                bytes.dropLast(1).toByteArray() to expected,
                (bytes + 42.toByte()) to expected,
                bytes to expected.copy(sha256 = "0".repeat(64)),
            )) {
                assertThrows(Exception::class.java) { client(data).download("https://github.com/file", artifact, output) { _, _ -> } }
                assertFalse(output.exists())
                assertFalse(dir.resolve("download.apk.part").exists())
            }
            client(bytes).download("https://github.com/file", expected, output) { _, _ -> }
            assertArrayEquals(bytes, output.readBytes())
        } finally { dir.deleteRecursively() }
    }
    @Test fun httpErrorsAndCleartextFailClosed() {
        val dir = Files.createTempDirectory("appfredo-download").toFile()
        try {
            val artifact = Artifact("app.apk", "a".repeat(64), 1)
            for (status in listOf(403, 404, 429, 500)) {
                assertThrows(Exception::class.java) { client(byteArrayOf(), status).download("https://github.com/file", artifact, dir.resolve("app.apk")) { _, _ -> } }
            }
            assertThrows(Exception::class.java) { client(byteArrayOf()).download("http://github.com/file", artifact, dir.resolve("app.apk")) { _, _ -> } }
        } finally { dir.deleteRecursively() }
    }
}
