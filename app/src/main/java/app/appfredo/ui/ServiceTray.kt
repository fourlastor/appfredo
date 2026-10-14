package app.appfredo.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.appfredo.R
import app.appfredo.ui.art.AppfredoIcons
import app.appfredo.ui.art.ServingTray
import app.appfredo.ui.art.rememberPeelPainter
import app.appfredo.ui.theme.AppfredoMotion
import app.appfredo.ui.theme.AppfredoTheme

/**
 * The one download or installation in hand, docked at the bottom so the next step stays under the thumb
 * however far the library has scrolled. [permissionNeeded] swaps the install step for the one-time trip to
 * Android's settings.
 */
@Composable
internal fun ServiceTray(
    model: TrayModel,
    permissionNeeded: Boolean,
    canOpen: (String) -> Boolean,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowPrompt: () -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppfredoTheme.colors
    val stage = model.stage
    val name = model.appName ?: stringResource(R.string.tray_app_fallback)
    val failed = stage == null || (stage is TrayStage.Finished && !stage.success)
    val title: String
    val detail: String?
    when {
        stage is TrayStage.Working -> { title = stage.title; detail = stage.detail ?: model.appName }
        stage is TrayStage.Served && permissionNeeded -> {
            title = stringResource(R.string.tray_permission_title); detail = stringResource(R.string.tray_permission_body)
        }
        stage is TrayStage.Served -> {
            title = stage.handingOver ?: stringResource(R.string.tray_ready_title, name)
            detail = stringResource(if (stage.apk.usedDelta) R.string.tray_ready_patch else R.string.tray_ready_full, stage.apk.manifest.versionName)
        }
        stage is TrayStage.Confirming -> {
            title = stringResource(R.string.tray_confirm_title); detail = stringResource(R.string.tray_confirm_body)
        }
        stage is TrayStage.Finished && stage.success -> {
            title = stage.app?.let { stringResource(R.string.tray_done_title, it.name) } ?: stage.message
            detail = stage.app?.let { stringResource(R.string.tray_done_body) }
        }
        else -> {
            title = stringResource(R.string.tray_trouble_title)
            detail = (stage as? TrayStage.Finished)?.message ?: model.trouble
        }
    }
    // Something that went wrong beside a stage that is still usable, such as a cancelled prompt.
    val aside = if (stage is TrayStage.Served) model.trouble ?: stage.note else model.trouble.takeIf { stage != null && !failed }

    val handingOver = stage is TrayStage.Served && stage.handingOver != null
    // The stages differ in height; the tray grows and settles between them instead of jumping.
    Surface(
        modifier.fillMaxWidth().animateContentSize(AppfredoMotion.steady.defaultSpatialSpec()),
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), colors.tray, colors.onTray,
    ) {
        Column(
            // The keyboard is left out on purpose: the tray stays docked beneath it rather than riding up on it.
            Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Portrait(covered = stage is TrayStage.Working, failed)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.titleLarge)
                    detail?.let { Text(it, color = colors.onTrayMuted, style = MaterialTheme.typography.bodySmall) }
                }
                if (stage !is TrayStage.Working) {
                    IconButton(onClick = onDismiss, enabled = !handingOver) { Icon(AppfredoIcons.Close, stringResource(R.string.tray_dismiss)) }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
            aside?.let { Text(it, Modifier.padding(end = 8.dp), colors.accent, style = MaterialTheme.typography.bodySmall) }
            when (stage) {
                is TrayStage.Working -> Progress(stage.fraction, Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp))
                is TrayStage.Served ->
                    if (permissionNeeded) TrayButton(stringResource(R.string.tray_permission_action), onClick = onOpenSettings)
                    else TrayButton(stringResource(R.string.tray_install), enabled = !handingOver, onClick = onInstall)
                TrayStage.Confirming -> TrayButton(stringResource(R.string.tray_confirm_action), onClick = onShowPrompt)
                is TrayStage.Finished -> {
                    val packageName = stage.app?.packageName?.takeIf { stage.success }
                    if (packageName != null && remember(packageName) { canOpen(packageName) }) {
                        TrayButton(stringResource(R.string.action_open)) { onOpen(packageName) }
                    }
                }
                null -> Unit
            }
        }
    }
}

/** The tray's own little scene: covered while the butler works, revealed once it is served, a peel if he slipped. */
@Composable
private fun Portrait(covered: Boolean, failed: Boolean) {
    val lift by animateFloatAsState(if (covered) 0f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "cloche")
    Box(Modifier.size(72.dp).clip(MaterialTheme.shapes.large).background(AppfredoTheme.colors.spotlight), Alignment.Center) {
        if (failed) Image(rememberPeelPainter(), null, Modifier.padding(10.dp))
        else ServingTray(reveal = { lift }, Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp))
    }
}

@Composable
private fun Progress(fraction: Float?, modifier: Modifier = Modifier) {
    val colors = AppfredoTheme.colors
    val track = colors.onTray.copy(alpha = 0.2f)
    if (fraction == null) {
        LinearWavyProgressIndicator(modifier, colors.accent, track)
    } else {
        val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), ProgressIndicatorDefaults.ProgressAnimationSpec, label = "progress")
        LinearWavyProgressIndicator({ shown }, modifier, colors.accent, track)
    }
}

@Composable
private fun TrayButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = AppfredoTheme.colors
    Button(
        onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().padding(end = 8.dp), enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent, contentColor = colors.onAccent,
            disabledContainerColor = colors.onTray.copy(alpha = 0.12f), disabledContentColor = colors.onTray.copy(alpha = 0.38f),
        ),
    ) { Text(label) }
}
