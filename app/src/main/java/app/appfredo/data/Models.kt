package app.appfredo.data

import org.json.JSONObject
import java.net.URI

data class RepoId(val owner: String, val name: String) {
    val slug get() = "$owner/$name"
    companion object {
        fun parse(input: String): RepoId {
            val text = input.trim().trimEnd('/')
            val slug = if (text.startsWith("https://")) {
                val uri = URI(text)
                require(uri.host == "github.com" && uri.port == -1 && uri.userInfo == null && uri.query == null && uri.fragment == null) {
                    "I only call at github.com. Try a repository link, or owner/repository."
                }
                uri.path.removePrefix("/")
            } else text
            val parts = slug.removeSuffix(".git").split('/')
            require(parts.size == 2 && parts[0].matches(Regex("[A-Za-z0-9][A-Za-z0-9-]{0,38}")) &&
                parts[1].matches(Regex("[A-Za-z0-9_.-]{1,100}")) && parts[1] !in listOf(".", "..")) {
                "That doesn't look like owner/repository."
            }
            return RepoId(parts[0].lowercase(), parts[1].lowercase())
        }
    }
}

data class Artifact(val asset: String, val sha256: String, val size: Long)
data class Delta(val fromVersionCode: Long, val fromSha256: String, val file: Artifact)
data class Manifest(
    val packageName: String, val name: String, val versionName: String,
    val versionCode: Long, val apk: Artifact, val patches: List<Delta>,
) {
    companion object {
        private val hash = Regex("[a-f0-9]{64}")
        private fun digest(value: String): String = value.also { require(hash.matches(it)) { "Invalid SHA-256 in manifest." } }
        private fun artifact(json: JSONObject): Artifact {
            val name = json.getString("asset")
            require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,199}"))) { "Invalid release asset filename." }
            val size = json.getLong("size")
            require(size in 1..4_294_967_296L) { "Asset size is outside the supported range." }
            return Artifact(name, digest(json.getString("sha256")), size)
        }
        fun parse(text: String): Manifest {
            val json = JSONObject(text)
            require(json.getInt("schemaVersion") == 1) { "Unsupported Appfredo manifest version." }
            val pkg = json.getString("packageName")
            require(pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))) { "Invalid Android package name." }
            val code = json.getLong("versionCode")
            require(code > 0) { "versionCode must be positive." }
            val name = json.getString("name").also { require(it.isNotBlank() && it.length <= 100) }
            val version = json.getString("versionName").also { require(it.isNotBlank() && it.length <= 100) }
            val apk = artifact(json.getJSONObject("apk"))
            require(apk.asset.endsWith(".apk")) { "The full download must be an APK." }
            val items = json.optJSONArray("patches")
            require((items?.length() ?: 0) <= 100) { "Too many patches in manifest." }
            val patches = (0 until (items?.length() ?: 0)).map { i ->
                val p = items!!.getJSONObject(i)
                val from = p.getLong("fromVersionCode")
                require(from in 1 until code) { "Patches must come from an older version." }
                val file = artifact(p)
                require(file.asset.endsWith(".pwr")) { "Patches must be Wharf .pwr files." }
                Delta(from, digest(p.getString("fromSha256")), file)
            }
            require(patches.map { it.fromSha256 }.distinct().size == patches.size) { "Duplicate patch base." }
            return Manifest(pkg, name, version, code, apk, patches)
        }
    }
}

data class Release(val manifest: Manifest, val tag: String, val notes: String, val assets: Map<String, String>, val raw: String)
data class TrackedRepo(val id: RepoId, val release: Release? = null, val error: String? = null, val checkedAt: Long = 0)
data class InstalledApp(val versionCode: Long, val versionName: String, val apkPath: String, val split: Boolean)

fun chooseDelta(manifest: Manifest, installed: InstalledApp?): Delta? =
    if (installed == null || installed.split) null else manifest.patches
        .filter { it.fromVersionCode == installed.versionCode && it.file.size < manifest.apk.size }
        .minByOrNull { it.file.size }
