package app.appfredo.data

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

fun interface ArtifactDownloader {
    fun download(url: String, artifact: Artifact, destination: File, progress: (Long, Long) -> Unit)
}

class GitHubClient(private val connectionFactory: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }) : ArtifactDownloader {
    private fun connect(address: String): HttpURLConnection {
        require(URL(address).protocol == "https") { "Only HTTPS downloads are supported." }
        val connection = connectionFactory(address)
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "Appfredo/0.1")
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        try {
            val status = connection.responseCode
            check(status == 200) {
                when (status) {
                    404 -> "No public release at that address. Check the repository, or ask its maker for a stable release."
                    403, 429 -> "GitHub has asked me to wait a while. Try again later."
                    else -> "GitHub wouldn't hand it over (HTTP $status)."
                }
            }
            check(connection.url.protocol == "https") { "Insecure download redirect rejected." }
            return connection
        } catch (e: Exception) { connection.disconnect(); throw e }
    }

    private fun text(address: String): String {
        val connection = connect(address)
        try {
            return connection.inputStream.use { input ->
                val bytes = input.readNBytesCompat(1_048_577)
                check(bytes.size <= 1_048_576) { "Release metadata is too large." }
                bytes.toString(Charsets.UTF_8)
            }
        } finally { connection.disconnect() }
    }

    fun latest(repo: RepoId): Release {
        val response = JSONObject(text("https://api.github.com/repos/${repo.slug}/releases/latest"))
        check(!response.getBoolean("draft") && !response.getBoolean("prerelease")) { "Only stable releases are on the menu." }
        val assets = response.getJSONArray("assets")
        val urls = mutableMapOf<String, String>()
        val sizes = mutableMapOf<String, Long>()
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.getString("name")
            val url = asset.getString("browser_download_url")
            val prefix = "https://github.com/${repo.slug}/releases/download/"
            check(url.lowercase().startsWith(prefix)) { "Release asset does not belong to this repository." }
            urls[name] = url
            sizes[name] = asset.getLong("size")
        }
        val manifestUrl = urls["appfredo.json"] ?: error("This release has no appfredo.json manifest, so I can't serve it.")
        val manifestText = text(manifestUrl)
        val manifest = Manifest.parse(manifestText)
        (listOf(manifest.apk) + manifest.patches.map { it.file }).forEach {
            check(sizes[it.asset] == it.size) { "Missing asset or size mismatch: ${it.asset}" }
        }
        val raw = JSONObject().put("manifest", JSONObject(manifestText)).put("assets", JSONObject(urls as Map<*, *>))
            .put("tag", response.getString("tag_name")).put("notes", response.optString("body")).toString()
        return decodeCached(raw)
    }

    override fun download(url: String, artifact: Artifact, destination: File, progress: (Long, Long) -> Unit) {
        destination.parentFile!!.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val connection = connect(url)
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    var lastProgress = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        check(count <= artifact.size) { "Download exceeds the declared size." }
                        output.write(buffer, 0, n)
                        hash.update(buffer, 0, n)
                        if (count - lastProgress >= 256 * 1024 || count == artifact.size) {
                            progress(count, artifact.size); lastProgress = count
                        }
                    }
                    check(count == artifact.size) { "The download stopped short. Please try again." }
                }
            }
            check(hash.digest().hex() == artifact.sha256) { "Download checksum does not match the release manifest." }
            check(temporary.renameTo(destination)) { "Could not save the verified download." }
        } finally { connection.disconnect(); temporary.delete() }
    }

    companion object {
        fun decodeCached(raw: String): Release {
            val json = JSONObject(raw)
            val assets = json.getJSONObject("assets")
            return Release(Manifest.parse(json.getJSONObject("manifest").toString()), json.getString("tag"),
                json.getString("notes"), assets.keys().asSequence().associateWith { assets.getString(it) }, raw)
        }
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (n < 0) break
        output.write(buffer, 0, n)
    }
    return output.toByteArray()
}
fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().hex()
}
