package me.rerere.rikkahub.data.datastore

import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.iagent.isIagentManaged
import me.rerere.rikkahub.data.iagent.isIagentReviewModel

/** 读盘后和每次修改后都要过一遍，保证内存里的设置始终是规整的。 */
internal fun Settings.normalized(): Settings =
    withBuiltInDefaults().withoutInvalidReferences().withValuesInRange()

internal fun Settings.normalizedForIagent(): Settings = withIagentModels().normalized()

private fun Settings.withIagentModels(): Settings {
    val providers = providers.filter { it.isIagentManaged() }.mapNotNull { provider ->
        val allowed = provider.models.filterNot { isIagentReviewModel(it.modelId, it.displayName) }.map { model ->
            if (model.providerOverwrite == null) model else model.copy(providerOverwrite = null)
        }
        if (allowed.isEmpty()) null else provider.copyProvider(enabled = true, models = allowed)
    }
    val mediaProviders = mediaGenerationProviders.filterIsInstance<MediaGenerationProviderSetting.Iagent>()
        .mapNotNull { provider ->
            val allowed = provider.models.filterNot { isIagentReviewModel(it.modelId, it.displayName.orEmpty()) }
            if (allowed.isEmpty()) null else provider.copy(models = allowed)
        }
    return copy(
        providers = providers,
        mediaGenerationProviders = mediaProviders,
        chatModelId = chatModelId.takeIf { providers.findModelById(it) != null }
            ?: providers.firstOrNull()?.models?.firstOrNull()?.id
            ?: chatModelId,
    )
}

// 内置助手和设备 TTS 仍由客户端提供。
internal fun Settings.withBuiltInDefaults(): Settings {
    val assistants = this.assistants.ifEmpty { DEFAULT_ASSISTANTS }
    val ttsProviders = this.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
    DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
        if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
            ttsProviders.add(defaultTTSProvider.copyProvider())
        }
    }
    return copy(
        assistants = assistants,
        ttsProviders = ttsProviders,
    )
}

// 去重并清理无效引用
internal fun Settings.withoutInvalidReferences(): Settings {
    val settings = this
    val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
    val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
    val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
    val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
    val validModelIds by lazy { settings.providers.flatMap { it.models }.mapTo(HashSet()) { it.id } }
    val asrProviders = settings.asrProviders.distinctBy { it.id }
    return settings.copy(
        // 选中的助手被删除后改选第一个，避免直接读 assistantId 的地方拿到失效的 ID
        assistantId = settings.assistantId
            .takeIf { id -> settings.assistants.any { it.id == id } }
            ?: settings.assistants.firstOrNull()?.id
            ?: settings.assistantId,
        providers = settings.providers.distinctBy { it.id }.map { provider ->
            provider.copyProvider(models = provider.models.distinctBy { model -> model.id })
        },
        assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
            assistant.copy(
                // 过滤掉不存在的 MCP 服务器 ID
                mcpServers = assistant.mcpServers.filter { serverId ->
                    serverId in validMcpServerIds
                }.toSet(),
                // 过滤掉不存在的模式注入 ID
                modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                    id in validModeInjectionIds
                }.toSet(),
                // 过滤掉不存在的 Lorebook ID
                lorebookIds = assistant.lorebookIds.filter { id ->
                    id in validLorebookIds
                }.toSet(),
                // 过滤掉不存在的快捷消息 ID
                quickMessageIds = assistant.quickMessageIds.filter { id ->
                    id in validQuickMessageIds
                }.toSet()
            )
        },
        ttsProviders = settings.ttsProviders.distinctBy { it.id },
        asrProviders = asrProviders,
        selectedASRProviderId = settings.selectedASRProviderId
            ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
            ?: asrProviders.firstOrNull()?.id,
        favoriteModels = settings.favoriteModels.filter { it in validModelIds }.distinct(),
        modeInjections = settings.modeInjections.distinctBy { it.id },
        lorebooks = settings.lorebooks.distinctBy { it.id },
        quickMessages = settings.quickMessages.distinctBy { it.id },
    )
}

// 把取值收回合法范围，比如删掉搜索服务后选中的下标可能越界
private fun Settings.withValuesInRange(): Settings = copy(
    searchServiceSelected = searchServiceSelected.coerceIn(0, (searchServices.size - 1).coerceAtLeast(0)),
    defaultTTSPlaybackSpeed = defaultTTSPlaybackSpeed.coerceIn(0.5f, 2.0f),
    autoCompressThreshold = autoCompressThreshold.coerceIn(AUTO_COMPRESS_THRESHOLD_RANGE),
)

/** 自动压缩触发比例的可选范围，设置页的滑块也用它 */
val AUTO_COMPRESS_THRESHOLD_RANGE = 0.5f..0.95f
