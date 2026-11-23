package me.rerere.rikkahub.data.iagent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
internal data class IagentCatalog(
    val models: List<IagentCatalogModel>,
    @SerialName("group_order") val groupOrder: List<Long> = emptyList(),
    @SerialName("group_pricing") val groupPricing: List<IagentGroupPricing> = emptyList(),
    @SerialName("sort_rules") val sortRules: List<IagentModelSortRule> = emptyList(),
    @SerialName("price_unit") val priceUnit: String? = null,
)

@Serializable
internal data class IagentGroup(
    val id: Long,
    val name: String,
    val platform: String,
    val status: String,
    val description: String? = null,
    @SerialName("claude_code_only") val claudeCodeOnly: Boolean = false,
    @SerialName("rate_multiplier") val rate: Double? = null,
    @SerialName("image_rate_independent") val imageRateIndependent: Boolean? = null,
    @SerialName("image_rate_multiplier") val imageRate: Double? = null,
    @SerialName("video_rate_independent") val videoRateIndependent: Boolean? = null,
    @SerialName("video_rate_multiplier") val videoRate: Double? = null,
)

@Serializable
internal data class IagentGroupPricing(
    val id: Long,
    @SerialName("rate_multiplier") val rate: Double? = null,
    @SerialName("image_rate_independent") val imageRateIndependent: Boolean = false,
    @SerialName("image_rate_multiplier") val imageRate: Double? = null,
    @SerialName("video_rate_independent") val videoRateIndependent: Boolean = false,
    @SerialName("video_rate_multiplier") val videoRate: Double? = null,
)

@Serializable
internal data class IagentCatalogModel(
    val id: String,
    val name: String = "",
    val platform: String = "",
    val description: String? = null,
    @SerialName("group_id") val groupId: Long? = null,
    @SerialName("group_name") val groupName: String? = null,
    @SerialName("rate_multiplier") val rate: Double? = null,
    @SerialName("context_length") val contextLength: Int? = null,
    @SerialName("max_input_tokens") val maxInputTokens: Int? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
    @SerialName("default_pricing") val pricing: IagentModelPricing? = null,
) {
    val displayName get() = name.ifBlank { id }
}

@Serializable
internal data class IagentModelPricing(
    val found: Boolean = false,
    @SerialName("input_price") val input: Double? = null,
    @SerialName("output_price") val output: Double? = null,
    @SerialName("cache_read_price") val cacheRead: Double? = null,
)

@Serializable
internal data class IagentModelSortRule(
    val pattern: String,
    val strategy: String = "",
    val keywords: List<String> = emptyList(),
)

private val reviewPattern = Regex("""auto[\s._-]*review""", RegexOption.IGNORE_CASE)

internal fun isIagentReviewModel(id: String, name: String) =
    reviewPattern.containsMatchIn(id) || reviewPattern.containsMatchIn(name)

internal fun <T> IagentCatalog.orderGroups(groups: List<T>, id: (T) -> Long, name: (T) -> String): List<T> {
    val positions = groupOrder.withIndex().associate { it.value to it.index }
    return groups.sortedWith(
        compareBy<T> { positions[id(it)] ?: Int.MAX_VALUE }
            .thenComparator { left, right -> compareDescending(name(left), name(right)) }
            .thenBy(id)
    )
}

internal fun IagentCatalog.orderModels(models: List<IagentCatalogModel>): List<IagentCatalogModel> {
    val ranked = models.map { model ->
        val id = model.id.trim().lowercase(Locale.ROOT)
        val index = sortRules.indexOfFirst {
            val pattern = it.pattern.trim().lowercase(Locale.ROOT)
            if (pattern.endsWith('*')) id.startsWith(pattern.dropLast(1)) else id == pattern
        }
        val keywords = sortRules.getOrNull(index)?.keywords.orEmpty().map(::keyword).filter(String::isNotEmpty)
        val key = "-${keyword(id)}-"
        val tier = keywords.indexOfFirst { "-$it-" in key }.takeIf { it >= 0 } ?: keywords.size
        RankedModel(model, index.takeIf { it >= 0 } ?: sortRules.size, version(id), tier)
    }
    return ranked.sortedWith { left, right ->
        val rule = left.rule.compareTo(right.rule)
        if (rule != 0) return@sortedWith rule
        val tier = left.tier.compareTo(right.tier)
        val version = compareVersion(left.version, right.version)
        val rank = when (sortRules.getOrNull(left.rule)?.strategy) {
            "tier-version" -> tier.takeIf { it != 0 } ?: version
            "version-tier" -> version.takeIf { it != 0 } ?: tier
            "version" -> version
            else -> 0
        }
        if (rank != 0) {
            rank
        } else {
            compareDescending(left.model.displayName, right.model.displayName)
                .takeIf { it != 0 } ?: compareDescending(left.model.id, right.model.id)
        }
    }.map { it.model }
}

private data class RankedModel(val model: IagentCatalogModel, val rule: Int, val version: List<Int>, val tier: Int)

private val keywordSeparator = Regex("""[-._/\s]+""")
private val versionDate = Regex("""[-._]20\d{2}(?:[.-]\d{2}[.-]\d{2}|\d{4})(?=$|[-._]).*$""")
private val versionNumber = Regex("""(?:^|[\p{L}_.-])(\d{1,3}(?:[.-]\d{1,3})*)(?=$|[-._])""")
private val sortParts = Regex("""\d+|\D+""")

private fun keyword(value: String) = value.trim().lowercase(Locale.ROOT).split(keywordSeparator)
    .filter(String::isNotEmpty).joinToString("-")

private fun version(id: String): List<Int> = versionNumber.find(id.substringAfterLast('/').replace(versionDate, ""))
    ?.groupValues?.get(1)?.split('.', '-')?.map(String::toInt).orEmpty()

private fun compareVersion(left: List<Int>, right: List<Int>): Int {
    if (left.isEmpty() || right.isEmpty()) return right.isNotEmpty().compareTo(left.isNotEmpty())
    for (index in 0 until maxOf(left.size, right.size)) {
        val difference = right.getOrElse(index) { 0 }.compareTo(left.getOrElse(index) { 0 })
        if (difference != 0) return difference
    }
    return 0
}

private fun compareDescending(left: String, right: String): Int {
    val a = sortParts.findAll(left).map { it.value }.toList()
    val b = sortParts.findAll(right).map { it.value }.toList()
    for (index in 0 until minOf(a.size, b.size)) {
        val aNumber = a[index].toLongOrNull()
        val bNumber = b[index].toLongOrNull()
        val difference = if (aNumber != null && bNumber != null) {
            bNumber.compareTo(aNumber)
        } else {
            b[index].compareTo(a[index], ignoreCase = true)
        }
        if (difference != 0) return difference
    }
    return b.size.compareTo(a.size)
}
