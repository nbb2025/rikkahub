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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.hugeicons.stroke.Search01
import me.rerere.mediagen.model.MediaKind
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.iagent.IagentAccount
import me.rerere.rikkahub.data.iagent.IagentCatalog
import me.rerere.rikkahub.data.iagent.IagentCatalogModel
import me.rerere.rikkahub.data.iagent.IagentGroup
import me.rerere.rikkahub.data.iagent.IagentModels
import me.rerere.rikkahub.data.iagent.isIagentReviewModel
import me.rerere.rikkahub.data.iagent.mediaProtocol
import me.rerere.rikkahub.data.iagent.orderGroups
import me.rerere.rikkahub.data.iagent.orderModels
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.compose.koinInject
import java.text.NumberFormat

@Composable
internal fun IagentCatalogPage(mediaOnly: Boolean = false) {
    val service: IagentModels = koinInject()
    val account: IagentAccount = koinInject()
    val owner by account.user.collectAsStateWithLifecycle()
    val source by service.catalog.collectAsStateWithLifecycle()
    val available by service.availableGroups.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var opened by rememberSaveable { mutableStateOf<Long?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(refresh) {
        loading = true
        error = null
        try {
            val id = account.user.value?.id
            if (refresh > 0 && id != null) service.synchronize(id, force = true)
            else service.loadCatalog(force = refresh > 0)
        } catch (caught: CancellationException) {
            throw caught
        } catch (caught: Exception) {
            error = caught.localizedMessage ?: "iAgent"
        } finally {
            loading = false
        }
    }
    val privateGroups = available?.takeIf { it.owner == owner?.id }?.groups.orEmpty()
    val groups = remember(source, privateGroups, query, mediaOnly) {
        source?.sections(privateGroups, query, mediaOnly).orEmpty()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (mediaOnly) R.string.iagent_catalog_media else R.string.iagent_catalog_title)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { refresh++ }, enabled = !loading) {
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
                val expanded = if (query.isBlank()) (opened ?: groups.firstOrNull()?.id) == section.id else opened != -section.id
                item(key = "group-${section.id}") {
                    CatalogGroupHeader(section, expanded) {
                        opened = if (expanded) -section.id else section.id
                    }
                }
                if (expanded) {
                    items(section.models, key = { "model-${section.id}-${it.id}" }) { model ->
                        CatalogModelRow(model, source?.priceUnit == "USD/token")
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogGroupHeader(group: CatalogSection, expanded: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            Modifier.padding(15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        stringResource(R.string.iagent_catalog_count, group.models.size),
                        rateLabel(R.string.iagent_catalog_chat_rate, group.rate)?.takeIf { group.hasChat },
                        rateLabel(R.string.iagent_catalog_image_rate, group.imageRate)?.takeIf { group.hasImages },
                        rateLabel(R.string.iagent_catalog_video_rate, group.videoRate)?.takeIf { group.hasVideo },
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
    }
}

@Composable
private fun CatalogModelRow(model: IagentCatalogModel, hasTokenPrices: Boolean) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { context.writeClipboardText(model.id) },
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(model.displayName, style = MaterialTheme.typography.bodyLarge)
            val pricing = model.pricing?.takeIf { it.found && hasTokenPrices }
            val input = pricing?.input?.takeIf { it.isFinite() && it >= 0 }
            val output = pricing?.output?.takeIf { it.isFinite() && it >= 0 }
            val hasName = model.id != model.displayName
            if (hasName) Text(model.id, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            if (input != null && output != null) {
                Text(
                    stringResource(R.string.iagent_catalog_token_price, number(input * 1_000_000), number(output * 1_000_000)),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun rateLabel(label: Int, rate: Double?): String? =
    rate?.takeIf { it.isFinite() && it >= 0 }?.let { stringResource(label, number(it)) }

private fun number(value: Double) = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 6 }.format(value)

private data class CatalogSection(
    val id: Long,
    val name: String,
    val rate: Double?,
    val imageRate: Double?,
    val videoRate: Double?,
    val hasChat: Boolean,
    val hasImages: Boolean,
    val hasVideo: Boolean,
    val models: List<IagentCatalogModel>,
)

private fun IagentCatalog.sections(available: List<IagentGroup>, query: String, mediaOnly: Boolean): List<CatalogSection> {
    val privateGroups = available.associateBy { it.id }
    val publicRates = groupPricing.associateBy { it.id }
    val words = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val sections = models.filterNot { isIagentReviewModel(it.id, it.name) }.groupBy { it.groupId }.mapNotNull { (id, entries) ->
        if (id == null) return@mapNotNull null
        val first = entries.first()
        val channel = privateGroups[id]
        val prices = publicRates[id]
        val name = (channel?.name ?: first.groupName).orEmpty().ifBlank { first.platform.ifBlank { "#$id" } }
        val platform = channel?.platform ?: first.platform
        val visible = entries.distinctBy { it.id }.filter { model ->
            (!mediaOnly || mediaProtocol(platform, model) != null) &&
                words.all { "${model.id} ${model.name} $name $platform".contains(it, ignoreCase = true) }
        }
        if (visible.isEmpty()) return@mapNotNull null
        val rate = channel?.rate ?: prices?.rate ?: first.rate
        val imageRate = if (channel?.imageRateIndependent ?: (prices?.imageRateIndependent == true)) {
            channel?.imageRate ?: prices?.imageRate
        } else rate
        val videoRate = if (channel?.videoRateIndependent ?: (prices?.videoRateIndependent == true)) {
            channel?.videoRate ?: prices?.videoRate
        } else rate
        val kinds = visible.map { mediaProtocol(platform, it)?.kind }.toSet()
        CatalogSection(id, name, rate, imageRate, videoRate, null in kinds, MediaKind.IMAGE in kinds, MediaKind.VIDEO in kinds, orderModels(visible))
    }
    return orderGroups(sections, CatalogSection::id, CatalogSection::name)
}
