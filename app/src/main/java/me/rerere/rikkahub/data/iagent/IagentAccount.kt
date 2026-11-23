package me.rerere.rikkahub.data.iagent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import me.rerere.common.http.await
import me.rerere.rikkahub.AppScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal const val IAGENT_ORIGIN = "https://ai.iagent.dev"
internal const val IAGENT_REPOSITORY = "https://github.com/nbb2025/rikkahub"
internal const val IAGENT_QQ_GROUP = "116098473"
private val json = Json { ignoreUnknownKeys = true }
private val jsonType = "application/json".toMediaType()

@Serializable
data class IagentUser(val id: Long, val email: String, val balance: Double? = null)

@Serializable
private data class AuthResponse(
    @SerialName("access_token") val access: String = "",
    @SerialName("refresh_token") val refresh: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("requires_2fa") val requiresTwoFactor: Boolean = false,
    @SerialName("temp_token") val challenge: String? = null,
    val user: IagentUser? = null,
)

@Serializable
private data class Session(
    val user: IagentUser,
    val access: String,
    val refresh: String?,
    val expiresAt: Long,
)

@Serializable
private data class IagentKey(
    val id: Long,
    val name: String,
    val key: String = "",
    val status: String,
    @SerialName("group_id") val groupId: Long? = null,
    val group: IagentKeyGroup? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val quota: Double = 0.0,
    @SerialName("quota_used") val quotaUsed: Double = 0.0,
) {
    val boundGroup get() = groupId ?: group?.id

    fun usable() = status == "active" && key.isNotBlank() &&
        (quota <= 0 || quotaUsed < quota) &&
        (expiresAt.isNullOrBlank() ||
            runCatching { Instant.parse(expiresAt).isAfter(Instant.now()) }.getOrDefault(false))
}

@Serializable
private data class IagentKeyGroup(val id: Long)

@Serializable
private data class KeyPage(
    val items: List<IagentKey>,
    val pages: Int = 1,
    val total: Int = 0,
    @SerialName("page_size") val pageSize: Int = 100,
)

private data class CachedIagentKey(val entry: IagentKey, val fetchedAt: Long)
private data class IagentGroupSnapshot(val owner: Long, val groups: List<IagentGroup>, val fetchedAt: Long)

internal data class IagentKeyRef(val user: Long, val group: Long, val media: Boolean) {
    fun encode() = "iagent-managed:$user:$group:" + if (media) "media" else "chat"
    val workspaceName get() = if (media) "创意工坊" else "聊天"
    val idempotencyKey get() = "iagent-workspace-v1-$user-$group-${if (media) "media" else "chat"}"

    companion object {
        fun parse(value: String): IagentKeyRef? {
            val match = Regex("""iagent-managed:(\d+):(\d+):(media|chat)""").matchEntire(value) ?: return null
            val (_, user, group, purpose) = match.groupValues
            return IagentKeyRef(
                user.toLongOrNull() ?: return null,
                group.toLongOrNull() ?: return null,
                purpose == "media"
            )
        }
    }
}

class IagentException(val status: Int, message: String) : IOException(message)
class IagentDifferentAccount : IllegalStateException()

