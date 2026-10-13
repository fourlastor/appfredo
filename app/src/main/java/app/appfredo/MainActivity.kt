package app.appfredo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.appfredo.data.*
import app.appfredo.install.formatBytes

private val Cream = Color(0xFFF6F5EF)
private val Green = Color(0xFF285C4D)
private val Ink = Color(0xFF233C34)
private val Gold = Color(0xFFD8BA72)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Green,
                primaryContainer = Color(0xFFE2E9DD), secondaryContainer = Color(0xFFE2E9DD), onSecondaryContainer = Ink,
                outline = Color(0xFF8B9B90), outlineVariant = Color(0xFFD5DCD2),
                background = Cream, surface = Cream, surfaceContainer = Color(0xFFECEDE5), onBackground = Ink, onSurface = Ink)) {
                val model: StoreViewModel = viewModel()
                val state by model.state.collectAsStateWithLifecycle()
                val lifecycle = LocalLifecycleOwner.current
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refreshInstalled() }
                    lifecycle.lifecycle.addObserver(observer)
                    onDispose { lifecycle.lifecycle.removeObserver(observer) }
                }
                StoreScreen(state, model,
                    onInstall = {
                        if (packageManager.canRequestPackageInstalls()) model.installReady()
                        else startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    },
                    onConfirm = { state.installResult?.confirmation?.let { startActivity(it) } },
                    onOpen = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${it.slug}"))) })
            }
        }
    }
}

