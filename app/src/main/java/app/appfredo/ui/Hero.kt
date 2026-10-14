package app.appfredo.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.appfredo.R
import app.appfredo.ui.art.AppfredoIcons
import app.appfredo.ui.art.ButlerScene
import app.appfredo.ui.art.Emblem
import app.appfredo.ui.theme.AppfredoTheme
import app.appfredo.ui.theme.Banana
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.coroutines.CoroutineContext

/** How far the hero's lower edge sags in the middle. */
private val HeroDip = 22.dp

/** The pinned bar: the mark, the name, and the two things you can always do from anywhere in the library. */
@Composable
internal fun BrandBar(onAdd: () -> Unit, onAbout: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppfredoTheme.colors
    val name = stringResource(R.string.app_name)
    Row(
        modifier.fillMaxWidth().background(colors.hero)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .heightIn(min = 64.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Emblem(dome = colors.onHero, tie = Banana.Linen, Modifier.size(36.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.wordmark),
            Modifier.weight(1f).semantics { heading(); contentDescription = name },
            color = colors.onHero,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 30.sp, letterSpacing = (-1).sp),
            maxLines = 1,
        )
        FilledIconButton(
            onClick = onAdd,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.onHero, contentColor = colors.hero),
        ) { Icon(AppfredoIcons.Plus, stringResource(R.string.add_open)) }
        IconButton(onClick = onAbout) {
            Icon(AppfredoIcons.Info, stringResource(R.string.about_open), tint = colors.onHero)
        }
    }
}

/** The top of the library: what the butler has to report, the one thing to do about it, and the butler himself. */
@Composable
internal fun Hero(summary: Summary, refreshing: Boolean, onCheck: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppfredoTheme.colors
    val dip = with(LocalDensity.current) { HeroDip.toPx() }
    val shape = remember(dip) {
        GenericShape { size, _ ->
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - dip)
            quadraticTo(size.width / 2, size.height + dip, 0f, size.height - dip)
            close()
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth().clip(shape).background(colors.hero)) {
        val sceneWidth = min(maxWidth * 0.5f, 240.dp)
        HeroScene(plated = summary.apps > 0, sceneWidth, Modifier.align(Alignment.BottomEnd))
        Column(Modifier.width(maxWidth - sceneWidth + 4.dp).padding(start = 24.dp, top = 4.dp, bottom = HeroDip + 22.dp)) {
            Text(greeting().uppercase(), color = colors.onHeroMuted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                when (summary.mood) {
                    Mood.Empty -> stringResource(R.string.hero_empty_title)
                    Mood.Updates -> pluralStringResource(R.plurals.hero_updates_title, summary.updates, summary.updates)
                    Mood.Trouble -> stringResource(R.string.hero_trouble_title)
                    Mood.Waiting -> stringResource(R.string.hero_waiting_title)
                    Mood.Menu -> stringResource(R.string.hero_menu_title)
                    Mood.Fresh -> stringResource(R.string.hero_fresh_title)
                },
                // Two lines whatever there is to report, so a change of news does not move the list underneath.
                Modifier.semantics { heading() }, color = colors.onHero, minLines = 2, style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (summary.mood) {
                    Mood.Empty -> stringResource(R.string.hero_empty_body)
                    Mood.Updates -> stringResource(R.string.hero_updates_body)
                    Mood.Trouble -> pluralStringResource(R.plurals.hero_trouble_body, summary.troubled, summary.troubled)
                    Mood.Waiting -> stringResource(R.string.hero_waiting_body)
                    Mood.Menu -> pluralStringResource(R.plurals.hero_menu_body, summary.apps, summary.apps)
                    Mood.Fresh -> pluralStringResource(R.plurals.hero_fresh_body, summary.apps, summary.apps)
                },
                color = colors.onHeroMuted, minLines = 2, style = MaterialTheme.typography.bodyMedium,
            )
            if (summary.apps > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (summary.checkedAt > 0) checkedAgo(summary.checkedAt) else "",
                    color = colors.onHeroMuted, style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(16.dp))
            if (summary.apps == 0) {
                Button(onClick = onAdd, shapes = ButtonDefaults.shapes()) {
                    Icon(AppfredoIcons.Plus, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.action_add_first))
                }
            } else {
                Button(onClick = onCheck, shapes = ButtonDefaults.shapes(), enabled = !refreshing) {
                    Text(stringResource(if (refreshing) R.string.action_checking else R.string.action_check))
                }
            }
        }
    }
}

/**
 * The butler on his spotlight, holding the cloche aloft. He covers and re-presents the tray when something
 * first lands on it or the last thing leaves, and does the same if poked. There is no entrance: Android's
 * launch screen is still fading out over the first frames, so a lift on arrival would play unseen.
 */
@Composable
private fun HeroScene(plated: Boolean, width: Dp, modifier: Modifier = Modifier) {
    val spotlight = AppfredoTheme.colors.spotlight
    val flourish = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val scope = rememberCoroutineScope()
    var shownPlated by rememberSaveable { mutableStateOf(plated) }
    val lift = remember { Animatable(1f) }
    LaunchedEffect(plated) {
        if (shownPlated != plated) {
            // With animations removed each leg lands at once, so covering the tray would flash for a single
            // frame: it is skipped. The swap itself happens even if a poke interrupts the cover.
            try {
                if (coroutineContext.animates) lift.animateTo(0f, flourish)
            } finally {
                shownPlated = plated
            }
            lift.animateTo(1f, flourish)
        }
    }
    ButlerScene(
        reveal = { lift.value },
        modifier.width(width)
            .drawBehind { drawCircle(spotlight, size.width * 0.469f, Offset(size.width * 0.531f, size.height * 0.533f)) }
            // A poke is answered by the artwork itself and changes nothing, so there is no ripple over it
            // and no button for a screen reader to announce.
            .pointerInput(Unit) {
                detectTapGestures {
                    scope.launch {
                        if (!coroutineContext.animates) return@launch
                        lift.animateTo(0f, flourish)
                        lift.animateTo(1f, flourish)
                    }
                }
            },
        plated = shownPlated,
    )
}

/** False once the user has asked Android to remove animations. */
private val CoroutineContext.animates: Boolean get() = this[MotionDurationScale]?.scaleFactor != 0f

@Composable
private fun greeting(): String = stringResource(
    when (LocalTime.now().hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    },
)

@Composable
private fun checkedAgo(time: Long): String {
    val now = System.currentTimeMillis()
    if (now - time < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.hero_checked_now)
    return stringResource(R.string.hero_checked, DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString())
}
