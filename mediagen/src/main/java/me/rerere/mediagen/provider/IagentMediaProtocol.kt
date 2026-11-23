package me.rerere.mediagen.provider

import kotlinx.serialization.Serializable
import me.rerere.mediagen.model.MediaKind

fun iagentGptImageGeneration(model: String): Boolean =
    Regex("""^gpt-image-2(?:\.\d+)?(?:-|$)""", RegexOption.IGNORE_CASE).containsMatchIn(model.substringAfterLast('/'))

fun iagentGptImageAnimatedFormats(model: String): Boolean =
    Regex("""^gpt-image-2\.6(?:-|$)""", RegexOption.IGNORE_CASE).containsMatchIn(model.substringAfterLast('/'))

@Serializable
enum class IagentMediaProtocol(val kind: MediaKind, val label: String) {
    OPENAI_IMAGE(MediaKind.IMAGE, "Images"),
    GEMINI_IMAGE(MediaKind.IMAGE, "Gemini Image"),
    GROK_IMAGE(MediaKind.IMAGE, "Grok Imagine"),
    GROK_EDIT(MediaKind.IMAGE, "Grok Imagine Edit"),
    GROK_CHAT_IMAGE(MediaKind.IMAGE, "Grok Chat Image"),
    WAN(MediaKind.VIDEO, "Wan"),
    VIDEO_LT(MediaKind.VIDEO, "video-lt"),
    VIDEO_LT_FRAMES(MediaKind.VIDEO, "video-lt"),
    VIDEO_LT_H3(MediaKind.VIDEO, "MiniMax H3"),
    SEEDANCE(MediaKind.VIDEO, "Seedance"),
    GROK_VIDEO(MediaKind.VIDEO, "Grok Video"),
    GROK_VIDEO_WITH_IMAGES(MediaKind.VIDEO, "Grok Video"),
}