@Composable
private fun StoreScreen(state: StoreState, model: StoreViewModel, onInstall: () -> Unit, onConfirm: () -> Unit, onOpen: (RepoId) -> Unit) {
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var guideOpen by rememberSaveable { mutableStateOf(false) }
    var details by remember { mutableStateOf<TrackedRepo?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var updatesOnly by rememberSaveable { mutableStateOf(false) }
    fun hasUpdate(repo: TrackedRepo): Boolean {
        val manifest = repo.release?.manifest ?: return false
        val installed = state.installed[manifest.packageName] ?: return false
        return installed.versionCode < manifest.versionCode
    }
    val updates = state.repositories.count { hasUpdate(it) }
    val visible = state.repositories.filter { (!updatesOnly || hasUpdate(it)) &&
        (it.id.slug.contains(query, true) || it.release?.manifest?.name?.contains(query, true) == true) }

    Scaffold(containerColor = Cream, bottomBar = {
        Surface(color = Cream, tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = !updatesOnly, onClick = { updatesOnly = false }, label = { Text("Your library") }, modifier = Modifier.weight(1f))
                FilterChip(selected = updatesOnly, onClick = { updatesOnly = true }, label = { Text("Updates${if (updates > 0) " · $updates" else ""}") }, modifier = Modifier.weight(1f))
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    ButlerMark(Modifier.size(46.dp), Green)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("appfredo", fontFamily = FontFamily.Serif, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                        Text("YOUR APP BUTLER", fontSize = 9.sp, letterSpacing = 2.sp, color = Green)
                    }
                    TextButton(onClick = { guideOpen = true }) { Text("About") }
                }
            }
            item {
                Surface(color = Green, shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(26.dp)) {
                        Text("AT YOUR SERVICE", fontSize = 10.sp, letterSpacing = 2.sp, color = Gold)
                        Spacer(Modifier.height(14.dp))
                        Text(if (updatesOnly) "Fresh updates,\ncoming right up." else "Good apps.\nWell looked after.", color = Cream,
                            fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 38.sp)
                        Spacer(Modifier.height(14.dp))
                        Text("Your apps, straight from their makers.\nSmaller downloads when updates arrive.", color = Cream.copy(alpha = .8f), fontSize = 14.sp, lineHeight = 21.sp)
                        Spacer(Modifier.height(22.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("${state.repositories.size} repositories", color = Cream, fontSize = 12.sp)
                            Text("·", color = Gold)
                            Text("$updates updates available", color = Cream, fontSize = 12.sp)
                        }
                    }
                }
            }
            state.transfer?.let { transfer -> item {
                Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFFE7ECDF)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(transfer.message, style = MaterialTheme.typography.bodyMedium)
                        if (transfer.fraction != null) LinearProgressIndicator(progress = { transfer.fraction }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Keep Appfredo open while this download finishes.", fontSize = 12.sp)
                    }
                }
            } }
            state.message?.let { message -> item {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) { Text(message); TextButton(onClick = model::dismissMessage) { Text("Dismiss") } }
                }
            } }
            state.installResult?.let { result -> item {
                Surface(color = Color(0xFFE7ECDF), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(result.message)
                        if (result.confirmation != null) Button(onClick = onConfirm, modifier = Modifier.padding(top = 12.dp)) { Text("Review installation") }
                    }
                }
            } }
            state.ready?.takeIf { state.installResult?.confirmation == null }?.let { apk -> item {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${apk.manifest.name} is ready", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text("Version ${apk.manifest.versionName} · ${if (apk.usedDelta) "Prepared from a patch" else "Full APK downloaded"}", fontSize = 13.sp)
                        Text("Android will ask you to confirm. On first use, allow Appfredo to install apps, then return here.", fontSize = 13.sp)
                        Button(onClick = onInstall, enabled = state.activeRepo == null) { Text("Install with Android") }
                    }
                }
            } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (updatesOnly) "Ready when you are" else "Your library", fontFamily = FontFamily.Serif, fontSize = 25.sp, modifier = Modifier.weight(1f))
                    if (state.repositories.isNotEmpty()) TextButton(onClick = model::refresh, enabled = !state.refreshing) { Text(if (state.refreshing) "Checking…" else "Refresh") }
                    FilledTonalButton(onClick = { addOpen = true }, enabled = !state.refreshing) { Text("+ Add") }
                }
            }
            if (state.repositories.isNotEmpty()) item {
                OutlinedTextField(value = query, onValueChange = { query = it }, placeholder = { Text("Find an app or repository") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
            }
            if (visible.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ButlerMark(Modifier.size(88.dp), Green)
                    Spacer(Modifier.height(16.dp))
                    Text(when { query.isNotBlank() -> "Nothing by that name"; updatesOnly -> "All in good order"; else -> "Let's set the table." },
                        fontFamily = FontFamily.Serif, fontSize = 25.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(when { query.isNotBlank() -> "Try a different app or repository."; updatesOnly -> "Refresh your library to check for new releases.";
                        else -> "Add a GitHub repository to bring\nyour first app into the fold." },
                        color = Ink.copy(alpha = .65f), fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    if (state.repositories.isEmpty()) Button(onClick = { addOpen = true }, modifier = Modifier.padding(top = 20.dp)) { Text("Add your first repository") }
                }
            }
            items(visible, key = { it.id.slug }) { repo ->
                val manifest = repo.release?.manifest
                val installed = manifest?.let { state.installed[it.packageName] }
                val update = hasUpdate(repo)
                val delta = manifest?.let { chooseDelta(it, installed) }
                Surface(color = Color.White, shape = RoundedCornerShape(22.dp), onClick = { details = repo }) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color(0xFFECEDE5), shape = RoundedCornerShape(13.dp)) {
                                Text((manifest?.name ?: repo.id.name).take(1).uppercase(), Modifier.padding(14.dp), fontSize = 22.sp, fontFamily = FontFamily.Serif, color = Green)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(manifest?.name ?: repo.id.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                                Text(repo.id.slug, fontSize = 12.sp, color = Ink.copy(alpha = .6f))
                            }
                        }
                        repo.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                        if (manifest != null) {
                            Text(when {
                                update -> "${installed!!.versionName}  →  ${manifest.versionName}"
                                installed != null -> "Installed · ${installed.versionName}"
                                else -> "Version ${manifest.versionName} · ${formatBytes(manifest.apk.size)}"
                            }, fontSize = 13.sp, color = Green)
                            if (delta != null) Text("Patch available · ${formatBytes(delta.file.size)} instead of ${formatBytes(manifest.apk.size)}", fontSize = 12.sp, color = Green)
                            Button(onClick = { model.prepare(repo) }, enabled = state.activeRepo == null && (installed == null || update), modifier = Modifier.fillMaxWidth()) {
                                Text(when { update -> "Get update"; installed != null -> "Up to date"; else -> "Get app" })
                            }
                        } else if (!state.refreshing) OutlinedButton(onClick = model::refresh) { Text("Check release again") }
                    }
                }
            }
            item { Text("A personal collection. Thoughtfully served.", color = Ink.copy(alpha = .5f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp)) }
        }
    }
    if (addOpen) AddRepositoryDialog(onDismiss = { addOpen = false }, onAdd = { model.add(it) })
    if (guideOpen) AlertDialog(onDismissRequest = { guideOpen = false }, title = { Text("Meet your app butler.", fontFamily = FontFamily.Serif) },
        text = { Text("Appfredo keeps a personal library of Android apps from public GitHub releases.\n\nAdd a repository, check its release, and let Appfredo prepare your APK. Android handles the final installation.\n\nPublishers include appfredo.json and a signed APK. Optional Wharf patches make updates smaller. If a patch cannot be used, Appfredo downloads the complete APK.\n\nThis first version supports stable releases and single APKs. Check for updates with Refresh.") },
        confirmButton = { TextButton(onClick = { guideOpen = false }) { Text("At your service") } })
    details?.let { repo -> AlertDialog(onDismissRequest = { details = null }, title = { Text(repo.release?.manifest?.name ?: repo.id.name) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(repo.id.slug)
            Text(repo.release?.notes?.take(2500)?.ifBlank { "No release notes provided." } ?: "No Appfredo release loaded yet.")
            TextButton(onClick = { onOpen(repo.id) }) { Text("View on GitHub ↗") }
        } },
        confirmButton = { TextButton(onClick = { details = null }) { Text("Done") } },
        dismissButton = { TextButton(onClick = { model.remove(repo.id); details = null }, enabled = state.activeRepo != repo.id.slug) { Text("Remove repository") } }) }
}

@Composable
private fun AddRepositoryDialog(onDismiss: () -> Unit, onAdd: (String) -> String?) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("A new addition", fontFamily = FontFamily.Serif) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Paste a public GitHub repository. Its latest release should include an Appfredo manifest.")
            OutlinedTextField(value = input, onValueChange = { input = it; error = null }, label = { Text("owner / repository") },
                singleLine = true, isError = error != null, supportingText = { error?.let { Text(it) } })
        } }, confirmButton = { TextButton(onClick = { error = onAdd(input); if (error == null) onDismiss() }, enabled = input.isNotBlank()) { Text("Add repository") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ButlerMark(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawArc(color, 180f, 180f, true, Offset(w * .15f, h * .2f), Size(w * .7f, h * .7f))
        drawRoundRect(color, Offset(w * .08f, h * .59f), Size(w * .84f, h * .06f))
        drawCircle(color, w * .045f, Offset(w * .5f, h * .15f))
        drawPath(Path().apply { moveTo(w * .32f, h * .76f); lineTo(w * .5f, h * .83f); lineTo(w * .32f, h * .9f); close()
            moveTo(w * .68f, h * .76f); lineTo(w * .5f, h * .83f); lineTo(w * .68f, h * .9f); close() }, Gold)
    }
}
