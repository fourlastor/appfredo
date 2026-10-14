package app.appfredo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.appfredo.R
import app.appfredo.StoreState
import app.appfredo.data.RepoId
import app.appfredo.data.TrackedRepo
import app.appfredo.ui.art.AppfredoIcons
import app.appfredo.ui.art.rememberBananaPainter
import app.appfredo.ui.theme.AppfredoMotion
import app.appfredo.ui.theme.AppfredoTheme
import kotlinx.coroutines.launch

/** Everything the screens can ask of the app. */
class StoreActions(
    /** What is wrong with the input as a repository to add, or null if there is nothing. */
    val vet: (String) -> String?,
    val add: (String) -> Unit,
    val remove: (RepoId) -> Unit,
    val restore: (TrackedRepo, Int) -> Unit,
    val refresh: () -> Unit,
    val prepare: (TrackedRepo) -> Unit,
    val install: () -> Unit,
    val openInstallSettings: () -> Unit,
    val showPrompt: () -> Unit,
    val dismissTray: () -> Unit,
    val canOpen: (String) -> Boolean,
    val open: (String) -> Unit,
    val viewRepository: (RepoId) -> Unit,
)

/** Cards stop growing here, so the library stays readable on a tablet. */
private val ContentWidth = 640.dp

/**
 * The whole app: the library, the tray for the download in hand, and the sheets. [permissionNeeded] is true
 * once the user has asked to install and Android has yet to allow Appfredo to.
 */
