package me.rerere.rikkahub.ui.pages.iagent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.hugeicons.stroke.Search01
import me.rerere.mediagen.model.MediaKind
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.iagent.IagentAccount
import me.rerere.rikkahub.data.iagent.IagentCatalog
import me.rerere.rikkahub.data.iagent.IagentCatalogModel
import me.rerere.rikkahub.data.iagent.IagentChannelPriceKey
import me.rerere.rikkahub.data.iagent.IagentGroup
import me.rerere.rikkahub.data.iagent.IagentGroupPricing
import me.rerere.rikkahub.data.iagent.IagentModelPricing
import me.rerere.rikkahub.data.iagent.IagentModels
import me.rerere.rikkahub.data.iagent.IagentPlazaGroup
import me.rerere.rikkahub.data.iagent.IagentPlazaModel
import me.rerere.rikkahub.data.iagent.IagentPriceView
import me.rerere.rikkahub.data.iagent.IagentRequestMetrics
import me.rerere.rikkahub.data.iagent.IagentRequestStatus
import me.rerere.rikkahub.data.iagent.effectiveImageRate
import me.rerere.rikkahub.data.iagent.effectiveVideoRate
import me.rerere.rikkahub.data.iagent.forModel
import me.rerere.rikkahub.data.iagent.indexPrices
import me.rerere.rikkahub.data.iagent.isIagentReviewModel
import me.rerere.rikkahub.data.iagent.match
import me.rerere.rikkahub.data.iagent.mediaProtocol
import me.rerere.rikkahub.data.iagent.orderGroups
import me.rerere.rikkahub.data.iagent.orderModels
import me.rerere.rikkahub.data.iagent.priceView
import me.rerere.rikkahub.data.iagent.pricingModelName
import me.rerere.rikkahub.data.iagent.pricingPlatform
import me.rerere.rikkahub.data.iagent.standardRate
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.compose.koinInject

