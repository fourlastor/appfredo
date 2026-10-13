package app.appfredo

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import app.appfredo.data.sha256
import java.io.File
import wharf.Wharf

@RunWith(AndroidJUnit4::class)
class NativePatchTest {
    @Test fun bundledWharfReconstructsButlerPatch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.cacheDir, "native-test").apply { deleteRecursively(); mkdirs() }
        try {
            fun asset(name: String, dest: String): File = File(directory, dest).also { file ->
                instrumentation.context.assets.open(name).use { source -> file.outputStream().use { source.copyTo(it) } }
            }
            val old = asset("old/app.apk", "old.apk")
            val next = asset("new/app.apk", "new.apk")
            val patch = asset("update.pwr", "update.pwr")
            val output = File(directory, "rebuilt.apk")
            val baseHash = sha256(old)
            Wharf.apply(old.absolutePath, patch.absolutePath, output.absolutePath, baseHash, sha256(next), next.length())
            assertEquals(sha256(next), sha256(output))
            assertEquals(baseHash, sha256(old))
        } finally { directory.deleteRecursively() }
    }
}
