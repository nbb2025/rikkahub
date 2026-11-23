package me.rerere.mediagen.provider.providers.iagent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.mediagen.model.ImageRole
import me.rerere.mediagen.model.MediaGenerationInput
import me.rerere.mediagen.model.MediaGenerationRequest
import me.rerere.mediagen.provider.IagentMediaProtocol

internal val IagentMediaProtocol.isVideoLt: Boolean
    get() = this == IagentMediaProtocol.VIDEO_LT || this == IagentMediaProtocol.VIDEO_LT_FRAMES ||
        this == IagentMediaProtocol.VIDEO_LT_H3

internal fun videoCreatePath(protocol: IagentMediaProtocol): String = when {
    protocol.isVideoLt -> "v1/video/create"
    protocol == IagentMediaProtocol.SEEDANCE -> "v1/contents/generations/tasks"
    else -> "v1/videos/generations"
}

internal fun videoQueryPath(protocol: IagentMediaProtocol): String = when {
    protocol.isVideoLt -> "v1/video/query"
    protocol == IagentMediaProtocol.SEEDANCE -> "v1/contents/generations/tasks"
    else -> "v1/videos"
}

internal fun iagentVideoBody(
    protocol: IagentMediaProtocol,
    model: String,
    request: MediaGenerationRequest,
): JsonObject = when {
    protocol.isVideoLt -> videoLtBody(protocol, model, request)
    protocol == IagentMediaProtocol.SEEDANCE -> seedanceBody(model, request)
    else -> grokVideoBody(protocol, model, request)
}

private fun seedanceBody(model: String, request: MediaGenerationRequest) = buildJsonObject {
    val images = request.inputs.filterIsInstance<MediaGenerationInput.Image>()
    val frames = images.filter { it.role != ImageRole.REFERENCE }
    require(frames.isEmpty() || frames.size == images.size) { "首尾帧不能和参考图混用。" }
    require(frames.none { it.role == ImageRole.LAST_FRAME } || frames.any { it.role == ImageRole.FIRST_FRAME }) {
        "使用尾帧时请同时添加首帧。"
    }
    put("model", model)
    put("content", buildJsonArray {
        add(buildJsonObject {
            put("type", "text")
            put("text", request.prompt)
        })
        for (image in images) {
            add(buildJsonObject {
                put("type", "image_url")
                put("image_url", buildJsonObject { put("url", imagePayload(image.url)) })
                put("role", when (image.role) {
                    ImageRole.FIRST_FRAME -> "first_frame"
                    ImageRole.LAST_FRAME -> "last_frame"
                    ImageRole.REFERENCE -> "reference_image"
                })
            })
        }
    })
    put("duration", request.durationSeconds ?: 5)
    put("resolution", request.resolution?.lowercase() ?: "720p")
    put("ratio", request.aspectRatio ?: "16:9")
}

private fun grokVideoBody(protocol: IagentMediaProtocol, model: String, request: MediaGenerationRequest) =
    buildJsonObject {
        val references = request.inputs.filterIsInstance<MediaGenerationInput.Image>()
        require(references.isEmpty() || protocol == IagentMediaProtocol.GROK_VIDEO_WITH_IMAGES) {
            "图生视频请选择 Grok Imagine Video 1.5。"
        }
        require(references.size <= 7) { "Grok 视频最多支持 7 张参考图。" }
        put("model", model)
        put("prompt", request.prompt)
        put("duration", request.durationSeconds ?: 15)
        put("resolution", request.resolution?.lowercase() ?: "720p")
        put("aspect_ratio", request.aspectRatio ?: "16:9")
        when (references.size) {
            0 -> Unit
            1 -> put("image", buildJsonObject { put("url", imagePayload(references.single().url)) })
            else -> put("reference_images", buildJsonArray {
                references.forEach { input -> add(buildJsonObject { put("url", imagePayload(input.url)) }) }
            })
        }
    }

