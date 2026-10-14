package app.appfredo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.appfredo.R
import app.appfredo.install.formatBytes
import app.appfredo.ui.art.AppfredoIcons
import app.appfredo.ui.art.ButlerScene
import app.appfredo.ui.theme.AppfredoTheme
import kotlinx.coroutines.launch

/**
 * Asks for a repository. [onVet] returns what is wrong with the input, or null when it is accepted; the
 * sheet then steps aside, and [onDismiss] is where the caller acts on what was accepted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddAppSheet(onDismiss: () -> Unit, onVet: (String) -> String?) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val focus = remember { FocusRequester() }
    var input by rememberSaveable { mutableStateOf("") }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    fun submit() {
        problem = onVet(input)
        if (problem == null) scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.add_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.add_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; problem = null },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                label = { Text(stringResource(R.string.add_label)) },
                placeholder = { Text(stringResource(R.string.add_placeholder), fontFamily = FontFamily.Monospace) },
                trailingIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            val clip = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)
                            clip?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { input = it; problem = null }
                        }
                    }) { Icon(AppfredoIcons.Paste, stringResource(R.string.add_paste)) }
                },
                // Always present, so a problem appearing does not push the button down a line.
                supportingText = { Text(problem.orEmpty()) },
                isError = problem != null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) submit() }),
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            Button(onClick = ::submit, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth(), enabled = input.isNotBlank()) {
                Text(stringResource(R.string.add_action))
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Everything about one app: its versions, its release notes, and the door out of the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetailsSheet(
    entry: AppEntry,
    enabled: Boolean,
    canOpen: Boolean,
    onDismiss: () -> Unit,
    onGet: () -> Unit,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
    onViewRepository: () -> Unit,
    onRemove: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun closeThen(action: () -> Unit) {
        scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); action() }
    }
    val manifest = entry.manifest
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(entry, Modifier.size(64.dp))
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(entry.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        entry.slug, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
            if (manifest != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val none = stringResource(R.string.details_none)
                    Fact(stringResource(R.string.details_installed), entry.installed?.versionName ?: none, Modifier.weight(1f))
                    Fact(stringResource(R.string.details_latest), manifest.versionName, Modifier.weight(1f))
                    Fact(
                        stringResource(if (entry.patch != null) R.string.details_patch else R.string.details_download),
                        formatBytes(entry.patch?.file?.size ?: manifest.apk.size), Modifier.weight(1f),
                    )
                }
            }
            entry.repo.error?.let { Trouble(it) }
            // The sheet steps aside first, so the tray it would be covering is what the user sees next.
            AppAction(entry, enabled, canOpen, { closeThen(onGet) }, onOpen, onCheck, Modifier.fillMaxWidth())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.details_notes), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(
                        when {
                            entry.repo.release == null -> stringResource(R.string.details_no_release)
                            else -> entry.repo.release.notes.take(2500).ifBlank { stringResource(R.string.details_no_notes) }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewRepository, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.details_github))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(AppfredoIcons.External, null, Modifier.size(ButtonDefaults.IconSize))
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { closeThen(onRemove) }, shapes = ButtonDefaults.shapes(), enabled = !entry.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.details_remove)) }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Who the butler is, how he works, and what he does not do yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutSheet(version: String, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val spotlight = AppfredoTheme.colors.spotlight
    val hero = AppfredoTheme.colors.hero
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(hero), Alignment.BottomCenter) {
                ButlerScene(
                    reveal = { 1f },
                    Modifier.padding(top = 16.dp).width(220.dp)
                        .drawBehind { drawCircle(spotlight, size.width * 0.469f, Offset(size.width * 0.531f, size.height * 0.533f)) },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge)
            }
            HowItWorks()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.about_makers_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_makers_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.about_limits_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_limits_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.about_version, version), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.about_fonts), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } },
                shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.about_close)) }
        }
    }
}

/** The three promises, shown to anyone with an empty library and again in About. */
@Composable
internal fun HowItWorks(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Promise(AppfredoIcons.Download, stringResource(R.string.how_source_title), stringResource(R.string.how_source_body))
        Promise(AppfredoIcons.Refresh, stringResource(R.string.how_fresh_title), stringResource(R.string.how_fresh_body))
        Promise(AppfredoIcons.Shield, stringResource(R.string.how_rules_title), stringResource(R.string.how_rules_body))
    }
}

@Composable
private fun Promise(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.secondaryContainer), Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
