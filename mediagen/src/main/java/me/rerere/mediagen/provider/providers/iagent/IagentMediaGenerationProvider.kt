package me.rerere.mediagen.provider.providers.iagent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import me.rerere.mediagen.model.MediaGenerationError
import me.rerere.mediagen.model.MediaGenerationInput
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaGenerationOutput
import me.rerere.mediagen.model.MediaGenerationRequest
import me.rerere.mediagen.model.MediaGenerationStatus
import me.rerere.mediagen.model.MediaGenerationTask
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.IagentMediaProtocol
import me.rerere.mediagen.provider.MediaGenerationProvider
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.mediagen.provider.iagentGptImageAnimatedFormats
import me.rerere.mediagen.provider.iagentGptImageGeneration
import me.rerere.mediagen.provider.providers.MediaGenerationApiException
import me.rerere.mediagen.provider.providers.aliyun.AliyunMediaGenerationProvider
import me.rerere.mediagen.provider.providers.bearerRequest
import me.rerere.mediagen.provider.providers.executeJson
import me.rerere.mediagen.provider.providers.mediaGenerationJson
import me.rerere.mediagen.provider.providers.openai.OpenAIMediaGenerationProvider
import me.rerere.mediagen.provider.providers.postJson
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

class IagentSubmissionUnknown(cause: Throwable? = null) : IOException("iAgent submission result is unknown", cause)

