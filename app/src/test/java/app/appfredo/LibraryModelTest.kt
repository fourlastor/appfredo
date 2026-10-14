package app.appfredo

import android.content.Intent
import app.appfredo.data.*
import app.appfredo.install.*
import app.appfredo.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LibraryModelTest {
    private val hash = "a".repeat(64)
    private fun manifest(name: String, code: Long, patchFrom: Long? = null) = Manifest(
        "example.${name.lowercase()}", name, "$code.0", code, Artifact("app.apk", hash, 4096),
        listOfNotNull(patchFrom?.let { Delta(it, hash, Artifact("update.pwr", hash, 512)) }),
    )
    private fun repo(manifest: Manifest?, slug: String = "owner/${manifest?.name?.lowercase()}", error: String? = null) =
        TrackedRepo(RepoId.parse(slug), manifest?.let { Release(it, "v1", "", emptyMap(), "") }, error, checkedAt = if (manifest == null) 0 else 42)
    private fun installed(code: Long) = InstalledApp(code, "$code.0", "/app.apk", false)

    private val jam = manifest("Jam", 2, patchFrom = 1)
    private val tea = manifest("Tea", 5)
    private val oats = manifest("Oats", 3)
    private val library = StoreState(
        repositories = listOf(repo(jam), repo(tea), repo(oats), repo(null, "owner/ghost", error = "Gone."), repo(null, "owner/new")),
        installed = mapOf(jam.packageName to installed(1), tea.packageName to installed(5)),
    )

    @Test fun eachAppStandsWhereItsVersionsPutIt() {
        val entries = library.entries()
        assertEquals(listOf(AppStatus.Update, AppStatus.Fresh, AppStatus.Available, AppStatus.Unchecked, AppStatus.Unchecked), entries.map { it.status })
        assertEquals(512L, entries[0].patch?.file?.size)
        assertNull(entries[1].patch)
        assertEquals("ghost", entries[3].name)
        // A first look-up shows as checking; a failed one waits to be retried instead.
        assertEquals(listOf(AppStatus.Unchecked, AppStatus.Checking), library.copy(refreshing = true).entries().takeLast(2).map { it.status })
    }

    @Test fun theAppInHandIsBusyWhileDownloadingAndWhileInstalling() {
        assertEquals(listOf("owner/tea"), library.copy(activeRepo = "owner/tea").entries().filter { it.busy }.map { it.slug })
        val installing = library.copy(activeRepo = "installer", ready = PreparedApk(File("jam.apk"), jam, true))
        assertEquals(listOf("owner/jam"), installing.entries().filter { it.busy }.map { it.slug })
        assertTrue(library.copy(ready = PreparedApk(File("jam.apk"), jam, true)).entries().none { it.busy })
    }

    @Test fun searchAndUpdatesFilterNarrowTheLibrary() {
        val entries = library.entries()
        assertEquals(listOf("owner/jam"), entries.matching("", updatesOnly = true).map { it.slug })
        assertEquals(listOf("owner/tea"), entries.matching("TEA", updatesOnly = false).map { it.slug })
        assertEquals(listOf("owner/ghost"), entries.matching("gho", updatesOnly = false).map { it.slug })
        assertTrue(entries.matching("tea", updatesOnly = true).isEmpty())
    }

    @Test fun theMostPressingNewsLeads() {
        fun mood(state: StoreState) = state.entries().summary().mood
        assertEquals(Mood.Empty, mood(StoreState()))
        assertEquals(Mood.Updates, mood(library))
        val current = library.copy(installed = library.installed + (jam.packageName to installed(2)))
        assertEquals(Mood.Trouble, mood(current))
        val tidy = current.copy(repositories = current.repositories.take(3))
        assertEquals(Mood.Fresh, mood(tidy))
        assertEquals(Mood.Menu, mood(tidy.copy(installed = emptyMap())))
        // A look-up in progress is said out loud; one that simply has not happened is not.
        assertEquals(Mood.Waiting, mood(tidy.copy(repositories = tidy.repositories + repo(null, "owner/new"), refreshing = true)))
        assertEquals(Mood.Fresh, mood(tidy.copy(repositories = tidy.repositories + repo(null, "owner/new"))))
        val summary = library.copy(refreshing = true).entries().summary()
        assertEquals(listOf(5, 1, 2, 1, 1), listOf(summary.apps, summary.updates, summary.installed, summary.troubled, summary.checking))
        assertEquals(42L, summary.checkedAt)
    }

    @Test fun theTrayFollowsOneJobFromDownloadToDone() {
        val apk = PreparedApk(File("jam.apk"), jam, true)
        assertNull(library.tray())

        val working = library.copy(activeRepo = "owner/jam", transfer = Transfer("Fetching", 0.5f, "1 KB of 2 KB")).tray()!!
        assertEquals(TrayStage.Working("Fetching", "1 KB of 2 KB", 0.5f), working.stage)
        assertEquals("Jam", working.appName)

        assertEquals(TrayStage.Served(apk, null), library.copy(ready = apk).tray()!!.stage)
        // Handing the APK to Android keeps it served: only the words change, so the tray does not flinch.
        assertEquals(TrayStage.Served(apk, null, "Presenting"), library.copy(activeRepo = "installer", ready = apk, transfer = Transfer("Presenting")).tray()!!.stage)

        val asking = library.copy(ready = apk, installResult = InstallResult("Awaiting", Intent())).tray()!!
        assertEquals(TrayStage.Confirming, asking.stage)
        assertEquals("Jam", asking.appName)

        // A cancelled prompt leaves the APK served, with the reason beside it, so Install can be tapped again.
        assertEquals(TrayStage.Served(apk, "As you wish."), library.copy(ready = apk, installResult = InstallResult("As you wish.")).tray()!!.stage)

        val done = library.copy(installResult = InstallResult("Installed.", success = true), served = jam).tray()!!
        assertEquals(TrayStage.Finished(true, "Installed.", jam), done.stage)
        assertEquals("Jam", done.appName)
    }

    @Test fun troubleShowsOnItsOwnOrBesideWhatIsStillServed() {
        val alone = library.copy(message = "No room.").tray()!!
        assertNull(alone.stage)
        assertEquals("No room.", alone.trouble)
        val beside = library.copy(ready = PreparedApk(File("jam.apk"), jam, false), message = "Allow installs.").tray()!!
        assertTrue(beside.stage is TrayStage.Served)
        assertEquals("Allow installs.", beside.trouble)
    }
}