class IagentAccount internal constructor(
    context: Context,
    private val scope: AppScope,
    httpClient: OkHttpClient? = null,
) {
    private val profile = context.getSharedPreferences("iagent-profile", Context.MODE_PRIVATE)
    private val vault = SessionVault(context)

    @Volatile private var session: Session? = vault.load()?.takeIf {
        it.user.id == profile.getLong("owner", 0)
    }
    private val currentUser = MutableStateFlow(session?.user)
    val user = currentUser.asStateFlow()
    private val sessionLock = Mutex()
    private val keyLock = Mutex()
    private val groupLock = Mutex()
    private val keys = mutableMapOf<IagentKeyRef, CachedIagentKey>()
    private var groupSnapshot: IagentGroupSnapshot? = null

    // This client does not inherit the app's request logger or reissue a charged POST on failure.
    internal val http = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == "ai.iagent.dev" && request.header("Accept-Language") == null) {
                chain.proceed(
                    request.newBuilder().header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                )
            } else {
                chain.proceed(request)
            }
        }
        .build()

    suspend fun login(email: String, password: String): String? {
        val result = decode<AuthResponse>(request("/api/v1/auth/login", body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
        }))
        if (result.requiresTwoFactor) return checkNotNull(result.challenge)
        storeLogin(result)
        return null
    }

    suspend fun confirmTwoFactor(challenge: String, code: String) {
        storeLogin(decode(request("/api/v1/auth/login/2fa", body = buildJsonObject {
            put("temp_token", challenge)
            put("totp_code", code)
        })))
    }

    private suspend fun storeLogin(result: AuthResponse) = withContext(Dispatchers.IO) {
        val user = checkNotNull(result.user) { "iAgent login returned no account" }
        check(result.access.isNotBlank()) { "iAgent login returned no access token" }
        sessionLock.withLock {
            val owner = profile.getLong("owner", 0)
            if (owner != 0L && owner != user.id) throw IagentDifferentAccount()
            check(profile.edit().putLong("owner", user.id).commit()) { "Could not save iAgent account" }
            setSession(Session(user, result.access, result.refresh, expiry(result)))
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val previous = sessionLock.withLock {
            val value = session
            setSession(null)
            value
        }
        keyLock.withLock { keys.clear() }
        groupLock.withLock { groupSnapshot = null }
        previous?.refresh?.let { token ->
            scope.async(Dispatchers.IO) {
                try {
                    request("/api/v1/auth/logout", body = buildJsonObject { put("refresh_token", token) })
                } catch (_: IOException) {
                    // The local session is already cleared.
                }
            }
        }
        Unit
    }

    internal suspend fun catalog(force: Boolean = false): IagentCatalog =
        decode(request("/site-api/models?include_group_pricing=1" + if (force) "&refresh=1" else "", envelope = false))

    internal suspend fun catalogChannels(force: Boolean): List<IagentPublicChannel> =
        decode(request(if (force) "/site-api/channels?refresh=1" else "/site-api/channels", envelope = false))

    internal suspend fun catalogPlaza(owner: Long): Map<Long, IagentPlazaGroup> {
        val result = try {
            decode<IagentPlazaPayload>(authenticated(owner, "/api/v1/model-plaza"))
        } catch (error: IagentException) {
            if (error.status == 404) return emptyMap()
            throw error
        }
        return result.groups.associate { group ->
            val pricing = decode<IagentGroupPricing>(group)
            pricing.id to IagentPlazaGroup(pricing, decode<IagentPlazaModels>(group).models)
        }
    }

    internal suspend fun catalogMetrics(owner: Long): IagentRequestMetrics =
        decode(authenticated(owner, "/api/v1/usage/model-request-metrics"))

    internal suspend fun catalogUserRates(owner: Long): Map<Long, Double> =
        decode<Map<Long, Double>?>(authenticated(owner, "/api/v1/groups/rates")).orEmpty()

    internal suspend fun siteBilling(): IagentSiteBilling =
        decode<IagentSiteConfig>(request("/site-api/config", envelope = false)).billing

    internal suspend fun groups(owner: Long, maxAgeMillis: Long = 0): List<IagentGroup> = groupLock.withLock {
        requireSession(owner)
        val cached = groupSnapshot
        if (cached != null && cached.owner == owner && maxAgeMillis > 0 &&
            android.os.SystemClock.elapsedRealtime() - cached.fetchedAt <= maxAgeMillis) {
            return@withLock cached.groups
        }
        decode<List<JsonObject>>(authenticated(owner, "/api/v1/groups/available")).map { group ->
            decode<IagentGroup>(group).copy(pricing = decode(group))
        }.also {
            groupSnapshot = IagentGroupSnapshot(owner, it, android.os.SystemClock.elapsedRealtime())
        }
    }

    suspend fun refreshProfile(owner: Long) = withContext(Dispatchers.IO) {
        val profile = decode<IagentUser>(authenticated(owner, "/api/v1/auth/me"))
        check(profile.id == owner)
        sessionLock.withLock {
            val current = requireSession(owner)
            setSession(current.copy(user = profile))
        }
    }

    internal suspend fun key(ref: IagentKeyRef, forSubmission: Boolean): String = withContext(Dispatchers.IO) {
        keyLock.withLock {
            requireSession(ref.user)
            if (forSubmission) requireActiveGroup(ref)
            keys[ref]?.takeIf {
                it.entry.usable() && android.os.SystemClock.elapsedRealtime() - it.fetchedAt < 60_000
            }?.let { return@withLock it.entry.key }

            val savedId = profile.getLong(ref.encode(), 0)
            if (savedId > 0) {
                val bound = try {
                    decode<IagentKey>(authenticated(ref.user, "/api/v1/keys/$savedId"))
                } catch (error: IagentException) {
                    if (error.status != 404 && error.status != 403) throw error
                    null
                }
                if (bound != null && bound.id == savedId && bound.boundGroup == ref.group && bound.usable()) {
                    return@withLock rememberKey(ref, bound)
                }
            }

            val matches = workspaceKeys(ref)
            matches.firstOrNull(IagentKey::usable)?.let { return@withLock rememberKey(ref, it) }
            if (matches.isNotEmpty()) {
                // Remember unusable managed keys too, so their later disappearance never causes a replacement.
                check(profile.edit().putLong(ref.encode(), matches.first().id).commit())
                throw keyUnavailable()
            }
            if (savedId > 0 || !forSubmission) throw keyUnavailable()
            val pendingKey = "${ref.encode()}:creating"
            if (profile.getBoolean(pendingKey, false)) throw keyCreationUnknown()
            requireActiveGroup(ref, force = true)
            check(profile.edit().putBoolean(pendingKey, true).commit()) { "Could not reserve the iAgent key" }
            val created = try {
                decode<IagentKey>(authenticated(
                    ref.user,
                    "/api/v1/keys",
                    buildJsonObject {
                        put("name", ref.workspaceName)
                        put("group_id", ref.group)
                    },
                    idempotencyKey = ref.idempotencyKey,
                ))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (session?.user?.id == ref.user && error is IagentException &&
                    error.status in setOf(400, 401, 403, 404)) {
                    check(profile.edit().remove(pendingKey).commit())
                    throw error
                }
                throw keyCreationUnknown(error)
            }
            if (created.boundGroup != ref.group || created.id <= 0) throw keyCreationUnknown()
            if (!created.usable()) {
                check(profile.edit().putLong(ref.encode(), created.id).remove(pendingKey).commit())
                throw keyUnavailable()
            }
            rememberKey(ref, created)
        }
    }

    internal suspend fun invalidateKey(ref: IagentKeyRef) {
        keyLock.withLock { keys.remove(ref) }
        groupLock.withLock { if (groupSnapshot?.owner == ref.user) groupSnapshot = null }
    }

    private fun rememberKey(ref: IagentKeyRef, entry: IagentKey): String {
        requireSession(ref.user)
        check(profile.edit().putLong(ref.encode(), entry.id).remove("${ref.encode()}:creating").commit()) {
            "Could not save iAgent key reference"
        }
        keys[ref] = CachedIagentKey(entry, android.os.SystemClock.elapsedRealtime())
        return entry.key
    }

    private suspend fun requireActiveGroup(ref: IagentKeyRef, force: Boolean = false) {
        val active = groups(ref.user, maxAgeMillis = if (force) 0 else 30_000)
            .any { it.id == ref.group && it.status == "active" }
        if (!active) throw IagentException(403, "iAgent 分组已下线或当前账号无权使用，请选择可用分组。")
    }

    private fun keyUnavailable() = IagentException(403, "分组令牌不可用，请在 iAgent 网站检查状态、有效期和额度。")

    private fun keyCreationUnknown(cause: Throwable? = null) =
        IOException("暂时无法确认令牌创建结果。请在 iAgent 网站核对专用令牌后再试。", cause)

    private suspend fun workspaceKeys(ref: IagentKeyRef): List<IagentKey> {
        val matches = mutableListOf<IagentKey>()
        var page = 1
        var pages = 1
        do {
            val result = decode<KeyPage>(authenticated(
                ref.user,
                "/api/v1/keys?page=$page&page_size=100&group_id=${ref.group}"
            ))
            val pageSize = result.pageSize.takeIf { it > 0 } ?: 100
            pages = maxOf(pages, result.pages, ((result.total.toLong() + pageSize - 1) / pageSize).toInt())
            if (result.items.isEmpty() && (page > 1 || result.total > 0 || pages > 1)) {
                throw IOException("iAgent 令牌列表未能完整加载，请重试。")
            }
            matches += result.items.filter { it.id > 0 && it.boundGroup == ref.group && it.name == ref.workspaceName }
            page++
        } while (page <= pages)
        return matches.distinctBy { it.id }.sortedBy { it.id }
    }

    internal suspend fun authenticated(
        owner: Long,
        path: String,
        body: JsonObject? = null,
        idempotencyKey: String? = null,
    ): JsonElement {
        val access = accessToken(owner)
        val result = try {
            request(path, access, body, idempotencyKey = idempotencyKey)
        } catch (error: IagentException) {
            if (error.status != 401) throw error
            request(path, accessToken(owner, access), body, idempotencyKey = idempotencyKey)
        }
        requireSession(owner)
        return result
    }

    private suspend fun accessToken(owner: Long, rejected: String? = null): String {
        val current = requireSession(owner)
        if (current.access != rejected &&
            current.expiresAt > System.currentTimeMillis() + 120_000) {
            return current.access
        }
        return scope.async(Dispatchers.IO) {
            sessionLock.withLock {
                val latest = requireSession(owner)
                if (latest.access != rejected && latest.expiresAt > System.currentTimeMillis() + 120_000) {
                    return@withLock latest.access
                }
                val refresh = latest.refresh
                if (refresh == null) {
                    if (rejected == null && latest.expiresAt > System.currentTimeMillis()) return@withLock latest.access
                    setSession(null)
                    throw IagentException(401, "请重新登录 iAgent。")
                }
                val result = try {
                    decode<AuthResponse>(request("/api/v1/auth/refresh", body = buildJsonObject {
                        put("refresh_token", refresh)
                    }))
                } catch (error: IagentException) {
                    if (error.status == 401 || error.status == 403) setSession(null)
                    throw error
                }
                if (result.access.isBlank()) throw IagentException(502, "iAgent 返回了无效的登录会话。")
                val renewed = Session(latest.user, result.access, result.refresh ?: refresh, expiry(result))
                setSession(renewed)
                renewed.access
            }
        }.await()
    }

    private fun requireSession(owner: Long): Session = session?.takeIf { it.user.id == owner }
        ?: throw IagentException(401, "请重新登录 iAgent。")

    private fun setSession(value: Session?) {
        vault.save(value)
        session = value
        currentUser.value = value?.user
    }

    private fun expiry(response: AuthResponse): Long =
        System.currentTimeMillis() + (response.expiresIn.takeIf { it > 0 } ?: 3_600) * 1_000

    private suspend fun request(
        path: String,
        token: String? = null,
        body: JsonObject? = null,
        envelope: Boolean = true,
        idempotencyKey: String? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(IAGENT_ORIGIN + path)
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
        token?.let { builder.header("Authorization", "Bearer $it") }
        idempotencyKey?.let { builder.header("Idempotency-Key", it) }
        body?.let { builder.post(it.toString().toRequestBody(jsonType)) }
        http.newCall(builder.build()).await().use { response ->
            val payload = runCatching { json.parseToJsonElement(response.body.string()) }.getOrNull()
            val fields = payload as? JsonObject
            if (!response.isSuccessful || (envelope && (fields?.get("code") as? JsonPrimitive)?.content != "0")) {
                val message = (fields?.get("message") as? JsonPrimitive)?.contentOrNull
                    ?: (fields?.get("error") as? JsonPrimitive)?.contentOrNull
                    ?: "iAgent HTTP ${response.code}"
                throw IagentException(response.code, message)
            }
            (if (envelope) fields?.get("data") else payload) ?: JsonObject(emptyMap())
        }
    }

    internal inline fun <reified T> decode(value: JsonElement): T = json.decodeFromJsonElement(value)
}

private class SessionVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "iagent-session.enc"))
    private val alias = "iagent-session"

    fun load(): Session? = runCatching {
        if (!file.baseFile.exists()) return null
        val bytes = file.readFully()
        val ivSize = bytes.first().toInt()
        require(ivSize in 12..16 && bytes.size > 1 + ivSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivSize)))
        json.decodeFromString<Session>(cipher.doFinal(bytes.copyOfRange(1 + ivSize, bytes.size)).decodeToString())
    }.getOrNull()

    fun save(value: Session?) {
        if (value == null) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(json.encodeToString(Session.serializer(), value).encodeToByteArray())
        val output = file.startWrite()
        try {
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            output.write(encrypted)
            file.finishWrite(output)
        } catch (error: IOException) {
            file.failWrite(output)
            throw error
        }
    }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }
}