internal class IagentMediaGenerationProvider(
    private val client: OkHttpClient,
) : MediaGenerationProvider<MediaGenerationProviderSetting.Iagent> {
    override val id = "iagent"
    private val openAI = OpenAIMediaGenerationProvider(client)
    private val aliyun = AliyunMediaGenerationProvider(client)

    override suspend fun create(
        setting: MediaGenerationProviderSetting.Iagent,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): Result<MediaGenerationTask> = runCatching {
        try {
            when {
                setting.protocol.kind == MediaKind.IMAGE -> createImage(setting, model, request)
                setting.protocol == IagentMediaProtocol.WAN -> aliyun.create(
                    setting.asAliyun(), model,
                    request.copy(
                        resolution = request.resolution?.uppercase() ?: "720P",
                        aspectRatio = request.aspectRatio ?: "adaptive",
                        durationSeconds = request.durationSeconds ?: 5,
                        generateAudio = request.generateAudio ?: true,
                        watermark = request.watermark ?: false,
                        promptEnhancement = request.promptEnhancement ?: true,
                    ),
                ).getOrThrow()
                else -> {
                    val body = iagentVideoBody(setting.protocol, model.modelId, request)
                    val response = client.executeJson(setting.request(videoCreatePath(setting.protocol)).postJson(body).build(), id)
                    parseVideo(setting.protocol, model.modelId, response)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IagentSubmissionUnknown) {
            throw error
        } catch (error: MediaGenerationApiException) {
            if (error.statusCode == 408 || error.statusCode == 429 || error.statusCode >= 500) {
                throw IagentSubmissionUnknown(error)
            }
            throw error
        } catch (error: IOException) {
            throw IagentSubmissionUnknown(error)
        } catch (error: IllegalStateException) {
            if (error.message == "Aliyun response does not contain task_id") throw IagentSubmissionUnknown(error)
            throw error
        }
    }

    override suspend fun query(
        setting: MediaGenerationProviderSetting.Iagent,
        model: MediaGenerationModel,
        taskId: String,
    ): Result<MediaGenerationTask> = runCatching {
        when {
            setting.protocol == IagentMediaProtocol.WAN -> aliyun.query(setting.asAliyun(), model, taskId).getOrThrow()
            setting.protocol.kind == MediaKind.IMAGE -> parseImageTask(model.modelId, taskId,
                client.executeJson(setting.request("site-api/image-tasks", taskId).build(), id))
            else -> parseVideo(setting.protocol, model.modelId,
                client.executeJson(setting.request(videoQueryPath(setting.protocol), taskId).build(), id), taskId)
        }
    }

    private suspend fun createImage(
        setting: MediaGenerationProviderSetting.Iagent,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MediaGenerationTask {
        val asyncEnabled = client.executeJson(
            Request.Builder().url("${setting.baseUrl}/site-api/image-config").build(), id,
        ).text("async_enabled") == "true"
        val edit = request.inputs.isNotEmpty()
        val modelName = model.modelId.substringAfterLast('/')
        val route: String
        val path: String
        val api = setting.asOpenAI()
        val requestBuilder = when (setting.protocol) {
            IagentMediaProtocol.OPENAI_IMAGE -> {
                route = if (edit) "openai-edit" else "openai-generation"
                path = if (edit) "images/edits" else "images/generations"
                val inputs = prepareOpenAIImage(modelName, request)
                if (edit) Request.Builder().post(openAI.buildEditBody(api, model, inputs))
                else Request.Builder().postJson(openAI.buildGenerationBody(api, model, inputs))
            }
            IagentMediaProtocol.GEMINI_IMAGE -> {
                route = "gemini"
                path = "v1beta/models/$modelName:generateContent"
                Request.Builder().header("x-goog-api-key", setting.apiKey).postJson(geminiImageBody(modelName, request))
            }
            IagentMediaProtocol.GROK_CHAT_IMAGE -> {
                route = "grok-chat"
                path = "chat/completions"
                Request.Builder().postJson(buildJsonObject {
                    put("model", model.modelId)
                    put("messages", buildJsonArray {
                        add(buildJsonObject { put("role", "user"); put("content", request.prompt) })
                    })
                    put("image_config", buildJsonObject { request.resolution?.let { put("size", it) } })
                    put("stream", asyncEnabled)
                })
            }
            else -> {
                val isEditModel = setting.protocol == IagentMediaProtocol.GROK_EDIT
                require(if (isEditModel) request.inputs.size == 1 else !edit) {
                    "Grok Imagine Edit requires one reference image"
                }
                route = if (isEditModel) "grok-edit" else "grok-generation"
                path = if (isEditModel) "images/edits" else "images/generations"
                Request.Builder().postJson(buildJsonObject {
                    put("model", model.modelId)
                    put("prompt", request.prompt)
                    put("n", 1)
                    request.resolution?.let { put("size", it) }
                    if (isEditModel) {
                        val image = request.inputs.single() as MediaGenerationInput.Image
                        put("image", buildJsonObject { put("image_url", imagePayload(image.url)) })
                    }
                })
            }
        }
        val url = when {
            asyncEnabled && setting.protocol == IagentMediaProtocol.OPENAI_IMAGE -> "$path/async"
            asyncEnabled -> "site-api/image-tasks"
            else -> path
        }
        val response = client.executeJson(
            requestBuilder.url("${setting.baseUrl}/$url")
                .header("Authorization", "Bearer ${setting.apiKey}")
                .header("X-Iagent-Image-Route", route)
                .header("X-Iagent-Image-Model", modelName)
                .build(), id,
        )
        if (asyncEnabled) {
            val taskId = response.text("task_id") ?: response.text("id") ?: throw IagentSubmissionUnknown()
            val pollUrl = response.text("poll_url")
            if (pollUrl != null) {
                val expected = setting.request("site-api/image-tasks", taskId).build().url
                if (setting.baseUrl.toHttpUrl().resolve(pollUrl) != expected) throw IagentSubmissionUnknown()
            }
            return parseImageTask(model.modelId, taskId, response)
        }
        val output = imageOutputs(response)
        check(output.isNotEmpty()) { "iAgent returned no image" }
        return MediaGenerationTask(Uuid.random().toString(), id, model.modelId, MediaGenerationStatus.SUCCEEDED, output)
    }

    private fun parseImageTask(model: String, taskId: String, response: JsonObject): MediaGenerationTask {
        val status = iagentStatus(response.text("status"))
        val results = if (status == MediaGenerationStatus.SUCCEEDED) {
            val raw = response["result"]
            val payload = raw as? JsonObject ?: (raw as? JsonPrimitive)?.contentOrNull?.let {
                runCatching { mediaGenerationJson.parseToJsonElement(it) as? JsonObject }.getOrNull()
            }
            val body = payload?.let(::imageOutputs).orEmpty()
            val url = response.text("image_url")?.let { imageOutput(it, "image/png") }
            (listOfNotNull(url) + body).distinctBy { it.url ?: it.data?.contentHashCode() }
        } else emptyList()
        return MediaGenerationTask(
            taskId, id, model, status, results,
            failure(response.text("error", "message") ?: response.text("error") ?: response.text("message")),
        )
    }

    private fun parseVideo(protocol: IagentMediaProtocol, model: String, response: JsonObject, knownId: String? = null): MediaGenerationTask {
        val failed = response.text("success") == "false"
        val reason = response.text("data", "fail_reason") ?: response.text("fail_reason") ?: response.text("error", "message")
            ?: response.text("error") ?: response.text("message")
        if (failed && knownId == null) throw MediaGenerationApiException(id, 400, null, reason ?: "iAgent rejected the video request")
        val taskId = when {
            protocol.isVideoLt -> response.text("data", "id") ?: response.text("id")
            protocol == IagentMediaProtocol.SEEDANCE -> response.text("id") ?: response.text("data", "id")
            else -> response.text("request_id") ?: response.text("id") ?: response.text("data", "request_id") ?: response.text("data", "id")
        } ?: knownId ?: throw IagentSubmissionUnknown()
        check(knownId == null || taskId == knownId) { "iAgent returned a different video task ID" }
        val status = if (failed) MediaGenerationStatus.FAILED else
            iagentStatus(response.text("data", "status") ?: response.text("status") ?: response.text("video", "status"))
        val url = response.text("data", "video_url") ?: response.text("data", "result_url") ?:
            response.text("content", "video_url") ?: response.text("data", "content", "video_url") ?:
            response.text("video", "url") ?: response.text("video_url") ?: response.text("result_url") ?: response.text("url")
        val output = if (status == MediaGenerationStatus.SUCCEEDED) {
            val source = url ?: if (protocol == IagentMediaProtocol.GROK_VIDEO || protocol == IagentMediaProtocol.GROK_VIDEO_WITH_IMAGES) {
                MediaGenerationProviderSetting.Iagent.ORIGIN.toHttpUrl().newBuilder()
                    .addPathSegments("v1/videos").addPathSegment(taskId).addPathSegment("content").build().toString()
            } else null
            source?.let { listOf(MediaGenerationOutput(url = it, mimeType = "video/mp4")) }.orEmpty()
        } else emptyList()
        return MediaGenerationTask(taskId, id, model, status, output, failure(reason))
    }

    private fun failure(message: String?) = message?.let { MediaGenerationError(message = it) }

    private fun MediaGenerationProviderSetting.Iagent.asOpenAI() =
        MediaGenerationProviderSetting.OpenAI(apiKey = apiKey, baseUrl = "$baseUrl/v1", models = models)

    private fun MediaGenerationProviderSetting.Iagent.asAliyun() =
        MediaGenerationProviderSetting.Aliyun(apiKey = apiKey, baseUrl = "$baseUrl/api/v1", models = models)

    private fun MediaGenerationProviderSetting.Iagent.request(path: String, taskId: String? = null): Request.Builder {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments(path)
        taskId?.let(url::addPathSegment)
        return bearerRequest(url.build().toString(), apiKey)
    }
}

private fun prepareOpenAIImage(model: String, request: MediaGenerationRequest): MediaGenerationRequest {
    if (!iagentGptImageGeneration(model)) return request.copy(extraParameters = JsonObject(emptyMap()))
    val options = request.extraParameters
    val animated = iagentGptImageAnimatedFormats(model)
    val format = options.text("output_format").takeIf { animated || it !in setOf("gif", "svg") } ?: "png"
    require(format != "svg" || request.inputs.isEmpty() && (request.count ?: 1) == 1) {
        "SVG 仅支持单张文生图。"
    }
    return request.copy(extraParameters = buildJsonObject {
        put("output_format", format)
        put("quality", options.text("quality") ?: "auto")
        put("moderation", "auto")
        if (animated) options.text("complexity")?.let { put("complexity", it) }
        if (format == "gif") {
            val frames = options.text("frame_count")?.toIntOrNull() ?: 9
            val interval = options.text("frame_duration_ms")?.toIntOrNull() ?: 200
            require(frames in 2..16 && interval in 20..2_000) { "GIF 帧数为 2–16，帧间隔为 20–2000 毫秒。" }
            put("frame_count", frames)
            put("frame_duration_ms", ((interval + 5) / 10) * 10)
        }
    })
}

private fun geminiImageBody(model: String, request: MediaGenerationRequest) = buildJsonObject {
    put("contents", buildJsonArray {
        add(buildJsonObject {
            put("parts", buildJsonArray {
                add(buildJsonObject { put("text", request.prompt) })
                request.inputs.filterIsInstance<MediaGenerationInput.Image>().forEach { ref ->
                    val data = imagePayload(ref.url)
                    add(buildJsonObject {
                        put("inlineData", buildJsonObject {
                            put("mimeType", data.substringAfter("data:").substringBefore(';'))
                            put("data", data.substringAfter(','))
                        })
                    })
                }
            })
        })
    })
    put("generationConfig", buildJsonObject {
        put("responseModalities", JsonArray(listOf(JsonPrimitive("IMAGE"))))
        put("imageConfig", buildJsonObject {
            put("aspectRatio", request.aspectRatio ?: "1:1")
            if (!model.contains("2.5-flash", ignoreCase = true)) {
                request.resolution?.takeUnless { it.equals("auto", ignoreCase = true) }?.let { put("imageSize", it) }
            }
        })
    })
}

internal fun imagePayload(path: String): String {
    val file = File(path.removePrefix("file://"))
    require(file.isFile && file.length() <= 20_000_000) { "Reference image must be a local file up to 20 MB" }
    val mime = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> throw IllegalArgumentException("Unsupported reference image type")
    }
    return "data:$mime;base64,${Base64.encode(file.readBytes())}"
}

private fun imageOutputs(root: JsonObject): List<MediaGenerationOutput> = buildList {
    for (element in root["data"] as? JsonArray ?: emptyList()) {
        val item = element as? JsonObject ?: continue
        val format = item.text("output_format") ?: root.text("output_format") ?: "png"
        val mime = when (format.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            else -> "image/$format"
        }
        val value = item.text("b64_json")?.let { if (it.startsWith("data:")) it else "data:$mime;base64,$it" }
            ?: item.text("url")
        value?.let { imageOutput(it, mime) }?.let(::add)
    }
    for (candidate in root["candidates"] as? JsonArray ?: emptyList()) {
        val parts = (candidate as? JsonObject)?.get("content") as? JsonObject ?: continue
        for (value in parts["parts"] as? JsonArray ?: emptyList()) {
            val part = value as? JsonObject ?: continue
            val inline = (part["inlineData"] ?: part["inline_data"]) as? JsonObject ?: continue
            val data = inline.text("data") ?: continue
            val mime = inline.text("mimeType") ?: inline.text("mime_type") ?: "image/png"
            imageOutput("data:$mime;base64,$data", mime)?.let(::add)
        }
    }
    for (choice in root["choices"] as? JsonArray ?: emptyList()) {
        val fields = choice as? JsonObject ?: continue
        val message = (fields["message"] ?: fields["delta"]) as? JsonObject ?: continue
        for (image in message["images"] as? JsonArray ?: emptyList()) {
            val value = (image as? JsonObject)?.let { it.text("image_url", "url") ?: it.text("url") }
            value?.let { imageOutput(it, "image/png") }?.let(::add)
        }
        message.text("content")?.let { content ->
            markdownImage.findAll(content).forEach { imageOutput(it.groupValues[1], "image/png")?.let(::add) }
        }
    }
    val events = root["events"] as? JsonArray ?: JsonArray(emptyList())
    for (event in events.filterIsInstance<JsonObject>()) addAll(imageOutputs(event))
    val streamedContent = events.filterIsInstance<JsonObject>().flatMap {
        (it["choices"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    }.mapNotNull { it.text("delta", "content") }.joinToString("")
    markdownImage.findAll(streamedContent).forEach { imageOutput(it.groupValues[1], "image/png")?.let(::add) }
}

private val markdownImage = Regex("""!\[[^\]]*]\((https?://[^\s)]+|data:image/[^\s)]+)\)""")

private fun imageOutput(value: String, mime: String): MediaGenerationOutput? = when {
    value.startsWith("data:image/") && value.contains(";base64,") -> runCatching {
        MediaGenerationOutput(data = Base64.decode(value.substringAfter(',')), mimeType = value.substringAfter("data:").substringBefore(';'))
    }.getOrNull()
    value.startsWith("https://") || value.startsWith("http://") ->
        MediaGenerationOutput(url = value, mimeType = mime)
    else -> null
}

internal fun JsonObject.text(vararg path: String): String? {
    var value: JsonElement = this
    for (field in path) value = (value as? JsonObject)?.get(field) ?: return null
    return (value as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
}

internal fun iagentStatus(raw: String?): MediaGenerationStatus = when (raw?.lowercase()) {
    "queued", "pending", "submitted" -> MediaGenerationStatus.QUEUED
    "processing", "running", "in_progress" -> MediaGenerationStatus.RUNNING
    "succeeded", "success", "completed", "complete", "done" -> MediaGenerationStatus.SUCCEEDED
    "failed", "error" -> MediaGenerationStatus.FAILED
    "canceled", "cancelled", "deleted" -> MediaGenerationStatus.CANCELLED
    "expired" -> MediaGenerationStatus.EXPIRED
    else -> MediaGenerationStatus.UNKNOWN
}
