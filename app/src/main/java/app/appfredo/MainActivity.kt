package app.appfredo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.appfredo.ui.AppfredoApp
import app.appfredo.ui.StoreActions
import app.appfredo.ui.theme.AppfredoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            AppfredoTheme {
                val model: StoreViewModel = viewModel()
                val state by model.state.collectAsStateWithLifecycle()
                LifecycleResumeEffect(model) {
                    model.refreshInstalled()
                    onPauseOrDispose {}
                }
                // Installing needs a one-time trip to Android's settings. The tray explains it first, and the
                // installation carries on by itself once the user comes back having said yes.
                var permissionNeeded by rememberSaveable { mutableStateOf(false) }
                fun install() {
                    permissionNeeded = !packageManager.canRequestPackageInstalls()
                    if (!permissionNeeded) model.installReady()
                }
                val installSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    if (packageManager.canRequestPackageInstalls()) install()
                }
                // Android's own prompt follows straight from the user's tap on Install.
                LaunchedEffect(state.installResult) { model.takeConfirmation()?.let(::startActivity) }
                val actions = remember(model) {
                    StoreActions(
                        vet = model::vet,
                        add = { model.add(it) },
                        remove = model::remove,
                        restore = model::restore,
                        refresh = model::refresh,
                        prepare = { permissionNeeded = false; model.prepare(it) },
                        install = ::install,
                        openInstallSettings = {
                            installSettings.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                        },
                        showPrompt = { model.state.value.installResult?.confirmation?.let(::startActivity) },
                        dismissTray = model::dismissTray,
                        canOpen = { packageManager.getLaunchIntentForPackage(it) != null },
                        open = { packageManager.getLaunchIntentForPackage(it)?.let(::startActivity) },
                        viewRepository = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${it.slug}"))) },
                    )
                }
                AppfredoApp(state, permissionNeeded, version, actions)
            }
        }
    }
}
