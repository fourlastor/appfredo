package app.appfredo.debug

import android.content.Intent
import app.appfredo.StoreState
import app.appfredo.data.Artifact
import app.appfredo.data.Delta
import app.appfredo.data.InstalledApp
import app.appfredo.data.Manifest
import app.appfredo.data.Release
import app.appfredo.data.RepoId
import app.appfredo.data.TrackedRepo
import app.appfredo.install.InstallResult
import app.appfredo.install.PreparedApk
import app.appfredo.install.Transfer
import java.io.File

/** Made-up libraries for previews and the gallery. Two entries borrow real system packages so real icons show. */
internal object Samples {
    private const val MB = 1024L * 1024L
    private val hash = "a".repeat(64)

    private fun manifest(packageName: String, name: String, version: String, code: Long, size: Long, patch: Long? = null) = Manifest(
        packageName, name, version, code, Artifact("app.apk", hash, size),
        listOfNotNull(patch?.let { Delta(code - 1, hash, Artifact("update.pwr", hash, it)) }),
    )

    private fun repo(slug: String, manifest: Manifest?, notes: String = "", error: String? = null) = TrackedRepo(
        RepoId.parse(slug), manifest?.let { Release(it, "v${it.versionName}", notes, emptyMap(), "") }, error,
        checkedAt = if (manifest == null) 0 else System.currentTimeMillis() - 7 * 60_000,
    )

    private fun installed(code: Long, version: String) = InstalledApp(code, version, "/data/app/base.apk", false)

    private val marmalade = manifest("app.sample.marmalade", "Marmalade", "2.4.0", 24, 38 * MB, patch = 260 * 1024)
    private val teatime = manifest("app.sample.teatime", "Teatime", "1.8.2", 18, 12 * MB)
    private val clock = manifest("com.google.android.deskclock", "Clock", "7.1", 71, 9 * MB)
    private val settings = manifest("com.android.settings", "Silver Polish", "3.0.1", 30, 21 * MB)

    private val notes = """
        Spread a little thicker this time.

        - Jars now sync between devices
        - Fixed a sticky scroll on the recipes list
        - Smaller updates: this one ships with a Wharf patch
    """.trimIndent()

    private val repositories = listOf(
        repo("preserves/marmalade", marmalade, notes),
        repo("kettle/teatime", teatime, "Brews a minute faster."),
        repo("aosp/deskclock", clock),
        repo("butlers/silver-polish", settings, "A deep clean."),
        repo("pantry/crumpets", null, error = "No public release at that address. Check the repository, or ask its maker for a stable release."),
    )

    private val library = StoreState(
        repositories = repositories,
        installed = mapOf(
            marmalade.packageName to installed(23, "2.3.1"),
            clock.packageName to installed(71, "7.1"),
            settings.packageName to installed(30, "3.0.1"),
        ),
    )

    private val served = PreparedApk(File("marmalade.apk"), marmalade, usedDelta = true)

    val scenes: Map<String, StoreState> = linkedMapOf(
        "empty" to StoreState(),
        "library" to library,
        "fresh" to library.copy(
            repositories = repositories.take(4),
            installed = library.installed + mapOf(marmalade.packageName to installed(24, "2.4.0"), teatime.packageName to installed(18, "1.8.2")),
        ),
        "checking" to StoreState(repositories = listOf(repo("preserves/marmalade", null), repo("kettle/teatime", null)), refreshing = true),
        "working" to library.copy(
            activeRepo = "preserves/marmalade",
            transfer = Transfer("Fetching just the fresh bits", 0.42f, "109.2 KB of 260.0 KB"),
        ),
        "stitching" to library.copy(activeRepo = "preserves/marmalade", transfer = Transfer("Stitching the patch into place")),
        "served" to library.copy(ready = served),
        "cancelled" to library.copy(ready = served, installResult = InstallResult("As you wish. Nothing was changed.")),
        "confirming" to library.copy(ready = served, installResult = InstallResult("Awaiting Android's approval", Intent())),
        "done" to library.copy(
            installed = library.installed + (marmalade.packageName to installed(24, "2.4.0")),
            installResult = InstallResult("Installed, and at your service.", success = true), served = clock,
        ),
        "trouble" to library.copy(message = "There isn't enough free space to prepare this app."),
    )
}