@Composable
fun AppfredoApp(state: StoreState, permissionNeeded: Boolean, version: String, actions: StoreActions) {
    val colors = AppfredoTheme.colors
    val steady = AppfredoMotion.steady
    val resources = LocalResources.current
    val direction = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val list = rememberLazyListState()
    var adding by rememberSaveable { mutableStateOf(false) }
    // What the add sheet accepted. It joins the library once the sheet has stepped aside, so it is seen to arrive.
    val accepted = remember { arrayOfNulls<String>(1) }
    // How many apps there were when one was last added, until the newcomer's card has been brought into view.
    var arrivalAfter by remember { mutableStateOf<Int?>(null) }
    var about by rememberSaveable { mutableStateOf(false) }
    var detailsOf by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var updatesOnly by rememberSaveable { mutableStateOf(false) }

    // Keyed on what the library is made of, so a download ticking along does not rebuild every card.
    val entries = remember(state.repositories, state.installed, state.refreshing, state.activeRepo, state.ready) { state.entries() }
    val summary = remember(entries) { entries.summary() }
    val visible = remember(entries, query, updatesOnly) { entries.matching(query, updatesOnly) }
    val tray = state.tray()
    val idle = state.activeRepo == null
    // While the user types, the results hold still: a list that never rests cannot be read.
    val settling = query.isEmpty()

    fun remove(entry: AppEntry) {
        val index = state.repositories.indexOf(entry.repo)
        actions.remove(entry.repo.id)
        scope.launch {
            val answer = snackbar.showSnackbar(
                resources.getString(R.string.removed_message, entry.name), resources.getString(R.string.action_undo),
                duration = SnackbarDuration.Long,
            )
            if (answer == SnackbarResult.ActionPerformed) actions.restore(entry.repo, index)
        }
    }
    LaunchedEffect(arrivalAfter, visible) {
        // A new arrival joins the end of the library, which may be off screen: go and meet it. The marker is
        // cleared only afterwards, because clearing it restarts this effect and would cut the scroll short.
        val before = arrivalAfter ?: return@LaunchedEffect
        if (entries.size > before) {
            val index = visible.indexOf(entries.last())
            if (index >= 0) list.animateScrollToItem(index + LeadingItems)
            arrivalAfter = null
        }
    }

    Scaffold(
        topBar = { BrandBar(onAdd = { adding = true }, onAbout = { about = true }) },
        bottomBar = {
            // The tray comes up from the bottom edge and leaves the way it came, without overshooting: a
            // bounce would lift it clear of the edge it is docked to. The last model is kept so it still has
            // something to show on its way out.
            val shown = remember { arrayOfNulls<TrayModel>(1) }
            if (tray != null) shown[0] = tray
            AnimatedVisibility(
                visible = tray != null,
                enter = expandVertically(steady.defaultSpatialSpec(), Alignment.Top) + fadeIn(steady.defaultEffectsSpec()),
                exit = shrinkVertically(steady.defaultSpatialSpec(), Alignment.Top) + fadeOut(steady.defaultEffectsSpec()),
            ) {
                shown[0]?.let {
                    ServiceTray(
                        it, permissionNeeded, actions.canOpen, actions.install, actions.openInstallSettings,
                        actions.showPrompt, actions.open, actions.dismissTray,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = colors.page,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = actions.refresh,
            modifier = Modifier.fillMaxSize().padding(
                start = padding.calculateStartPadding(direction), top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction),
            ),
            enabled = entries.isNotEmpty(),
        ) {
            LazyColumn(
                // With the tray up, the scaffold pads for the tray rather than the keyboard; this covers the rest.
                Modifier.fillMaxSize().consumeWindowInsets(padding).imePadding(), list,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "hero", contentType = "hero") {
                    Hero(summary, state.refreshing, actions.refresh, onAdd = { adding = true })
                }
                if (entries.isEmpty()) {
                    item(key = "how") {
                        Column(Modifier.widthIn(max = ContentWidth).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(stringResource(R.string.how_title), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                            HowItWorks()
                        }
                    }
                } else {
                    item(key = "controls") {
                        LibraryControls(
                            query, { query = it }, updatesOnly, { updatesOnly = it }, summary.updates,
                            Modifier.widthIn(max = ContentWidth),
                        )
                    }
                    if (visible.isEmpty()) {
                        item(key = "nothing") {
                            NothingHere(
                                searching = query.isNotBlank(), onClear = { query = "" },
                                if (settling) Modifier.animateItem(steady.defaultEffectsSpec(), null, steady.fastEffectsSpec()) else Modifier,
                            )
                        }
                    }
                    items(visible, key = { it.slug }, contentType = { "app" }) { entry ->
                        val packageName = entry.manifest?.packageName
                        AppCard(
                            entry,
                            enabled = idle,
                            canOpen = remember(packageName, entry.installed) { packageName != null && entry.installed != null && actions.canOpen(packageName) },
                            onDetails = { detailsOf = entry.slug },
                            onGet = { actions.prepare(entry.repo) },
                            onOpen = { packageName?.let(actions.open) },
                            onCheck = actions.refresh,
                            // Rows fade in and out where they stand, and the rest close up around them.
                            modifier = Modifier.widthIn(max = ContentWidth).padding(horizontal = 16.dp, vertical = 6.dp).then(
                                if (!settling) Modifier
                                else Modifier.animateItem(steady.defaultEffectsSpec(), steady.defaultSpatialSpec(), steady.fastEffectsSpec()),
                            ),
                        )
                    }
                    item(key = "footer") {
                        Text(
                            stringResource(R.string.footer),
                            Modifier.padding(24.dp).then(if (settling) Modifier.animateItem(null, steady.defaultSpatialSpec(), null) else Modifier),
                            MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        AddAppSheet(
            onDismiss = {
                adding = false
                accepted[0]?.let { input ->
                    accepted[0] = null
                    arrivalAfter = entries.size
                    actions.add(input)
                }
            },
            onVet = { input -> actions.vet(input).also { if (it == null) accepted[0] = input } },
        )
    }
    if (about) AboutSheet(version, onDismiss = { about = false })
    entries.firstOrNull { it.slug == detailsOf }?.let { entry ->
        val packageName = entry.manifest?.packageName
        AppDetailsSheet(
            entry,
            enabled = idle,
            canOpen = remember(packageName, entry.installed) { packageName != null && entry.installed != null && actions.canOpen(packageName) },
            onDismiss = { detailsOf = null },
            onGet = { actions.prepare(entry.repo) },
            onOpen = { packageName?.let(actions.open) },
            onCheck = actions.refresh,
            onViewRepository = { actions.viewRepository(entry.repo.id) },
            onRemove = { remove(entry) },
        )
    }
}

/** The hero and the controls sit above the first app in the list. */
private const val LeadingItems = 2

@Composable
private fun LibraryControls(
    query: String,
    onQuery: (String) -> Unit,
    updatesOnly: Boolean,
    onUpdatesOnly: (Boolean) -> Unit,
    updates: Int,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Row(
        modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(AppfredoIcons.Search, null) },
            trailingIcon = if (query.isEmpty()) null else {
                { IconButton(onClick = { onQuery("") }) { Icon(AppfredoIcons.Close, stringResource(R.string.search_clear)) } }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AppfredoTheme.colors.card, unfocusedContainerColor = AppfredoTheme.colors.card,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        FilterChip(
            selected = updatesOnly,
            onClick = { onUpdatesOnly(!updatesOnly) },
            label = { Text(if (updates > 0) stringResource(R.string.filter_updates_count, updates) else stringResource(R.string.filter_updates)) },
            leadingIcon = if (updatesOnly) {
                { Icon(AppfredoIcons.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
            } else null,
        )
    }
}

/** What the list says when the search or the filter leaves it bare. */
@Composable
private fun NothingHere(searching: Boolean, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.widthIn(max = ContentWidth).padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(rememberBananaPainter(), null, Modifier.width(112.dp).padding(bottom = 8.dp))
        Text(
            stringResource(if (searching) R.string.empty_search_title else R.string.empty_updates_title),
            Modifier.semantics { heading() }, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge,
        )
        Text(
            stringResource(if (searching) R.string.empty_search_body else R.string.empty_updates_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
        )
        if (searching) TextButton(onClick = onClear, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.search_clear)) }
    }
}
