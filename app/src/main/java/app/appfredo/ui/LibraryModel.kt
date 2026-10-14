package app.appfredo.ui

import app.appfredo.StoreState
import app.appfredo.data.Delta
import app.appfredo.data.InstalledApp
import app.appfredo.data.Manifest
import app.appfredo.data.TrackedRepo
import app.appfredo.data.chooseDelta
import app.appfredo.install.PreparedApk

// What the screens show, worked out from StoreState with no Android in sight so it can be tested on the JVM.

enum class AppStatus {
    /** Being looked up for the first time. */
    Checking,

    /** No release is known: never checked, or the check failed. */
    Unchecked,

    /** A release is known and the app is not installed. */
    Available,

    /** A newer version than the installed one is on offer. */
    Update,

    /** Installed and current. */
    Fresh,
}

data class AppEntry(
    val repo: TrackedRepo,
    val installed: InstalledApp?,
    val status: AppStatus,
    /** The smaller download on offer for this update, if there is one. */
    val patch: Delta?,
    /** This app is the one being downloaded or installed right now. */
    val busy: Boolean,
) {
    val manifest: Manifest? get() = repo.release?.manifest
    val name: String get() = manifest?.name ?: repo.id.name
    val slug: String get() = repo.id.slug
}

fun StoreState.entries(): List<AppEntry> = repositories.map { repo ->
    val manifest = repo.release?.manifest
    val current = manifest?.let { installed[it.packageName] }
    val status = when {
        manifest == null -> if (refreshing && repo.error == null) AppStatus.Checking else AppStatus.Unchecked
        current == null -> AppStatus.Available
        current.versionCode < manifest.versionCode -> AppStatus.Update
        else -> AppStatus.Fresh
    }
    AppEntry(
        repo, current, status,
        patch = manifest?.let { chooseDelta(it, current) },
        // While Android installs, activeRepo names the installer, so the app is recognised by its package.
        busy = activeRepo == repo.id.slug ||
            (activeRepo != null && manifest != null && ready?.manifest?.packageName == manifest.packageName),
    )
}

fun List<AppEntry>.matching(query: String, updatesOnly: Boolean): List<AppEntry> = filter {
    (!updatesOnly || it.status == AppStatus.Update) && (it.slug.contains(query, true) || it.name.contains(query, true))
}

data class Summary(
    val apps: Int,
    val updates: Int,
    val installed: Int,
    val troubled: Int,
    /** Being looked up for the first time, or again after a failure. */
    val checking: Int,
    val checkedAt: Long,
) {
    val mood: Mood
        get() = when {
            apps == 0 -> Mood.Empty
            updates > 0 -> Mood.Updates
            troubled > 0 -> Mood.Trouble
            checking > 0 -> Mood.Waiting
            installed == 0 -> Mood.Menu
            else -> Mood.Fresh
        }
}

/** What the butler has to report, most pressing first. */
enum class Mood { Empty, Updates, Trouble, Waiting, Menu, Fresh }

fun List<AppEntry>.summary() = Summary(
    apps = size,
    updates = count { it.status == AppStatus.Update },
    installed = count { it.installed != null },
    troubled = count { it.repo.error != null },
    checking = count { it.status == AppStatus.Checking },
    checkedAt = maxOfOrNull { it.repo.checkedAt } ?: 0,
)

/** The one download or installation in hand. */
sealed interface TrayStage {
    data class Working(val title: String, val detail: String?, val fraction: Float?) : TrayStage

    /**
     * Downloaded, verified and waiting to be installed. [note] carries how an earlier attempt ended.
     * [handingOver] is what the butler says while Android is being given the APK: that is part of serving
     * it, so the tray keeps its shape and only its words change.
     */
    data class Served(val apk: PreparedApk, val note: String?, val handingOver: String? = null) : TrayStage

    /** Android is asking the user to approve the installation. */
    data object Confirming : TrayStage

    data class Finished(val success: Boolean, val message: String, val app: Manifest?) : TrayStage
}

data class TrayModel(val stage: TrayStage?, val appName: String?, val trouble: String?)

/** Null when nothing is in hand and nothing went wrong. */
fun StoreState.tray(): TrayModel? {
    val result = installResult
    val stage = when {
        transfer != null && ready != null -> TrayStage.Served(ready, note = null, handingOver = transfer.message)
        transfer != null -> TrayStage.Working(transfer.message, transfer.detail, transfer.fraction)
        result?.confirmation != null -> TrayStage.Confirming
        ready != null -> TrayStage.Served(ready, result?.message)
        result != null -> TrayStage.Finished(result.success, result.message, served)
        else -> null
    }
    if (stage == null && message == null) return null
    val active = repositories.firstOrNull { it.id.slug == activeRepo }
    val name = ready?.manifest?.name ?: served?.name ?: active?.let { it.release?.manifest?.name ?: it.id.name }
    return TrayModel(stage, name, message)
}
