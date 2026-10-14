package app.appfredo.debug

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.appfredo.StoreState
import app.appfredo.data.RepoId
import app.appfredo.data.TrackedRepo
import app.appfredo.install.InstallResult
import app.appfredo.install.PreparedApk
import app.appfredo.install.Transfer
import app.appfredo.install.formatBytes
import app.appfredo.ui.AppfredoApp
import app.appfredo.ui.StoreActions
import app.appfredo.ui.theme.AppfredoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Every state of the app on sample data, with a pretend store behind it: nothing here touches the network,
 * the disk or the package installer. Pick a starting point with `-e scene <name>` (see [Samples.scenes]),
 * force a theme with `-e theme dark|light`, and show the permission step with `--ez permission true`.
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = Samples.scenes[intent.getStringExtra("scene")] ?: Samples.scenes.getValue("library")
        val forced = when (intent.getStringExtra("theme")) { "dark" -> true; "light" -> false; else -> null }
        val permission = intent.getBooleanExtra("permission", false)
        // A forced theme has to take the system bars with it, or their icons keep following the system's.
        when (forced) {
            true -> enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
            false -> enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT), SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
            null -> enableEdgeToEdge()
        }
        setContent {
            AppfredoTheme(dark = forced ?: isSystemInDarkTheme()) {
                val scope = rememberCoroutineScope()
                var state by remember { mutableStateOf(start) }
                var permissionNeeded by remember { mutableStateOf(permission) }
                fun update(change: (StoreState) -> StoreState) { state = change(state) }
                val actions = remember {
                    StoreActions(
                        vet = { input -> runCatching { RepoId.parse(input) }.exceptionOrNull()?.message },
                        add = { input -> update { it.copy(repositories = it.repositories + TrackedRepo(RepoId.parse(input))) } },
                        remove = { id -> update { it.copy(repositories = it.repositories.filterNot { repo -> repo.id == id }) } },
                        restore = { repo, index ->
                            update { it.copy(repositories = it.repositories.toMutableList().apply { add(index.coerceIn(0, size), repo) }) }
                        },
                        refresh = {
                            scope.launch {
                                update { it.copy(refreshing = true) }
                                delay(1500)
                                update { it.copy(refreshing = false) }
                            }
                        },
                        prepare = { repo ->
                            val manifest = repo.release?.manifest
                            if (manifest != null) scope.launch {
                                val size = manifest.patches.firstOrNull()?.file?.size ?: manifest.apk.size
                                update { it.copy(activeRepo = repo.id.slug, transfer = Transfer("Rolling up my sleeves"), ready = null, message = null, installResult = null, served = null) }
                                delay(600)
                                for (step in 1..20) {
                                    update { it.copy(transfer = Transfer("Fetching just the fresh bits", step / 20f, "${formatBytes(size * step / 20)} of ${formatBytes(size)}")) }
                                    delay(120)
                                }
                                update { it.copy(transfer = Transfer("Stitching the patch into place")) }
                                delay(900)
                                update { it.copy(activeRepo = null, transfer = null, ready = PreparedApk(File("sample.apk"), manifest, manifest.patches.isNotEmpty())) }
                            }
                        },
                        install = {
                            scope.launch {
                                update { it.copy(activeRepo = "installer", transfer = Transfer("Presenting it to Android"), installResult = null) }
                                delay(900)
                                // Stands in for Android's own prompt, which the real app opens at this point.
                                update { it.copy(activeRepo = null, transfer = null, installResult = InstallResult("Awaiting Android's approval", Intent())) }
                                delay(1600)
                                val apk = state.ready
                                update {
                                    it.copy(
                                        activeRepo = null, transfer = null, ready = null, served = apk?.manifest,
                                        installResult = InstallResult("Installed, and at your service.", success = true),
                                    )
                                }
                            }
                        },
                        openInstallSettings = { permissionNeeded = false },
                        showPrompt = {},
                        dismissTray = { update { it.copy(ready = null, message = null, installResult = null, served = null) } },
                        canOpen = { packageManager.getLaunchIntentForPackage(it) != null },
                        open = {},
                        viewRepository = {},
                    )
                }
                AppfredoApp(state, permissionNeeded, "0.1.0", actions)
            }
        }
    }
}
