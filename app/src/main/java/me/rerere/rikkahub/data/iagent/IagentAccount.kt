package me.rerere.rikkahub.data.iagent

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
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
    val key: String,
    val status: String,
    @SerialName("group_id") val groupId: Long? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val quota: Double = 0.0,
    @SerialName("quota_used") val quotaUsed: Double = 0.0,
) {
    fun usable() = status == "active" && key.isNotBlank() &&
        (quota <= 0 || quotaUsed < quota) &&
        (expiresAt.isNullOrBlank() ||
            runCatching { Instant.parse(expiresAt).isAfter(Instant.now()) }.getOrDefault(false))
}

@Serializable
private data class KeyPage(val items: List<IagentKey>, val pages: Int = 1, val total: Int = 0)

internal data class IagentKeyRef(val user: Long, val group: Long, val media: Boolean) {
    fun encode() = "iagent-managed:$user:$group:" + if (media) "media" else "chat"
    val workspaceName get() = if (media) "创意工坊" else "聊天"

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
    private val keys = mutableMapOf<IagentKeyRef, IagentKey>()

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

    internal suspend fun catalog(): IagentCatalog =
        decode(request("/site-api/models?include_group_pricing=1", envelope = false))

    internal suspend fun siteBilling(): IagentSiteBilling =
        decode<IagentSiteConfig>(request("/site-api/config", envelope = false)).billing

    internal suspend fun groups(owner: Long): List<IagentGroup> =
        decode(authenticated(owner, "/api/v1/groups/available"))

    suspend fun refreshProfile(owner: Long) = withContext(Dispatchers.IO) {
        val profile = decode<IagentUser>(authenticated(owner, "/api/v1/auth/me"))
        check(profile.id == owner)
        sessionLock.withLock {
            val current = requireSession(owner)
            setSession(current.copy(user = profile))
        }
    }

    internal suspend fun key(ref: IagentKeyRef): String = keyLock.withLock {
        val owner = requireSession(ref.user)
        keys[ref]?.takeIf { it.usable() }?.let { return@withLock it.key }
        val savedId = profile.getLong(ref.encode(), 0)
        val entry = if (savedId > 0) {
            decode<IagentKey>(authenticated(owner.user.id, "/api/v1/keys/$savedId"))
        } else {
            val matches = workspaceKeys(ref)
            matches.firstOrNull(IagentKey::usable) ?: if (matches.isEmpty()) {
                decode<IagentKey>(authenticated(ref.user, "/api/v1/keys", buildJsonObject {
                    put("name", ref.workspaceName)
                    put("group_id", ref.group)
                }))
            } else {
                throw IagentException(403, "分组令牌不可用，请在 iAgent 网站检查状态、有效期和额度。")
            }
        }
        requireSession(ref.user)
        if (entry.groupId != ref.group || !entry.usable()) {
            throw IagentException(403, "分组令牌不可用，请在 iAgent 网站检查状态、有效期和额度。")
        }
        check(profile.edit().putLong(ref.encode(), entry.id).commit()) { "Could not save iAgent key reference" }
        keys[ref] = entry
        entry.key
    }

    private suspend fun workspaceKeys(ref: IagentKeyRef): List<IagentKey> {
        val matches = mutableListOf<IagentKey>()
        var page = 1
        do {
            val result = decode<KeyPage>(authenticated(ref.user, "/api/v1/keys?page=$page&page_size=100"))
            matches += result.items.filter { it.groupId == ref.group && it.name == ref.workspaceName }
            if (result.items.isEmpty()) break
            page++
        } while (page <= maxOf(result.pages, (result.total + 99) / 100))
        return matches
    }

    internal suspend fun authenticated(owner: Long, path: String, body: JsonObject? = null): JsonElement {
        val access = accessToken(owner)
        try {
            return request(path, access, body).also { requireSession(owner) }
        } catch (error: IagentException) {
            if (error.status != 401) throw error
            return request(path, accessToken(owner, access), body).also { requireSession(owner) }
        }
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
    ): JsonElement = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(IAGENT_ORIGIN + path)
            .header("Accept", "application/json")
            .header("Accept-Language", Locale.getDefault().toLanguageTag())
        token?.let { builder.header("Authorization", "Bearer $it") }
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
