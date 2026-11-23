package me.rerere.rikkahub.data.iagent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import me.rerere.common.http.await
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.files.MediaCreationFiles
import me.rerere.rikkahub.data.model.MediaCreationAssetType
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class IagentMediaTransfer(private val account: IagentAccount) {
    internal val downloadClient = account.http.newBuilder()
        .dispatcher(Dispatcher())
        .followRedirects(true)
        .addNetworkInterceptor(IagentRequestAuthorization(account))
        .build()

    suspend fun prepare(provider: MediaGenerationProviderSetting.Iagent) {
        account.key(checkNotNull(IagentKeyRef.parse(provider.keyReference)), forSubmission = true)
    }

    suspend fun upload(
        provider: MediaGenerationProviderSetting.Iagent,
        model: String,
        file: File,
        kind: MediaCreationAssetType,
    ): String = withContext(Dispatchers.IO) {
        val reference = checkNotNull(IagentKeyRef.parse(provider.keyReference))
        val key = account.key(reference, forSubmission = true)
        val upload = metadata("/v1/media/upload-url", key, buildJsonObject {
            put("model", model)
            put("name", file.name)
            put("kind", kind.name.lowercase())
            put("size", file.length())
            if (kind != MediaCreationAssetType.IMAGE) {
                MediaCreationFiles.durationSeconds(file)?.let { put("duration", it) }
            }
        })
        val uploadId = requireNotNull(upload.text("id"))
        require(uploadId.matches(Regex("[A-Za-z0-9_-]+"))) { "iAgent 返回了无效的素材 ID。" }
        val destination = upload.text("upload_url")?.toHttpUrlOrNull()
        require(
            destination != null && destination.isHttps && destination.username.isEmpty() &&
                destination.password.isEmpty()
        ) {
            "iAgent 返回了无效的素材上传地址。"
        }
        val headers = upload["headers"] as? JsonObject ?: error("iAgent did not return upload headers")
        val contentType = requireNotNull(headers.text("Content-Type") ?: headers.text("content-type")).toMediaType()
        val transfer = Request.Builder().url(destination).put(file.asRequestBody(contentType))
        headers.forEach { (name, value) ->
            if (name.equals("Content-Type", true) || name.equals("Cache-Control", true) ||
                name.equals("If-None-Match", true)) {
                (value as? JsonPrimitive)?.contentOrNull?.let { transfer.header(name, it) }
            }
        }
        account.http.newCall(transfer.build()).await().use { response ->
            // A previous PUT may have completed without its response reaching the device.
            if (!response.isSuccessful && response.code != 412) throw IOException("参考素材上传失败（HTTP ${response.code}）")
        }
        val confirmed = metadata("/v1/media/uploads/$uploadId/complete", key)
        val url = confirmed.text("url")?.toHttpUrlOrNull()
        val expiry = (confirmed["expires_at"] as? JsonPrimitive)?.longOrNull
        require(
            url != null && url.username.isEmpty() && url.password.isEmpty() &&
                (expiry == null || expiry > System.currentTimeMillis())
        ) { "参考素材链接无效或已过期。" }
        url.toString()
    }

    private suspend fun metadata(path: String, key: String, body: JsonObject? = null): JsonObject {
        val request = Request.Builder().url(IAGENT_ORIGIN + path)
            .header("Authorization", "Bearer $key")
            .post(if (body ==
                null) {
                ByteArray(0).toRequestBody()
            } else {
                body.toString().toRequestBody("application/json".toMediaType())
            })
            .build()
        return account.http.newCall(request).await().use { response ->
            val payload = runCatching { Json.parseToJsonElement(response.body.string()) as? JsonObject }.getOrNull()
            if (!response.isSuccessful || payload == null) {
                throw IagentException(
                    response.code,
                    (payload?.get("error") as? JsonObject)?.text("message") ?: payload?.text("error")
                        ?: payload?.text("message") ?: "素材接口返回 HTTP ${response.code}"
                )
            }
            payload
        }
    }

    private fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull
}
