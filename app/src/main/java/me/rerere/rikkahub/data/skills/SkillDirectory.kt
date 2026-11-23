package me.rerere.rikkahub.data.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.common.http.await
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class SkillDirectoryEntry(
    val owner: String,
    val repository: String,
    val slug: String,
    val name: String,
) {
    val source get() = "$owner/$repository"
    val key get() = "$source/$slug"
}

internal class SkillDirectory(httpClient: OkHttpClient) {
    private val http = httpClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun list(query: String): List<SkillDirectoryEntry> {
        val url = catalogUrl.newBuilder()
        if (query.isBlank()) {
            url.addPathSegments("api/skills/all-time/0")
        } else {
            url.addPathSegments("api/search")
                .addQueryParameter("q", query)
                .addQueryParameter("limit", maximumResults.toString())
        }
        val items = readJson(url.build(), 512L * 1024).getJSONArray("skills")
        return (0 until items.length()).asSequence()
            .mapNotNull { index -> items.optJSONObject(index)?.let(::readEntry) }
            .distinctBy { it.key }
            .take(maximumResults)
            .toList()
    }

    suspend fun findSkillDirectory(entry: SkillDirectoryEntry): String? {
        val url = "https://api.github.com".toHttpUrl().newBuilder()
            .addPathSegment("repos")
            .addPathSegment(entry.owner)
            .addPathSegment(entry.repository)
            .addPathSegments("git/trees/HEAD")
            .addQueryParameter("recursive", "1")
            .build()
        val tree = readJson(url, 8L * 1024 * 1024).getJSONArray("tree")
        val directories = (0 until tree.length()).mapNotNull { index ->
            val item = tree.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("type") != "blob" || item.optString("mode") == "120000") {
                return@mapNotNull null
            }
            val path = item.optString("path")
            if (path.substringAfterLast('/') != "SKILL.md") return@mapNotNull null
            path.substringBeforeLast('/', missingDelimiterValue = "")
        }.distinct()
        val matches = directories.filter { it.substringAfterLast('/').equals(entry.slug, ignoreCase = true) }
        return when {
            matches.size == 1 -> matches.single()
            "skills/${entry.slug}" in matches -> "skills/${entry.slug}"
            matches.isEmpty() && "" in directories -> ""
            else -> null
        }
    }

    private suspend fun readJson(url: HttpUrl, maximumBytes: Long): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw IOException("${url.host} returned HTTP ${response.code}")
            val source = response.body.source()
            if (source.request(maximumBytes + 1)) throw IOException("${url.host} response is too large")
            JSONObject(source.readUtf8())
        }
    }

    private fun readEntry(item: JSONObject): SkillDirectoryEntry? {
        val parts = item.optString("source").split('/')
        if (parts.size != 2 || parts.any { !segment.matches(it) || it == "." || it == ".." }) return null
        val slug = item.optString("skillId")
        if (!segment.matches(slug) || slug == "." || slug == "..") return null
        return SkillDirectoryEntry(parts[0], parts[1], slug, item.optString("name").ifBlank { slug }.take(160))
    }

    private companion object {
        val catalogUrl = "https://skills.sh".toHttpUrl()
        val segment = Regex("[a-zA-Z0-9_.-]{1,128}")
        const val maximumResults = 20
    }
}
