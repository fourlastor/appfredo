package app.appfredo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RepositoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    fun load(): List<TrackedRepo> {
        val array = runCatching { JSONArray(preferences.getString("repositories", "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                TrackedRepo(RepoId.parse(item.getString("repo")),
                    item.optString("release").takeIf { it.isNotBlank() }?.let { GitHubClient.decodeCached(it) },
                    checkedAt = item.optLong("checkedAt"))
            }.getOrNull()
        }
    }
    fun save(repositories: List<TrackedRepo>) {
        val array = JSONArray()
        repositories.forEach { array.put(JSONObject().put("repo", it.id.slug)
            .put("release", it.release?.raw ?: "").put("checkedAt", it.checkedAt)) }
        preferences.edit().putString("repositories", array.toString()).apply()
    }
}
