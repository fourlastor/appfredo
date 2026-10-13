package app.appfredo.install

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.appfredo.data.*
import java.io.File
import wharf.Wharf

data class Transfer(val message: String, val fraction: Float? = null)
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
        check(current == null || current.versionCode < manifest.versionCode) { "This version or a newer one is already installed." }
        val directory = File(context.filesDir, "apks/${manifest.packageName}").apply { mkdirs() }
        // A process killed during download/patching can leave uncommitted staging files.
        directory.listFiles()?.filter { it.name.startsWith(".wharf-") || it.name.endsWith(".part") }?.forEach { it.deleteRecursively() }
        val final = File(directory, "${manifest.apk.sha256}.apk")
        if (final.exists()) {
            report(Transfer("Checking saved APK"))
            if (final.length() == manifest.apk.size && sha256(final) == manifest.apk.sha256) {
                validate(final, manifest)
                return PreparedApk(final, manifest, false)
            }
            check(final.delete()) { "Could not replace an incomplete APK." }
        }
        val delta = chooseDelta(manifest, current)
        // Reserve room for a copied base, reconstruction and download, plus filesystem overhead.
        check(directory.usableSpace > manifest.apk.size * 3 + 16 * 1024 * 1024) { "Not enough free storage to prepare this APK." }
        var usedDelta = false
        if (delta != null) {
            val cached = File(directory, "${delta.fromSha256}.apk")
            val base = if (cached.exists()) cached else File(current!!.apkPath)
            report(Transfer("Checking the previous APK"))
            if (runCatching { sha256(base) == delta.fromSha256 }.getOrDefault(false)) {
                val patch = File(directory, "update.pwr")
                try {
                    github.download(release.assets.getValue(delta.file.asset), delta.file, patch) { done, total ->
                        report(Transfer("Downloading update · ${formatBytes(done)} / ${formatBytes(total)}", done.toFloat() / total))
                    }
                    report(Transfer("Preparing your update"))
                    Wharf.apply(base.absolutePath, patch.absolutePath, final.absolutePath, delta.fromSha256,
                        manifest.apk.sha256, manifest.apk.size)
                    usedDelta = true
                } catch (e: Exception) {
                    final.delete()
                    report(Transfer("Patch unavailable; downloading the full APK"))
                } finally { patch.delete() }
            } else report(Transfer("Previous APK differs; downloading the full APK"))
        }
        if (!usedDelta) github.download(release.assets.getValue(manifest.apk.asset), manifest.apk, final) { done, total ->
            report(Transfer("Downloading APK · ${formatBytes(done)} / ${formatBytes(total)}", done.toFloat() / total))
        }
        report(Transfer("Checking Android package"))
        try { validate(final, manifest) } catch (e: Exception) { final.delete(); throw e }
        return PreparedApk(final, manifest, usedDelta)
    }

    @Suppress("DEPRECATION")
    private fun validate(file: File, manifest: Manifest) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("The download is not a readable Android APK.")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        check(info.packageName == manifest.packageName && code == manifest.versionCode && info.versionName == manifest.versionName) {
            "APK package or version does not match the release manifest."
        }
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        check(!signatures.isNullOrEmpty()) { "The APK must be signed by its publisher." }
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
