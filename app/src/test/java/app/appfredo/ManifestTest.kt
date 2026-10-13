package app.appfredo

import app.appfredo.data.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ManifestTest {
    private fun json(): JSONObject = JSONObject("""{
        "schemaVersion":1,"packageName":"example.app","name":"Example","versionName":"2.0","versionCode":2,
        "apk":{"asset":"app.apk","sha256":"${"a".repeat(64)}","size":4096},
        "patches":[{"fromVersionCode":1,"fromSha256":"${"b".repeat(64)}","asset":"update.pwr","sha256":"${"c".repeat(64)}","size":512}]
    }""")
    private fun rejects(edit: (JSONObject) -> Unit) {
        val source = json().also(edit)
        assertThrows(Exception::class.java) { Manifest.parse(source.toString()) }
    }
    @Test fun repositoryUrlsNormalize() {
        assertEquals("owner/repo", RepoId.parse(" https://github.com/Owner/Repo.git/ ").slug)
        listOf("http://github.com/a/b", "https://github.com.evil/a/b", "https://github.com/a/b?x=1", "a/../b", "a/..", "https://github.com@evil/a/b", "a/b/releases").forEach {
            assertThrows("Must reject $it", Exception::class.java) { RepoId.parse(it) }
        }
    }
    @Test fun manifestRejectsUnsafeAndInconsistentAssets() {
        rejects { it.put("schemaVersion", 2) }
        rejects { it.put("packageName", "../outside") }
        rejects { it.getJSONObject("apk").put("asset", "../app.apk") }
        rejects { it.getJSONObject("apk").put("sha256", "not-a-hash") }
        rejects { it.getJSONObject("apk").put("size", -1) }
        rejects { it.getJSONObject("apk").put("size", 4_294_967_297L) }
        rejects { it.getJSONArray("patches").getJSONObject(0).put("fromVersionCode", 2) }
    }
    @Test fun deltaRequiresMatchingVersionSingleApkAndBandwidthSaving() {
        val manifest = Manifest.parse(json().toString())
        val old = InstalledApp(1, "1.0", "/app.apk", false)
        assertNotNull(chooseDelta(manifest, old))
        assertNull(chooseDelta(manifest, null))
        assertNull(chooseDelta(manifest, old.copy(versionCode = 3)))
        assertNull(chooseDelta(manifest, old.copy(split = true)))
        assertNull(chooseDelta(manifest.copy(apk = manifest.apk.copy(size = 128)), old))
    }
}
