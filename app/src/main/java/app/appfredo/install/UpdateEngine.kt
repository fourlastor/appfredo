package app.appfredo.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.appfredo.data.*
import java.io.File
import wharf.Wharf

data class Transfer(val message: String, val fraction: Float? = null, val detail: String? = null)
data class PreparedApk(val file: File, val manifest: Manifest, val usedDelta: Boolean)

class UpdateEngine(private val context: Context, private val github: ArtifactDownloader) {
    @Suppress("DEPRECATION")
    fun installed(packageName: String): InstalledApp? = try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        InstalledApp(if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            info.versionName ?: "", info.applicationInfo!!.sourceDir, !info.applicationInfo!!.splitSourceDirs.isNullOrEmpty())
    } catch (_: PackageManager.NameNotFoundException) { null }

    fun prepare(release: Release, report: (Transfer) -> Unit): PreparedApk {
        val manifest = release.manifest
        val current = installed(manifest.packageName)
        check(current == null || current.versionCode < manifest.versionCode) { "You already have this version, or a newer one." }
        val directory = File(context.filesDir, "apks/${manifest.packageName}").apply { mkdirs() }
        // A process killed during download/patching can leave uncommitted staging files.
        directory.listFiles()?.filter { it.name.startsWith(".wharf-") || it.name.endsWith(".part") }?.forEach { it.deleteRecursively() }
        val final = File(directory, "${manifest.apk.sha256}.apk")
        if (final.exists()) {
            report(Transfer("Checking the pantry for a saved copy"))
            if (final.length() == manifest.apk.size && sha256(final) == manifest.apk.sha256) {
                validate(final, manifest)
                return PreparedApk(final, manifest, false)
            }
            check(final.delete()) { "I couldn't clear out a half-finished download." }
        }
        val delta = chooseDelta(manifest, current)
        // Reserve room for a copied base, reconstruction and download, plus filesystem overhead.
        check(directory.usableSpace > manifest.apk.size * 3 + 16 * 1024 * 1024) { "There isn't enough free space to prepare this app." }
        var usedDelta = false
        if (delta != null) {
            val cached = File(directory, "${delta.fromSha256}.apk")
            val base = if (cached.exists()) cached else File(current!!.apkPath)
            report(Transfer("Inspecting the version you have"))
            if (runCatching { sha256(base) == delta.fromSha256 }.getOrDefault(false)) {
                val patch = File(directory, "update.pwr")
                try {
                    github.download(release.assets.getValue(delta.file.asset), delta.file, patch) { done, total ->
                        report(Transfer("Fetching just the fresh bits", done.toFloat() / total, "${formatBytes(done)} of ${formatBytes(total)}"))
                    }
                    report(Transfer("Stitching the patch into place"))
                    Wharf.apply(base.absolutePath, patch.absolutePath, final.absolutePath, delta.fromSha256,
                        manifest.apk.sha256, manifest.apk.size)
                    usedDelta = true
                } catch (e: Exception) {
                    final.delete()
                    report(Transfer("The patch didn't take. Fetching the whole app."))
                } finally { patch.delete() }
            } else report(Transfer("Your copy isn't the one I expected. Fetching the whole app."))
        }
        if (!usedDelta) github.download(release.assets.getValue(manifest.apk.asset), manifest.apk, final) { done, total ->
            report(Transfer("Fetching the whole app", done.toFloat() / total, "${formatBytes(done)} of ${formatBytes(total)}"))
        }
        report(Transfer("One last inspection"))
        try { validate(final, manifest) } catch (e: Exception) { final.delete(); throw e }
        return PreparedApk(final, manifest, usedDelta)
    }

    @Suppress("DEPRECATION")
    private fun validate(file: File, manifest: Manifest) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("That download isn't a readable Android app.")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        check(info.packageName == manifest.packageName && code == manifest.versionCode && info.versionName == manifest.versionName) {
            "The APK isn't what its release promised: package or version differs."
        }
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        check(!signatures.isNullOrEmpty()) { "The APK isn't signed by its maker, so I won't serve it." }
        // Android's PackageInstaller performs final signature validation and upgrade-key matching.
    }

    fun prune(packageName: String, keepHash: String) {
        File(context.filesDir, "apks/$packageName").listFiles()?.filter { it.name != "$keepHash.apk" }?.forEach { it.deleteRecursively() }
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
