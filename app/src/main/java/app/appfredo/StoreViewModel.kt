package app.appfredo

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.appfredo.data.*
import app.appfredo.install.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StoreState(
    val repositories: List<TrackedRepo> = emptyList(), val installed: Map<String, InstalledApp?> = emptyMap(),
    val refreshing: Boolean = false, val activeRepo: String? = null, val transfer: Transfer? = null,
    val ready: PreparedApk? = null, val message: String? = null, val installResult: InstallResult? = null,
    val served: Manifest? = null,
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RepositoryStore(application)
    private val client = GitHubClient()
    private val engine = UpdateEngine(application, client)
    private val installer = Installer(application)
    private val mutableState = MutableStateFlow(StoreState(repositories = store.load()))
    val state = mutableState.asStateFlow()
    private var confirmationOwed = false

    init {
        refreshInstalled()
        viewModelScope.launch {
            (application as AppfredoApplication).installResult.collect { result ->
                mutableState.update { it.copy(installResult = result) }
                if (result?.success == true) {
                    val apk = state.value.ready
                    apk?.let { withContext(Dispatchers.IO) { engine.prune(it.manifest.packageName, it.manifest.apk.sha256) } }
                    mutableState.update { it.copy(ready = null, served = apk?.manifest ?: it.served) }
                    refreshInstalled()
                }
            }
        }
    }

    /** What is wrong with [input] as a repository to add, or null if there is nothing. */
    fun vet(input: String): String? {
        val repo = try { RepoId.parse(input) } catch (e: Exception) { return e.message ?: "That doesn't look like a repository." }
        return if (state.value.repositories.any { it.id == repo }) "Already in your library. No need to send for it twice." else null
    }

    fun add(input: String): String? {
        vet(input)?.let { return it }
        mutableState.update { it.copy(repositories = it.repositories + TrackedRepo(RepoId.parse(input))) }
        save()
        refresh()
        return null
    }

    fun remove(repo: RepoId) {
        if (state.value.activeRepo == repo.slug) return
        mutableState.update { it.copy(repositories = it.repositories.filterNot { item -> item.id == repo }) }
        save()
    }

    /** Puts back a repository that was just removed, where it was. */
    fun restore(repo: TrackedRepo, index: Int) {
        if (state.value.repositories.any { it.id == repo.id }) return
        mutableState.update { it.copy(repositories = it.repositories.toMutableList().apply { add(index.coerceIn(0, size), repo) }) }
        save()
        refreshInstalled()
    }

    fun refresh() {
        if (state.value.refreshing) return
        // Earlier complaints are dropped as the check starts, so a repository being retried shows as being checked.
        mutableState.update { it.copy(refreshing = true, message = null, repositories = it.repositories.map { repo -> repo.copy(error = null) }) }
        viewModelScope.launch {
            try {
                for (repo in state.value.repositories) {
                    val checked = withContext(Dispatchers.IO) {
                        runCatching { client.latest(repo.id) }.fold(
                            onSuccess = { release ->
                                if (repo.release != null && repo.release.manifest.packageName != release.manifest.packageName)
                                    repo.copy(error = "This repository changed its Android package. Remove and re-add it to accept the change.")
                                else repo.copy(release = release, error = null, checkedAt = System.currentTimeMillis())
                            },
                            onFailure = { repo.copy(error = it.message ?: "I couldn't reach this repository.") },
                        )
                    }
                    mutableState.update { it.copy(repositories = it.repositories.map { old -> if (old.id == repo.id) checked else old }) }
                    save()
                }
            } finally {
                mutableState.update { it.copy(refreshing = false) }
                refreshInstalled()
            }
        }
    }

    fun refreshInstalled() {
        val apps = state.value.repositories.mapNotNull { it.release?.manifest?.packageName }.associateWith { engine.installed(it) }
        mutableState.update { it.copy(installed = apps) }
    }

    fun prepare(repo: TrackedRepo) {
        if (state.value.activeRepo != null || repo.release == null) return
        (getApplication<Application>() as AppfredoApplication).installResult.value = null
        confirmationOwed = false
        mutableState.update { it.copy(activeRepo = repo.id.slug, transfer = Transfer("Rolling up my sleeves"), ready = null, message = null, installResult = null, served = null) }
        viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    engine.prepare(repo.release) { progress -> mutableState.update { it.copy(transfer = progress) } }
                }
                mutableState.update { it.copy(ready = apk) }
            } catch (e: Exception) { mutableState.update { it.copy(message = e.message ?: "I couldn't prepare this app.") } }
            finally { mutableState.update { it.copy(activeRepo = null, transfer = null) } }
        }
    }

    fun installReady() {
        val apk = state.value.ready ?: return
        if (state.value.activeRepo != null) return
        (getApplication<Application>() as AppfredoApplication).installResult.value = null
        confirmationOwed = true
        mutableState.update { it.copy(activeRepo = "installer", message = null, installResult = null, transfer = Transfer("Presenting it to Android")) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { installer.install(apk) }
            } catch (e: Exception) {
                confirmationOwed = false
                mutableState.update { it.copy(message = e.message ?: "I couldn't start the installation.") }
            }
            finally { mutableState.update { it.copy(activeRepo = null, transfer = null) } }
        }
    }

    /**
     * Android's prompt for an installation the user started here, handed out once so it opens once.
     * Installations started any other way leave the prompt to whoever started them.
     */
    fun takeConfirmation(): Intent? {
        if (!confirmationOwed) return null
        return state.value.installResult?.confirmation?.also { confirmationOwed = false }
    }

    /** Clears whatever the last download or installation left behind. A saved APK stays on disk for next time. */
    fun dismissTray() {
        if (state.value.activeRepo != null) return
        confirmationOwed = false
        (getApplication<Application>() as AppfredoApplication).installResult.value = null
        mutableState.update { it.copy(ready = null, message = null, installResult = null, served = null) }
    }

    private fun save() = store.save(state.value.repositories)
}
