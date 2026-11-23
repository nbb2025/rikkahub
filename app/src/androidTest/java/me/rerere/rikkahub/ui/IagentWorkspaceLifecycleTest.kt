package me.rerere.rikkahub.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.iagent.IagentAccount
import me.rerere.rikkahub.data.iagent.IagentKeyRef
import me.rerere.rikkahub.data.iagent.IagentModels
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class IagentWorkspaceLifecycleTest {
    @Test
    fun reconcilesAddedRemovedAndRestoredGroupsWithoutDuplicatingKeys() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val koin = GlobalContext.get()
        val owner = 8021L
        val base = Random.nextLong(10_000, 100_000_000) * 10
        val chat = IagentKeyRef(owner, base, media = false)
        val media = IagentKeyRef(owner, base + 1, media = true)
        val fresh = IagentKeyRef(owner, base + 2, media = false)
        val uncertain = IagentKeyRef(owner, base + 3, media = false)
        val server = KeyFixture(chat, media, fresh, uncertain)
        val store: SettingsStore = koin.get()
        val previous = store.awaitLoaded()
        val account = IagentAccount(context, koin.get<AppScope>(), server.http)
        val models = IagentModels(account, store)
        try {
            account.login("preview@iagent.example", "preview-only")
            models.synchronize(owner, force = true)
            assertEquals(listOf("Chat"), store.settingsFlow.value.providers.map { it.name })
            val mediaId = store.settingsFlow.value.mediaGenerationProviders.single().id
            assertTrue(server.creates.isEmpty())

            val twoRequests = coroutineScope {
                val first = async { account.key(chat, forSubmission = true) }
                val second = async { account.key(chat, forSubmission = true) }
                listOf(first.await(), second.await())
            }
            assertEquals(listOf("fixture-chat-key", "fixture-chat-key"), twoRequests)
            assertTrue(server.creates.isEmpty())
            assertEquals("fixture-media-key", account.key(media, forSubmission = true))

            server.active.addAll(listOf(fresh.group, uncertain.group))
            models.synchronize(owner, force = true)
            assertTrue(store.settingsFlow.value.providers.any { it.name == "New group" })
            assertTrue(server.creates.isEmpty())
            assertTrue(runCatching { account.key(fresh, forSubmission = false) }.exceptionOrNull() is IOException)
            assertTrue(server.creates.isEmpty())
            assertTrue(runCatching { account.key(fresh, forSubmission = true) }.exceptionOrNull() is IOException)
            assertEquals("fixture-new-key", account.key(fresh, forSubmission = true))
            assertEquals(listOf(fresh.idempotencyKey), server.creates.toList())

            assertTrue(runCatching { account.key(uncertain, forSubmission = true) }.exceptionOrNull() is IOException)
            assertTrue(runCatching { account.key(uncertain, forSubmission = true) }.exceptionOrNull() is IOException)
            assertEquals(listOf(fresh.idempotencyKey, uncertain.idempotencyKey), server.creates.toList())

            server.active.remove(media.group)
            server.catalogUnavailable = true
            assertTrue(runCatching { models.synchronize(owner, force = true) }.exceptionOrNull() is IOException)
            assertTrue(store.settingsFlow.value.mediaGenerationProviders.isEmpty())
            assertEquals(mediaId, store.settingsFlow.value.retiredIagentMediaProviders.single { it.id == mediaId }.id)
            assertTrue(runCatching { account.key(media, forSubmission = true) }.exceptionOrNull() is IOException)
            assertEquals("fixture-media-key", account.key(media, forSubmission = false))
            assertEquals("fixture-media-key", server.tokens.getValue(media.group).optString("key"))

            server.catalogUnavailable = false
            server.active.add(media.group)
            models.synchronize(owner, force = true)
            assertEquals(mediaId, store.settingsFlow.value.mediaGenerationProviders.single().id)
            assertTrue(store.settingsFlow.value.retiredIagentMediaProviders.none { it.id == mediaId })
            val reopened = IagentAccount(context, koin.get<AppScope>(), server.http)
            assertEquals("fixture-media-key", reopened.key(media, forSubmission = true))
            server.tokens.remove(media.group)
            reopened.invalidateKey(media)
            assertTrue(runCatching { reopened.key(media, forSubmission = true) }.exceptionOrNull() is IOException)
            assertEquals(listOf(fresh.idempotencyKey, uncertain.idempotencyKey), server.creates.toList())
        } finally {
            account.logout()
            store.update { previous }
        }
    }
}