@Composable
internal fun IagentCatalogPage(mediaOnly: Boolean = false) {
    val service: IagentModels = koinInject()
    val account: IagentAccount = koinInject()
    val owner by account.user.collectAsStateWithLifecycle()
    val ownerId = owner?.id
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val source by service.catalog.collectAsStateWithLifecycle()
    val available by service.availableGroups.collectAsStateWithLifecycle()
    val privateGroups = available?.takeIf { it.owner == ownerId }?.groups
    var query by rememberSaveable { mutableStateOf("") }
    var expansion by rememberSaveable { mutableStateOf(longArrayOf()) }
    var applyRate by rememberSaveable { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var channels by remember { mutableStateOf(emptyMap<IagentChannelPriceKey, List<IagentModelPricing>>()) }
    var plaza by remember(ownerId) { mutableStateOf(emptyMap<Long, IagentPlazaGroup>()) }
    var userRates by remember(ownerId) { mutableStateOf(emptyMap<Long, Double>()) }
    var metrics by remember(ownerId) { mutableStateOf<IagentRequestMetrics?>(null) }
    var loadingMetrics by remember(ownerId) { mutableStateOf(ownerId != null) }

    LaunchedEffect(ownerId, refresh) {
        loading = true
        error = null
        try {
            if (ownerId != null && (refresh > 0 || privateGroups == null)) {
                service.synchronize(ownerId, force = refresh > 0)
            } else {
                service.loadCatalog(force = refresh > 0)
            }
        } catch (caught: CancellationException) {
            throw caught
        } catch (caught: Exception) {
            error = caught.localizedMessage ?: "iAgent"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(refresh) {
        channels = catalogOrNull { account.catalogChannels(force = refresh > 0).indexPrices() }.orEmpty()
    }
    LaunchedEffect(ownerId, refresh, lifecycle) {
        if (ownerId == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                loadingMetrics = metrics == null
                coroutineScope {
                    launch {
                        plaza = catalogOrNull { account.catalogPlaza(ownerId) }.orEmpty()
                    }
                    launch {
                        userRates = catalogOrNull { account.catalogUserRates(ownerId) }.orEmpty()
                    }
                    launch {
                        metrics = catalogOrNull(timeoutMillis = 15_000) { account.catalogMetrics(ownerId) }
                        loadingMetrics = false
                    }
                }
                delay(120_000)
            }
        }
    }
    val groups = remember(source, ownerId, privateGroups, plaza, userRates, query, mediaOnly) {
        if (ownerId != null && privateGroups == null) emptyList()
        else source?.sections(privateGroups, plaza, userRates, query, mediaOnly).orEmpty()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (mediaOnly) R.string.iagent_catalog_media else R.string.iagent_catalog_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { refresh++ }, enabled = !loading && !loadingMetrics) {
                        Icon(HugeIcons.Refresh, stringResource(R.string.iagent_refresh_models))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item("search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.iagent_catalog_search)) },
                    leadingIcon = { Icon(HugeIcons.Search01, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                )
            }
            item("price-mode") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = applyRate,
                        onClick = { applyRate = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text(stringResource(R.string.iagent_catalog_group_price))
                    }
                    SegmentedButton(
                        selected = !applyRate,
                        onClick = { applyRate = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text(stringResource(R.string.iagent_catalog_base_price))
                    }
                }
            }
            if (ownerId == null) {
                item("guest-metrics") {
                    Text(
                        stringResource(R.string.iagent_catalog_metrics_sign_in),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (loading) {
                item("progress") {
                    Box(Modifier.fillMaxWidth().padding(15.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            error?.let { detail ->
                item("error") {
                    Text(stringResource(R.string.iagent_catalog_load_error, detail), color = MaterialTheme.colorScheme.error)
                }
            }
            if (groups.isEmpty() && !loading && error == null) {
                item("empty") { Text(stringResource(R.string.iagent_catalog_empty)) }
            }
            groups.forEach { section ->
                val expanded = section.id in expansion || (-section.id !in expansion &&
                    (query.isNotBlank() || section.id == groups.firstOrNull()?.id))
                item(key = "group-${section.id}") {
                    CatalogGroupHeader(section, expanded, ownerId != null, metrics?.group(section.id), metrics != null, loadingMetrics) {
                        expansion = expansion.filter { it != section.id && it != -section.id }.toLongArray() +
                            if (expanded) -section.id else section.id
                    }
                }
                if (expanded) {
                    items(section.models, key = { "model-${section.id}-${it.model.platform}-${it.model.id}" }) { entry ->
                        val model = entry.model
                        val prices = remember(entry, section.pricing, section.platform, channels, source?.priceUnit) {
                            model.priceView(
                                section.platform, section.pricing, channels.forModel(section.id, model),
                                entry.resolved, source?.priceUnit == "USD/token",
                            )
                        }
                        CatalogModelRow(
                            model = model,
                            pricing = prices,
                            group = section,
                            applyRate = applyRate,
                            signedIn = ownerId != null,
                            metrics = metrics,
                            loadingMetrics = loadingMetrics,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogGroupHeader(
    group: CatalogSection,
    expanded: Boolean,
    showMetrics: Boolean,
    status: IagentRequestStatus?,
    metricsAvailable: Boolean,
    loadingMetrics: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(
                            stringResource(R.string.iagent_catalog_count, group.models.size),
                            rateLabel(R.string.iagent_catalog_chat_rate, group.pricing.standardRate)?.takeIf { group.hasChat },
                            rateLabel(R.string.iagent_catalog_image_rate, group.pricing.effectiveImageRate)?.takeIf { group.hasImages },
                            rateLabel(R.string.iagent_catalog_video_rate, group.pricing.effectiveVideoRate)?.takeIf { group.hasVideo },
                        ).joinToString(" · "),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Icon(
                    HugeIcons.ArrowDown01,
                    stringResource(if (expanded) R.string.iagent_catalog_collapse else R.string.iagent_catalog_expand),
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            if (showMetrics) CatalogRequestMetrics(status, metricsAvailable, loadingMetrics)
        }
    }
}

@Composable
private fun CatalogModelRow(
    model: IagentCatalogModel,
    pricing: IagentPriceView,
    group: CatalogSection,
    applyRate: Boolean,
    signedIn: Boolean,
    metrics: IagentRequestMetrics?,
    loadingMetrics: Boolean,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { context.writeClipboardText(model.id) },
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerLow,
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(model.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val identity = listOfNotNull(
                    model.id.takeIf { it != model.displayName },
                    model.platform.takeIf { it != group.platform },
                ).joinToString(" · ")
                if (identity.isNotEmpty()) Text(identity, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            CatalogPriceDetails(pricing, group.pricing, applyRate)
            if (signedIn) {
                CatalogRequestMetrics(
                    status = metrics?.model(group.id, model.id),
                    available = metrics != null,
                    loading = loadingMetrics,
                    media = pricing.media,
                )
            }
        }
    }
}

@Composable
private fun rateLabel(label: Int, rate: Double?): String? =
    rate?.let { stringResource(label, catalogNumber(it, 4)) }

private suspend fun <T> catalogOrNull(timeoutMillis: Long = 20_000, load: suspend () -> T): T? = try {
    withTimeoutOrNull(timeoutMillis) { load() }
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

private data class CatalogSection(
    val id: Long,
    val name: String,
    val platform: String,
    val pricing: IagentGroupPricing,
    val hasChat: Boolean,
    val hasImages: Boolean,
    val hasVideo: Boolean,
    val models: List<CatalogEntry>,
)

private data class CatalogEntry(val model: IagentCatalogModel, val resolved: IagentPlazaModel? = null)

private fun IagentCatalog.sections(
    available: List<IagentGroup>?,
    plaza: Map<Long, IagentPlazaGroup>,
    userRates: Map<Long, Double>,
    query: String,
    mediaOnly: Boolean,
): List<CatalogSection> {
    val privateGroups = available?.associateBy { it.id }
    val publicRates = groupPricing.associateBy { it.id }
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val published = models.filter { it.groupId != null }.groupBy { it.groupId }
    val sections = (privateGroups?.keys ?: published.keys.filterNotNull()).mapNotNull { id ->
        val entries = published[id].orEmpty()
        val first = entries.firstOrNull()
        val channel = privateGroups?.get(id)
        val effective = plaza[id]
        val platform = channel?.platform ?: first?.platform.orEmpty()
        val name = (channel?.name ?: first?.groupName).orEmpty().ifBlank { platform.ifBlank { "#$id" } }
        val visible = mergeCatalogEntries(entries, effective, id).filter { (model) ->
            !isIagentReviewModel(model.id, model.name) && (!mediaOnly || mediaProtocol(platform, model) != null) &&
                words.all { "${model.id} ${model.name} $name $platform".contains(it, ignoreCase = true) }
        }
        if (visible.isEmpty()) return@mapNotNull null
        val prices = channel?.pricing?.let { it.copy(userRate = userRates[id] ?: it.userRate) }
            ?: publicRates[id] ?: IagentGroupPricing(id, rate = first?.rate)
        val pricing = effective?.pricing?.let {
            it.copy(
                imagePrice1k = it.imagePrice1k ?: prices.imagePrice1k,
                imagePrice2k = it.imagePrice2k ?: prices.imagePrice2k,
                imagePrice4k = it.imagePrice4k ?: prices.imagePrice4k,
                videoPrice480p = it.videoPrice480p ?: prices.videoPrice480p,
                videoPrice720p = it.videoPrice720p ?: prices.videoPrice720p,
                videoPrice1080p = it.videoPrice1080p ?: prices.videoPrice1080p,
                videoModelPrices = it.videoModelPrices ?: prices.videoModelPrices,
            )
        } ?: prices
        val byModel = visible.associateBy(CatalogEntry::model)
        val kinds = byModel.keys.map { mediaProtocol(platform, it)?.kind }.toSet()
        CatalogSection(
            id, name, platform, pricing, null in kinds, MediaKind.IMAGE in kinds, MediaKind.VIDEO in kinds,
            orderModels(byModel.keys.toList()).map(byModel::getValue),
        )
    }
    return orderGroups(sections, CatalogSection::id, CatalogSection::name)
}

private fun mergeCatalogEntries(
    published: List<IagentCatalogModel>,
    plaza: IagentPlazaGroup?,
    group: Long,
): List<CatalogEntry> {
    val entries = published.flatMap { model ->
        val matches = plaza.match(model)
        if (matches.isEmpty()) listOf(CatalogEntry(model))
        else matches.map { CatalogEntry(model.copy(platform = it.platform), it) }
    }
    val seen = entries.mapTo(hashSetOf()) { (model) -> pricingPlatform(model.platform) to pricingModelName(model.id) }
    val extra = plaza?.models.orEmpty().mapNotNull { model ->
        if (!seen.add(pricingPlatform(model.platform) to pricingModelName(model.name))) return@mapNotNull null
        CatalogEntry(IagentCatalogModel(id = model.name, platform = model.platform, groupId = group), model)
    }
    return (entries + extra).distinctBy { (model) -> pricingPlatform(model.platform) to model.id }
}