private fun videoLtBody(protocol: IagentMediaProtocol, model: String, request: MediaGenerationRequest): JsonObject {
    val flash = model.contains("flash", ignoreCase = true)
    val framesAllowed = protocol == IagentMediaProtocol.VIDEO_LT_FRAMES
    val h3 = protocol == IagentMediaProtocol.VIDEO_LT_H3
    val duration = request.durationSeconds ?: 5
    val range = if (framesAllowed) 4..30 else 5..15
    require(duration in range) { "当前视频模型的时长范围为 ${range.first}–${range.last} 秒。" }
    val prompt = request.prompt.orEmpty().trim()
    require(prompt.isNotEmpty()) { "请填写视频提示词。" }
    val images = request.inputs.filterIsInstance<MediaGenerationInput.Image>()
    val first = images.firstOrNull { it.role == ImageRole.FIRST_FRAME }
    val last = images.firstOrNull { it.role == ImageRole.LAST_FRAME }
    val referenceImages = images.filter { it.role == ImageRole.REFERENCE }
    val videos = request.inputs.filterIsInstance<MediaGenerationInput.Video>()
    val audio = request.inputs.filterIsInstance<MediaGenerationInput.Audio>()
    require(last == null || first != null) { "使用尾帧时请同时添加首帧。" }
    require(first == null || framesAllowed && referenceImages.isEmpty() && videos.isEmpty() && audio.isEmpty()) {
        "当前模型的首尾帧不能和参考素材混用。"
    }
    require(!h3 || referenceImages.isNotEmpty() && videos.isEmpty()) { "MiniMax H3 需要至少一张参考图。" }
    require(framesAllowed || videos.isEmpty() || referenceImages.isNotEmpty()) { "使用参考视频时请同时添加参考图。" }
    val imageLimit = if (!framesAllowed) 9 else if (flash) 10 else 30
    val clipLimit = if (!framesAllowed) 3 else if (flash) 5 else 10
    require(referenceImages.size <= imageLimit && videos.size <= clipLimit && audio.size <= clipLimit) {
        "参考素材超出当前模型的数量限制。"
    }
    val total = referenceImages.size + videos.size + audio.size
    if (total > 0) {
        val tags = materialTags.findAll(prompt).toList()
        val limits = mapOf(
            "" to total, "image" to referenceImages.size, "图片" to referenceImages.size,
            "video" to videos.size, "视频" to videos.size, "audio" to audio.size, "音频" to audio.size,
        )
        require(!(framesAllowed || model.contains("-vip", ignoreCase = true)) || tags.isNotEmpty()) {
            "请在提示词里使用 @image1、@video1 或 @audio1 引用素材。"
        }
        require(tags.all { tag ->
            val index = tag.groupValues[2].toIntOrNull() ?: 0
            index in 1..(limits[tag.groupValues[1].lowercase()] ?: 0)
        }) { "提示词引用了未添加的素材。" }
    }
    return buildJsonObject {
        put("model", model)
        put("prompt", prompt)
        put("duration", duration)
        if (first != null) {
            put("start_image_url", first.url)
            last?.let { put("end_image_url", it.url) }
        } else {
            val ratio = request.aspectRatio ?: "16:9"
            val allowed = if (h3) listOf("16:9", "9:16") else
                listOf("16:9", "9:16", "4:3", "3:4", "1:1") + if (framesAllowed) emptyList() else listOf("21:9")
            require(ratio in allowed) { "请选择当前模型支持的画幅比例。" }
            val (width, height) = ratio.split(':').map(String::toInt)
            put("orientation", if (width >= height) "landscape" else "portrait")
            if (h3) request.watermark?.let { put("watermark", it) } else put("ratio", ratio)
            if (referenceImages.isNotEmpty()) put("images", buildJsonArray { referenceImages.forEach { add(it.url) } })
            if (videos.isNotEmpty()) put("videos", buildJsonArray { videos.forEach { add(it.url) } })
            if (audio.isNotEmpty()) put("audios", buildJsonArray { audio.forEach { add(it.url) } })
        }
    }
}

private val materialTags = Regex("""@(image|video|audio|图片|视频|音频)?([0-9]+)\b""", RegexOption.IGNORE_CASE)
