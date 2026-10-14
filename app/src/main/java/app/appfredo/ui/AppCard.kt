package app.appfredo.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.appfredo.R
import app.appfredo.install.formatBytes
import app.appfredo.ui.art.AppfredoIcons
import app.appfredo.ui.theme.AppfredoMotion
import app.appfredo.ui.theme.AppfredoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * One app in the library. The card opens its details; the button does the one thing that app needs.
 * [enabled] is false while another download or installation is in hand, since they run one at a time.
 */
@Composable
internal fun AppCard(
    entry: AppEntry,
    enabled: Boolean,
    canOpen: Boolean,
    onDetails: () -> Unit,
    onGet: () -> Unit,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = stringResource(R.string.card_details)
    // While a look-up is shown as pending, its answer is held back whole: trouble, standing and action
    // then change in one beat rather than three.
    val waiting = rememberPlaceholder(entry.status == AppStatus.Checking)
    val settled = !waiting && entry.status != AppStatus.Checking
    Surface(
        onClick = onDetails,
        modifier = modifier.fillMaxWidth().semantics { onClick(details, null) },
        shape = MaterialTheme.shapes.extraLarge,
        color = AppfredoTheme.colors.card,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(entry, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        entry.slug, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                Icon(AppfredoIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
            }
            if (settled) entry.repo.error?.let { Trouble(it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Standing(entry, waiting, Modifier.weight(1f))
                if (settled) AppAction(entry, enabled, canOpen, onGet, onOpen, onCheck)
            }
        }
    }
}

/** What the butler found wrong, in the app's own card rather than a banner the user has to match up. */
@Composable
internal fun Trouble(message: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(AppfredoIcons.Alert, null, Modifier.size(18.dp), MaterialTheme.colorScheme.error)
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Where the app stands: the versions involved, and the smaller download when there is one. It simply changes
 * when a check comes back: that is the outcome of background work, and it should not ask for attention.
 */
@Composable
private fun Standing(entry: AppEntry, waiting: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val manifest = entry.manifest
    val quiet = MaterialTheme.typography.labelLarge
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            entry.busy -> Text(stringResource(R.string.status_busy), color = scheme.onSurfaceVariant, style = quiet)
            waiting -> Text(stringResource(R.string.status_checking), color = scheme.onSurfaceVariant, style = quiet)
            // Still inside the placeholder's delay: hold the line's height so nothing jumps when the answer lands.
            entry.status == AppStatus.Checking -> Text("", style = quiet)
            // When the look-up failed, the trouble line above has already said so.
            manifest == null -> if (entry.repo.error == null) {
                Text(stringResource(R.string.status_unchecked), color = scheme.onSurfaceVariant, style = quiet)
            }
            entry.status == AppStatus.Update && entry.installed != null -> {
                val spoken = stringResource(R.string.status_update_spoken, entry.installed.versionName, manifest.versionName)
                Surface(color = AppfredoTheme.colors.accent, contentColor = AppfredoTheme.colors.onAccent, shape = CircleShape) {
                    Row(
                        Modifier.padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp).semantics { contentDescription = spoken },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(AppfredoIcons.Banana, null, Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.status_update, entry.installed.versionName, manifest.versionName),
                            style = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                entry.patch?.let {
                    Text(
                        stringResource(R.string.delta_line, formatBytes(it.file.size), formatBytes(manifest.apk.size)),
                        Modifier.padding(top = 4.dp), color = scheme.tertiary, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            entry.status == AppStatus.Fresh && entry.installed != null -> Row(
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(AppfredoIcons.Leaf, null, Modifier.size(18.dp), scheme.tertiary)
                Text(stringResource(R.string.status_fresh, entry.installed.versionName), color = scheme.tertiary, style = quiet)
            }
            else -> Text(
                stringResource(R.string.status_available, manifest.versionName, formatBytes(manifest.apk.size)),
                color = scheme.onSurfaceVariant, style = quiet,
            )
        }
    }
}

/** The single action an app needs, shared by its card and its details. */
@Composable
internal fun AppAction(
    entry: AppEntry,
    enabled: Boolean,
    canOpen: Boolean,
    onGet: () -> Unit,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        // The tray reports the work in hand; the card only says that it is this app's.
        entry.busy -> Unit
        entry.status == AppStatus.Update || entry.status == AppStatus.Available ->
            Button(onClick = onGet, shapes = ButtonDefaults.shapes(), modifier = modifier, enabled = enabled) {
                Text(stringResource(if (entry.status == AppStatus.Update) R.string.action_update else R.string.action_get))
            }
        entry.status == AppStatus.Fresh -> if (canOpen) {
            FilledTonalButton(onClick = onOpen, shapes = ButtonDefaults.shapes(), modifier = modifier) { Text(stringResource(R.string.action_open)) }
        }
        entry.status == AppStatus.Unchecked -> OutlinedButton(onClick = onCheck, shapes = ButtonDefaults.shapes(), modifier = modifier) {
            Text(stringResource(if (entry.repo.error != null) R.string.action_retry else R.string.action_check_now))
        }
    }
}

private val appIcons = LruCache<String, ImageBitmap>(48)

/**
 * The installed app's own icon, or its initial on a tinted plate when there is none to show. The plate means
 * "not installed", so an installed app never flashes one while its icon loads: it shows nothing until the icon
 * is there. Only a plate this card has actually shown fades into the icon, which is what installing looks like.
 */
@Composable
internal fun AppIcon(entry: AppEntry, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageName = entry.manifest?.packageName?.takeIf { entry.installed != null }
    val key = packageName?.let { "$it@${entry.installed?.versionCode}" }
    val cached = key?.let(appIcons::get)
    var loading by remember(key) { mutableStateOf(key != null && cached == null) }
    val icon by produceState(cached, key) {
        if (packageName != null && key != null && value == null) {
            value = withContext(Dispatchers.Default) {
                runCatching {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    Bitmap.createBitmap(IconPixels, IconPixels, Bitmap.Config.ARGB_8888).also {
                        drawable.setBounds(0, 0, IconPixels, IconPixels)
                        drawable.draw(Canvas(it))
                    }.asImageBitmap()
                }.getOrNull()
            }?.also { appIcons.put(key, it) }
            loading = false
        }
    }
    var platedBefore by remember { mutableStateOf(packageName == null) }
    if (icon == null && !loading) SideEffect { platedBefore = true }
    if (platedBefore) {
        Crossfade(icon, modifier, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "icon") { bitmap ->
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize()) else Plate(entry)
        }
    } else {
        Box(modifier) { icon?.let { Image(it, null, Modifier.fillMaxSize()) } }
    }
}

@Composable
private fun Plate(entry: AppEntry) {
    val scheme = MaterialTheme.colorScheme
    val (plate, ink) = when (entry.slug.hashCode().mod(3)) {
        0 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        1 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        else -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
    Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(plate), Alignment.Center) {
        Text(entry.name.trim().take(1).uppercase().ifEmpty { "?" }, color = ink, style = MaterialTheme.typography.titleLarge)
    }
}

private const val IconPixels = 192

/**
 * Whether to show a placeholder while [loading]. It waits before appearing, so a fast answer never flashes
 * one, and once shown it stays long enough to be read as loading rather than as a glitch.
 */
@Composable
internal fun rememberPlaceholder(loading: Boolean): Boolean {
    var shown by remember { mutableStateOf(false) }
    var shownAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(loading) {
        if (loading) {
            delay(AppfredoMotion.PlaceholderDelayMillis)
            shownAt = System.nanoTime()
            shown = true
        } else if (shown) {
            delay(AppfredoMotion.PlaceholderMinimumMillis - (System.nanoTime() - shownAt) / 1_000_000)
            shown = false
        }
    }
    return shown
}
