package app.appfredo

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.appfredo.data.*
import app.appfredo.install.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Uses only a disposable emulator fixture package, never a real user's app. */
@RunWith(AndroidJUnit4::class)
class UpdateFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fixturePackage = "app.appfredo.fixture"
    private val downloads = mutableListOf<String>()
    private lateinit var release: Release
    private var corruptPatch = false
    private val downloader = ArtifactDownloader { _, artifact, destination, progress ->
        downloads.add(artifact.asset)
        destination.parentFile!!.mkdirs()
        if (corruptPatch && artifact.asset.endsWith(".pwr")) destination.writeText("broken patch")
        else instrumentation.context.assets.open("fixture/${artifact.asset}").use { input -> destination.outputStream().use { input.copyTo(it) } }
        progress(artifact.size, artifact.size)
    }
    private fun engine() = UpdateEngine(context, downloader)

    @Before fun setup() {
        val text = instrumentation.context.assets.open("fixture/appfredo.json").bufferedReader().use { it.readText() }
        val manifest = Manifest.parse(text)
        release = Release(manifest, "v2", "Test release", (listOf(manifest.apk) + manifest.patches.map { it.file }).associate { it.asset to "https://example.test/${it.asset}" }, "")
        File(context.filesDir, "apks/$fixturePackage").deleteRecursively()
        // Reset only our resource-only test package. The test runner has shell permissions on this emulator.
        instrumentation.uiAutomation.executeShellCommand("pm uninstall $fixturePackage").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
        val old = File(context.cacheDir, "fixture-old.apk")
        instrumentation.context.assets.open("fixture/old.apk").use { input -> old.outputStream().use { input.copyTo(it) } }
        val session = context.packageManager.packageInstaller
        // Installing via the shell requires a shell-readable file in /data/local/tmp.
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.INSTALL_PACKAGES")
        try {
            val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val id = session.createSession(params)
            session.openSession(id).use { s ->
                s.openWrite("base.apk", 0, old.length()).use { out -> old.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val callback = android.app.PendingIntent.getBroadcast(context, 992,
                    Intent(context, app.appfredo.install.InstallReceiver::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE)
                s.commit(callback.intentSender)
            }
            waitFor { engine().installed(fixturePackage)?.versionCode == 1L }
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
        (context.applicationContext as AppfredoApplication).installResult.value = null
    }

    @After fun cleanup() { File(context.filesDir, "apks/$fixturePackage").deleteRecursively() }

    @Test fun signedApkIsReconstructedFromInstalledBase() {
        val prepared = engine().prepare(release) {}
        assertTrue(prepared.usedDelta)
        assertEquals(listOf(release.manifest.patches.single().file.asset), downloads)
        assertEquals(release.manifest.apk.sha256, sha256(prepared.file))
        assertEquals(1L, engine().installed(fixturePackage)?.versionCode)
    }

    @Test fun fullApkAndCachedApkAreVerified() {
        val full = release.copy(manifest = release.manifest.copy(patches = emptyList()))
        val first = engine().prepare(full) {}
        assertFalse(first.usedDelta)
        val again = engine().prepare(full) {}
        assertEquals(first.file, again.file)
        assertEquals(listOf("app.apk"), downloads)
    }

    @Test fun badPatchFallsBackToFullApk() {
        corruptPatch = true
        val prepared = engine().prepare(release) {}
        assertFalse(prepared.usedDelta)
        assertEquals(listOf(release.manifest.patches.single().file.asset, "app.apk"), downloads)
        assertEquals(release.manifest.apk.sha256, sha256(prepared.file))
    }

    @Test fun mismatchedBaseSkipsPatch() {
        val bad = release.manifest.patches.single().copy(fromSha256 = "0".repeat(64))
        val prepared = engine().prepare(release.copy(manifest = release.manifest.copy(patches = listOf(bad)))) {}
        assertFalse(prepared.usedDelta)
        assertEquals(listOf("app.apk"), downloads)
    }

    @Test fun androidConfirmationInstallsTheReconstructedApk() {
        val prepared = engine().prepare(release) {}
        val app = context.applicationContext as AppfredoApplication
        instrumentation.uiAutomation.executeShellCommand("appops set app.appfredo REQUEST_INSTALL_PACKAGES allow").use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Installer(context).install(prepared)
        waitFor { app.installResult.value != null }
        val confirmation = app.installResult.value!!.confirmation
        assertNotNull("Expected Android user confirmation: ${app.installResult.value}", confirmation)
        context.startActivity(confirmation!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val device = UiDevice.getInstance(instrumentation)
        val button = device.wait(Until.findObject(By.res("android", "button1")), 10_000)
        assertNotNull("Android installer confirmation was not displayed", button)
        assertTrue(button.text in listOf("Update", "Install"))
        button.click()
        waitFor { engine().installed(fixturePackage)?.versionCode == 2L }
        waitFor { app.installResult.value?.success == true }
        assertEquals(release.manifest.apk.sha256, sha256(File(engine().installed(fixturePackage)!!.apkPath)))
    }

    private fun waitFor(check: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 20_000
        while (!check() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue("Timed out waiting for Android", check())
    }
}