private class KeyFixture(
    private val chat: IagentKeyRef,
    private val media: IagentKeyRef,
    private val fresh: IagentKeyRef,
    private val uncertain: IagentKeyRef,
) {
    val active = ConcurrentHashMap.newKeySet<Long>().apply { addAll(listOf(chat.group, media.group)) }
    val tokens = ConcurrentHashMap(
        mapOf(
            chat.group to token(chat, "fixture-chat-key"),
            media.group to token(media, "fixture-media-key"),
        ),
    )
    val creates = CopyOnWriteArrayList<String>()

    @Volatile var catalogUnavailable = false

    val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val path = request.url.encodedPath
        val result = when {
            path == "/api/v1/auth/login" -> 200 to envelope(
                JSONObject()
                    .put("access_token", "fixture")
                    .put("refresh_token", "fixture-refresh")
                    .put("expires_in", 86_400)
                    .put("user", JSONObject().put("id", chat.user).put("email", "preview@iagent.example"))
            )
            path == "/api/v1/auth/logout" -> 200 to envelope(JSONObject())
            path == "/api/v1/groups/available" -> 200 to envelope(JSONArray(listOf(chat, media, fresh, uncertain)
                .filter { it.group in active }.map { group(it) }))
            path == "/site-api/models" -> if (catalogUnavailable) {
                503 to JSONObject().put("message", "Catalog unavailable")
            } else {
                200 to JSONObject().put("models", JSONArray(listOf(chat, media, fresh, uncertain)
                    .filter { it.group in active }.map { model(it) }))
            }
            path == "/api/v1/keys" && request.method == "GET" -> {
                val group = request.url.queryParameter("group_id")!!.toLong()
                val page = request.url.queryParameter("page")!!.toInt()
                val saved = tokens[group]
                val rows = when {
                    saved == null -> emptyList()
                    group == chat.group && page == 1 -> listOf(JSONObject().put("id", 1)
                        .put(
                            "name",
                            "Personal"
                        ).put("status", "active").put("group_id", group).put("key", "fixture-other"))
                    else -> listOf(saved)
                }
                val count = if (group == chat.group && saved != null) 2 else rows.size
                200 to envelope(JSONObject().put("items", JSONArray(rows))
                    .put("page_size", 1).put("pages", count.coerceAtLeast(1)).put("total", count))
            }
            path == "/api/v1/keys" && request.method == "POST" -> {
                val contents = Buffer()
                request.body!!.writeTo(contents)
                val group = JSONObject(contents.readUtf8()).getLong("group_id")
                creates.add(checkNotNull(request.header("Idempotency-Key")))
                check(group == fresh.group || group == uncertain.group)
                if (group == fresh.group) tokens[group] = token(fresh, "fixture-new-key")
                503 to JSONObject().put("message", "Response lost")
            }
            path.startsWith("/api/v1/keys/") && request.method == "GET" -> {
                val key = tokens.values.firstOrNull { it.getLong("id").toString() == path.substringAfterLast('/') }
                if (key == null) 404 to JSONObject().put("message", "Not found") else 200 to envelope(key)
            }
            else -> error("Unexpected fixture request ${request.method} $path")
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(
                result.first
            ).message("Fixture").body(result.second.toString().toResponseBody("application/json".toMediaType())).build()
    }.build()

    private fun envelope(data: Any) = JSONObject().put("code", 0).put("data", data)

    private fun group(ref: IagentKeyRef) = JSONObject()
        .put("id", ref.group)
        .put(
            "name",
            when (ref) {
                chat -> "Chat"
                media -> "Studio"
                fresh -> "New group"
                else -> "Other group"
            }
        )
        .put("platform", if (ref.media) "bailian" else "openai")
        .put("status", "active")

    private fun model(ref: IagentKeyRef) = JSONObject()
        .put("id", if (ref.media) "wan3.0-video" else "gpt-6.1-sol")
        .put("platform", if (ref.media) "bailian" else "openai")
        .put("group_id", ref.group)

    private fun token(ref: IagentKeyRef, key: String) = JSONObject()
        .put("id", ref.group + 1000)
        .put("name", ref.workspaceName)
        .put("key", key)
        .put("status", "active")
        .put("group_id", ref.group)
}
