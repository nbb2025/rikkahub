package me.rerere.rikkahub.data.iagent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderSetting
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.provider.IagentMediaProtocol
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.rikkahub.data.datastore.DEEPSEEK_PROVIDER_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import kotlin.uuid.Uuid

class IagentModels(
    private val account: IagentAccount,
    private val settings: SettingsStore,
) {
    private val lock = Mutex()
    private val catalogLock = Mutex()
    private val currentCatalog = MutableStateFlow<IagentCatalog?>(null)
    private val currentGroups = MutableStateFlow<IagentAvailableGroups?>(null)
    private var catalogLoadedAt = 0L
    internal val catalog = currentCatalog.asStateFlow()
    internal val availableGroups = currentGroups.asStateFlow()

    internal suspend fun loadCatalog(force: Boolean = false): IagentCatalog = catalogLock.withLock {
        val cached = currentCatalog.value
        if (cached != null && !force && System.currentTimeMillis() - catalogLoadedAt in 0..60_000L) {
            return@withLock cached
        }
        account.catalog(force).also {
            currentCatalog.value = it
            catalogLoadedAt = System.currentTimeMillis()
        }
    }

    suspend fun synchronize(owner: Long, force: Boolean = false) = lock.withLock {
        val (groups, catalogResult) = coroutineScope {
            val groups = async { account.groups(owner) }
            val catalog = async {
                try {
                    Result.success(loadCatalog(force))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
            groups.await() to catalog.await()
        }
        check(account.user.value?.id == owner)
        val allowed = groups.filter { it.status == "active" }
        val catalog = catalogResult.getOrElse { error ->
            val allowedIds = allowed.mapTo(hashSetOf()) { it.id }
            settings.update { old ->
                if (account.user.value?.id != owner) return@update old
                val media = old.mediaGenerationProviders.filterIsInstance<MediaGenerationProviderSetting.Iagent>()
                    .filter { IagentKeyRef.parse(it.keyReference)?.group in allowedIds }
                old.copy(
                    providers = old.providers.filter {
                        it.id == DEEPSEEK_PROVIDER_ID || it.iagentRef()?.group in allowedIds
                    },
                    mediaGenerationProviders = media,
                    retiredIagentMediaProviders = old.retireIagentMediaProviders(media),
                )
            }
            if (account.user.value?.id == owner) currentGroups.value = IagentAvailableGroups(owner, allowed)
            throw error
        }
        val chats = mutableListOf<ProviderSetting>()
        val media = mutableListOf<MediaGenerationProviderSetting.Iagent>()
        val activeGroups = catalog.orderGroups(
            allowed,
            IagentGroup::id,
            IagentGroup::name
        )
        val eligible = catalog.models.filterNot { isIagentReviewModel(it.id, it.name) }.groupBy { it.groupId }
        for (group in activeGroups) {
            val models = catalog.orderModels(eligible[group.id].orEmpty().distinctBy { it.id })
            val (mediaModels, otherModels) = models.partition { mediaProtocol(group.platform, it) != null }
            val chatModels = otherModels.filter { isChatModel(group, it) }.map { record ->
                val name = record.id.lowercase()
                Model(
                    modelId = record.id,
                    displayName = record.displayName,
                    id = stableId(owner, group.id, "chat", record.id),
                    contextLength = record.contextLength?.takeIf { it > 0 } ?: 128_000,
                    inputModalities = if (visionModel.containsMatchIn(
                            name
                        )) {
                        listOf(Modality.TEXT, Modality.IMAGE)
                    } else {
                        listOf(Modality.TEXT)
                    },
                    abilities = buildList {
                        if (visionModel.containsMatchIn(name)) add(ModelAbility.TOOL)
                        if (reasoningModel.containsMatchIn(name)) add(ModelAbility.REASONING)
                    },
                )
            }
            if (chatModels.isNotEmpty()) {
                val id = stableId(owner, group.id, "chat")
                val reference = IagentKeyRef(owner, group.id, media = false).encode()
                val platform = group.platform.lowercase()
                chats += if (platform == "anthropic") {
                    ProviderSetting.Claude(
                        id = id,
                        name = group.name,
                        models = chatModels,
                        apiKey = reference,
                        baseUrl = "$IAGENT_ORIGIN/v1"
                    )
                } else {
                    ProviderSetting.OpenAI(
                        id = id,
                        name = group.name,
                        models = chatModels,
                        apiKey = reference,
                        baseUrl = "$IAGENT_ORIGIN/v1",
                        useResponseApi = platform == "openai",
                    )
                }
            }
            for ((protocol, entries) in mediaModels.groupBy { checkNotNull(mediaProtocol(group.platform, it)) }) {
                media += MediaGenerationProviderSetting.Iagent(
                    id = stableId(owner, group.id, protocol.name),
                    name = "${group.name} · ${protocol.label}",
                    keyReference = IagentKeyRef(owner, group.id, media = true).encode(),
                    protocol = protocol,
                    models = entries.map {
                        MediaGenerationModel(
                            it.id,
                            protocol.kind,
                            it.displayName,
                            stableId(owner, group.id, protocol.name, it.id)
                        )
                    },
                )
            }
        }
        check(account.user.value?.id == owner)
        settings.update { old ->
            if (account.user.value?.id != owner) return@update old
            val providers = chats + old.providers.filter { it.id == DEEPSEEK_PROVIDER_ID }
            val modelIds = providers.flatMap { it.models }.map { it.id }.toSet()
            val firstModel = providers.firstNotNullOfOrNull { it.models.firstOrNull()?.id }
            val initial = old.providers.none { it.isIagentManaged() }
            old.copy(
                providers = providers,
                mediaGenerationProviders = media,
                retiredIagentMediaProviders = old.retireIagentMediaProviders(media),
                chatModelId = old.chatModelId.takeIf { it in modelIds } ?: firstModel ?: old.chatModelId,
                fastModelId = old.fastModelId.takeIf { it in modelIds } ?: firstModel ?: old.fastModelId,
                enableAutoTitle = !initial && old.enableAutoTitle,
                enableSuggestion = !initial && old.enableSuggestion,
            )
        }
        if (account.user.value?.id == owner) currentGroups.value = IagentAvailableGroups(owner, activeGroups)
    }
}

internal data class IagentAvailableGroups(val owner: Long, val groups: List<IagentGroup>)

private fun Settings.retireIagentMediaProviders(
    next: List<MediaGenerationProviderSetting.Iagent>,
): List<MediaGenerationProviderSetting.Iagent> {
    val active = next.mapTo(hashSetOf()) { it.id }
    return (mediaGenerationProviders.filterIsInstance<MediaGenerationProviderSetting.Iagent>() +
        retiredIagentMediaProviders)
        .filterNot { it.id in active }.distinctBy { it.id }.map { it.copy(models = emptyList()) }
}

private fun ProviderSetting.iagentRef(): IagentKeyRef? = (this as? ProviderSetting.ApiKey)
    ?.takeIf { it.baseUrl.trimEnd('/') == "$IAGENT_ORIGIN/v1" }
    ?.apiKey
    ?.let(IagentKeyRef::parse)

internal fun ProviderSetting.isIagentManaged(): Boolean = iagentRef() != null

private val imageModel = Regex(
    "(?:gpt[-_. ]?image|dall[-_. ]?e|imagegen|imagen|image[-_ ]generation|image[-_ ]preview|flash[-_ ]image|pro[-_ ]image|nano[-_. ]?banana|stable[-_. ]?diffusion|midjourney|flux(?:[-_. ]|$)|seedream|ideogram|qwen[-_. ]?image|grok[-_. ]?imagine)",
    RegexOption.IGNORE_CASE,
)
private val grokVideo = Regex("grok[-_. ]?imag(?:ine|en)[-_. ]?video", RegexOption.IGNORE_CASE)
private val wan = Regex("""(?:^|[\s/])wan3[._-]0-video(?:-prime)?(?=$|\s)""", RegexOption.IGNORE_CASE)
private val nonChat =
    Regex(
        """(?:embed|rerank|whisper|tts|transcri|moderation|speech|video[-_ ]generation|(?:^|[\s/_-])(?:sora|veo|jev)(?:$|[\s/_.-])|typesafe)""",
        RegexOption.IGNORE_CASE
    )
private val visionModel = Regex("gpt|claude|gemini|grok|qwen.*vl")
private val reasoningModel = Regex("gpt-[56]|o[134]-|claude|gemini|reason|thinking")

internal fun mediaProtocol(groupPlatform: String, model: IagentCatalogModel): IagentMediaProtocol? {
    val platform = groupPlatform.lowercase()
    val native = model.platform.lowercase().takeIf { it == "bailian" || it == "video-lt" } ?: platform
    val id = model.id.substringAfterLast('/').lowercase()
    val label = "$id ${model.name}"
    return when {
        native == "video-lt" -> when {
            label.contains("minimax-h3", ignoreCase = true) -> IagentMediaProtocol.VIDEO_LT_H3
            label.contains("sd-2.5", ignoreCase = true) -> IagentMediaProtocol.VIDEO_LT_FRAMES
            else -> IagentMediaProtocol.VIDEO_LT
        }
        native == "bailian" || wan.containsMatchIn(label) -> IagentMediaProtocol.WAN
        label.contains("seedance", ignoreCase = true) -> IagentMediaProtocol.SEEDANCE
        grokVideo.containsMatchIn(
            label
        ) -> if (Regex("""video[- ._]*1[._]5""", RegexOption.IGNORE_CASE).containsMatchIn(label)) {
            IagentMediaProtocol.GROK_VIDEO_WITH_IMAGES
        } else {
            IagentMediaProtocol.GROK_VIDEO
        }
        !imageModel.containsMatchIn(label) -> null
        id == "grok-imagine-edit" -> IagentMediaProtocol.GROK_EDIT
        id == "grok-imagine-image" -> IagentMediaProtocol.GROK_IMAGE
        Regex("""^grok-imagine-1\.0(?:-fast)?$""").matches(id) -> IagentMediaProtocol.GROK_CHAT_IMAGE
        platform in setOf("grok", "xai") || id.startsWith("grok") -> IagentMediaProtocol.GROK_IMAGE
        platform in setOf("gemini", "antigravity", "google") || id.startsWith("gemini-") ||
            id.startsWith("imagen") || id.startsWith("nano-banana") -> IagentMediaProtocol.GEMINI_IMAGE
        else -> IagentMediaProtocol.OPENAI_IMAGE
    }
}

private fun isChatModel(group: IagentGroup, model: IagentCatalogModel) =
    !group.claudeCodeOnly && !nonChat.containsMatchIn("${model.id} ${model.name} ${group.platform}") &&
        model.id.isNotBlank()

private fun stableId(owner: Long, group: Long, category: String, model: String = ""): Uuid =
    Uuid.parse(UUID.nameUUIDFromBytes("iagent/$owner/$group/$category/$model".encodeToByteArray()).toString())

internal class IagentRequestAuthorization(private val account: IagentAccount) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val references = authHeaders.mapNotNull { name ->
            val value = original.header(name)?.removePrefix("Bearer ") ?: return@mapNotNull null
            if (value.startsWith("iagent-managed:")) name to value else null
        }
        if (references.isEmpty()) return chain.proceed(original)
        if (!original.url.isHttps || original.url.host != "ai.iagent.dev" || original.url.port != 443) {
            throw IOException("iAgent credentials require the iAgent origin")
        }
        val ref = IagentKeyRef.parse(references.first().second)
            ?: throw IOException("Invalid iAgent credential reference")
        if (references.any { IagentKeyRef.parse(it.second) != ref }) throw IOException("Conflicting iAgent credentials")
        val key = try {
            runBlocking { account.key(ref, forSubmission = original.method == "POST") }
        } catch (error: Exception) {
            throw IOException(error.message ?: "iAgent authorization failed", error)
        }
        val outbound = original.newBuilder()
        for ((name, _) in references) {
            outbound.header(name, if (name == "Authorization") "Bearer $key" else key)
        }
        // Keep the working key out of logs and any redirect request.
        val response = chain.proceed(outbound.build())
        if (response.code == 401 || response.code == 403) {
            runBlocking { account.invalidateKey(ref) }
        }
        return response.newBuilder().request(original).build()
    }

    private companion object {
        val authHeaders = listOf("Authorization", "x-api-key", "x-goog-api-key")
    }
}
