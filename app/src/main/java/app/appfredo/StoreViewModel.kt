package app.appfredo

import android.app.Application
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
)

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val store = RepositoryStore(application)
    private val client = GitHubClient()
    private val engine = UpdateEngine(application, client)
    private val installer = Installer(application)
    private val mutableState = MutableStateFlow(StoreState(repositories = store.load()))
    val state = mutableState.asStateFlow()

    init {
        refreshInstalled()
        viewModelScope.launch {
            (application as AppfredoApplication).installResult.collect { result ->
                mutableState.update { it.copy(installResult = result) }
                if (result?.success == true) {
                    state.value.ready?.let { apk -> withContext(Dispatchers.IO) { engine.prune(apk.manifest.packageName, apk.manifest.apk.sha256) } }
                    mutableState.update { it.copy(ready = null) }
                    refreshInstalled()
                }
            }
        }
    }

    fun add(input: String): String? {
        val repo = try { RepoId.parse(input) } catch (e: Exception) { return e.message ?: "Invalid repository." }
        if (state.value.repositories.any { it.id == repo }) return "This repository is already in your library."
        mutableState.update { it.copy(repositories = it.repositories + TrackedRepo(repo)) }
        save()
        refresh()
        return null
    }

    fun remove(repo: RepoId) {
        if (state.value.activeRepo == repo.slug) return
        mutableState.update { it.copy(repositories = it.repositories.filterNot { item -> item.id == repo }) }
        save()
    }

    fun refresh() {
        if (state.value.refreshing) return
        mutableState.update { it.copy(refreshing = true, message = null) }
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
                            onFailure = { repo.copy(error = it.message ?: "Could not check this repository.") },
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
        mutableState.update { it.copy(activeRepo = repo.id.slug, transfer = Transfer("Getting everything ready"), ready = null, message = null, installResult = null) }
        viewModelScope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    engine.prepare(repo.release) { progress -> mutableState.update { it.copy(transfer = progress) } }
                }
                mutableState.update { it.copy(ready = apk) }
            } catch (e: Exception) { mutableState.update { it.copy(message = e.message ?: "Could not prepare this update.") } }
            finally { mutableState.update { it.copy(activeRepo = null, transfer = null) } }
        }
    }

    fun installReady() {
        val apk = state.value.ready ?: return
        if (state.value.activeRepo != null) return
        (getApplication<Application>() as AppfredoApplication).installResult.value = null
        mutableState.update { it.copy(activeRepo = "installer", message = null, installResult = null, transfer = Transfer("Sending APK to Android")) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { installer.install(apk) }
            } catch (e: Exception) { mutableState.update { it.copy(message = e.message ?: "Could not start installation.") } }
            finally { mutableState.update { it.copy(activeRepo = null, transfer = null) } }
        }
    }
    fun dismissMessage() { mutableState.update { it.copy(message = null) } }
    private fun save() = store.save(state.value.repositories)
}
