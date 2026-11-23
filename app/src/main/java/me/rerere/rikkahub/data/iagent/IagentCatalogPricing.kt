package me.rerere.rikkahub.data.iagent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.Locale
import kotlin.math.abs

@Serializable
internal data class IagentModelPricing(
    val found: Boolean = false,
    @SerialName("billing_mode") val mode: String? = null,
    @SerialName("input_price") val input: Double? = null,
    @SerialName("output_price") val output: Double? = null,
    @SerialName("cache_write_price") val cacheWrite: Double? = null,
    @SerialName("cache_write_1h_price") val cacheWriteHour: Double? = null,
    @SerialName("cache_read_price") val cacheRead: Double? = null,
    @SerialName("image_input_price") val imageInput: Double? = null,
    @SerialName("image_output_price") val imageOutput: Double? = null,
    @SerialName("per_request_price") val perRequest: Double? = null,
    val intervals: List<IagentPriceInterval>? = null,
    @SerialName("fast_multiplier") val fastRate: Double? = null,
    @SerialName("flex_multiplier") val flexRate: Double? = null,
    @SerialName("reasoning_effort_multipliers") val reasoningRates: Map<String, Double>? = null,
    @SerialName("time_pricing") val schedule: IagentPriceSchedule? = null,
)

@Serializable
internal data class IagentPriceInterval(
    @SerialName("min_tokens") val min: Int,
    @SerialName("max_tokens") val max: Int? = null,
    @SerialName("tier_label") val label: String? = null,
    @SerialName("input_price") val input: Double? = null,
    @SerialName("output_price") val output: Double? = null,
    @SerialName("cache_write_price") val cacheWrite: Double? = null,
    @SerialName("cache_write_1h_price") val cacheWriteHour: Double? = null,
    @SerialName("cache_read_price") val cacheRead: Double? = null,
    @SerialName("image_input_price") val imageInput: Double? = null,
    @SerialName("image_output_price") val imageOutput: Double? = null,
    @SerialName("input_multiplier") val inputRate: Double? = null,
    @SerialName("output_multiplier") val outputRate: Double? = null,
    @SerialName("cache_write_multiplier") val cacheWriteRate: Double? = null,
    @SerialName("cache_read_multiplier") val cacheReadRate: Double? = null,
    @SerialName("per_request_price") val perRequest: Double? = null,
) {
    fun title(): String = label?.trim()?.takeIf(String::isNotEmpty)
        ?: (max?.let { "${compactTokens(min)}–${compactTokens(it)}" } ?: "${compactTokens(min)}+")
}

@Serializable
internal data class IagentPriceSchedule(
    val timezone: String,
    @SerialName("weekdays_only") val weekdaysOnly: Boolean = false,
    val periods: List<IagentPricePeriod>,
)

@Serializable
internal data class IagentPricePeriod(
    @SerialName("start_time") val start: String,
    @SerialName("end_time") val end: String,
    val multiplier: Double,
)

@Serializable
internal data class IagentPlazaPayload(val groups: List<JsonObject>)

@Serializable
internal data class IagentPlazaModels(val models: List<IagentPlazaModel>)

@Serializable
internal data class IagentPlazaModel(
    val name: String,
    val platform: String,
    val pricing: IagentModelPricing? = null,
    @SerialName("long_context_basis") val longContextBasis: String? = null,
    @SerialName("time_pricing") val schedule: IagentPriceSchedule? = null,
)

internal data class IagentPlazaGroup(
    val pricing: IagentGroupPricing,
    val models: List<IagentPlazaModel>,
)

@Serializable
internal data class IagentPublicChannel(val platforms: List<IagentChannelPlatform>)

@Serializable
internal data class IagentChannelPlatform(
    val platform: String,
    val groups: List<IagentChannelGroup>,
    @SerialName("supported_models") val models: List<IagentChannelModel>,
)

@Serializable
internal data class IagentChannelGroup(val id: Long)

@Serializable
internal data class IagentChannelModel(
    val name: String? = null,
    val id: String? = null,
    val model: String? = null,
    @SerialName("model_name") val modelName: String? = null,
    @SerialName("group_id") val groupId: Long? = null,
    @SerialName("group_ids") val groupIds: List<Long>? = null,
    val pricing: IagentModelPricing? = null,
)

internal data class IagentChannelPriceKey(val group: Long, val platform: String, val model: String)

internal fun List<IagentPublicChannel>.indexPrices(): Map<IagentChannelPriceKey, List<IagentModelPricing>> = buildMap {
    for (section in flatMap(IagentPublicChannel::platforms)) {
        for (model in section.models) {
            val pricing = model.pricing ?: continue
            val names = listOfNotNull(model.name, model.id, model.model, model.modelName)
                .map(::pricingModelName).filter(String::isNotEmpty).distinct()
            val groups = model.groupIds?.takeIf { it.isNotEmpty() } ?: model.groupId?.let(::listOf)
                ?: section.groups.map(IagentChannelGroup::id)
            for (group in groups) {
                for (name in names) {
                    val key = IagentChannelPriceKey(group, pricingPlatform(section.platform), name)
                    put(key, get(key).orEmpty() + pricing)
                }
            }
        }
    }
}

internal fun Map<IagentChannelPriceKey, List<IagentModelPricing>>.forModel(
    group: Long,
    model: IagentCatalogModel,
): IagentModelPricing? = listOf(model.id, model.displayName).flatMap { name ->
    get(IagentChannelPriceKey(group, pricingPlatform(model.platform), pricingModelName(name))).orEmpty()
}.maxWithOrNull(compareBy({ it.overrideScore(model.pricing) }, { it.completeness() }))

private fun IagentModelPricing.completeness() =
    listOf(input, output, cacheWrite, cacheWriteHour, cacheRead, imageInput, imageOutput, perRequest)
        .count { it.validPrice() != null } + intervals.orEmpty().size * 2

private fun IagentModelPricing.overrideScore(fallback: IagentModelPricing?): Int {
    if (mode?.equals("token", ignoreCase = true) == false || perRequest.validPrice() != null ||
        intervals.orEmpty().any { it.perRequest.validPrice() != null }
    ) return 100 + completeness()
    return listOf(
        input to fallback?.input, output to fallback?.output, cacheWrite to fallback?.cacheWrite,
        cacheRead to fallback?.cacheRead, imageInput to fallback?.imageInput, imageOutput to fallback?.imageOutput,
    ).count { (value, base) ->
        val configured = value.validPrice() ?: return@count false
        val fallbackValue = base.validPrice() ?: return@count true
        abs(configured - fallbackValue) > maxOf(1e-15, abs(fallbackValue) * 1e-9)
    } * 10
}

internal fun IagentPlazaGroup?.match(model: IagentCatalogModel): List<IagentPlazaModel> {
    if (this == null) return emptyList()
    for (name in listOf(model.id, model.displayName).map(::pricingModelName).distinct()) {
        val matches = models.filter { pricingModelName(it.name) == name }
        if (matches.isNotEmpty()) return matches.distinctBy { pricingPlatform(it.platform) }
    }
    return emptyList()
}

internal fun pricingPlatform(value: String) =
    value.trim().lowercase(Locale.ROOT).let { if (it == "claude") "anthropic" else it }

internal fun pricingModelName(value: String) =
    value.trim().lowercase(Locale.ROOT).trimStart('/').substringAfterLast("/models/").removePrefix("models/")

internal fun Double?.validPrice(): Double? = this?.takeIf { it.isFinite() && it >= 0 }

internal val IagentGroupPricing.standardRate: Double? get() = userRate.validPrice() ?: rate.validPrice()
internal val IagentGroupPricing.effectiveImageRate: Double?
    get() = imageRate.validPrice()?.takeIf { imageRateIndependent } ?: standardRate
internal val IagentGroupPricing.effectiveVideoRate: Double?
    get() = videoRate.validPrice()?.takeIf { videoRateIndependent } ?: standardRate

internal enum class IagentPriceUnit(val scale: Int = 1) {
    TOKEN(1_000_000), IMAGE_TOKEN(1_000_000), IMAGE, SECOND, VIDEO, REQUEST,
}

internal enum class IagentPriceField {
    INPUT, OUTPUT, CACHE_WRITE, CACHE_WRITE_HOUR, CACHE_READ, IMAGE_INPUT, IMAGE_OUTPUT, GENERATION,
}

internal data class IagentPrice(
    val value: Double,
    val unit: IagentPriceUnit,
    val field: IagentPriceField,
    val tier: String? = null,
) {
    val base: Double get() = value * unit.scale
}

internal data class IagentPriceView(
    val prices: List<IagentPrice>,
    val rate: Double?,
    val reference: Boolean,
    val media: IagentCatalogMedia,
    val longContextBasis: String? = null,
    val pricing: IagentModelPricing? = null,
    val schedule: IagentPriceSchedule? = null,
)

internal enum class IagentCatalogMedia { TEXT, IMAGE, VIDEO }

private val nativeVideoPlatforms = setOf("bailian", "video-lt")
private val videoModels = Regex(
    """grok[-_. ]?imag(?:ine|en)[-_. ]?video|(?:^|[\s/_-])(?:sora|veo)(?:[\s/_.-]|$)|seedance|(?:^|[\s/])wan3[._-]0-video(?:-prime)?(?=$|\s)|video[-_ ]generation""",
)
private val imageModels = Regex(
    """gpt-image|grok-imagine-(?:image|edit)|imagegen|image-preview|flash-image|pro-image|nano[-_. ]?banana|stable[-_. ]?diffusion|midjourney|flux(?:[-_. ]|$)|seedream|ideogram|qwen[-_. ]?image|(?:^|[\s/_-])(?:dall-e|imagen)(?:[\s/_.-]|$)|gemini[^\s]*[-_ ]image(?:[\s/_.-]|$)|image[-_ ]generation""",
)

internal fun IagentCatalogModel.priceView(
    groupPlatform: String,
    group: IagentGroupPricing,
    channel: IagentModelPricing?,
    resolved: IagentPlazaModel?,
    hasTokenDefaults: Boolean,
): IagentPriceView {
    val platform = pricingPlatform(groupPlatform).takeIf { it in nativeVideoPlatforms } ?: pricingPlatform(this.platform)
    val label = pricingModelName("$id $displayName")
    val media = when {
        platform in nativeVideoPlatforms || videoModels.containsMatchIn(label) -> IagentCatalogMedia.VIDEO
        imageModels.containsMatchIn(label) -> IagentCatalogMedia.IMAGE
        else -> IagentCatalogMedia.TEXT
    }
    val pricing = if (resolved != null) resolved.pricing else channel
    val mode = pricing?.mode?.lowercase(Locale.ROOT)
    val defaults = this.pricing?.takeIf { resolved == null && it.found && hasTokenDefaults }
    val kind = when {
        platform in nativeVideoPlatforms -> "video"
        mode == "token" -> "token"
        media == IagentCatalogMedia.VIDEO -> "video"
        media == IagentCatalogMedia.IMAGE || mode == "image" -> "image"
        mode == "video" || mode == "per_request" -> mode
        else -> "token"
    }
    val longContext = group.longContextEnabled != false
    val (prices, rate) = when (kind) {
        "video" -> {
            val prices = if (platform == "video-lt") {
                videoLtPrice(group, id, displayName)
            } else {
                group.videoPrices(platform, id).ifEmpty {
                    pricing.requestPrices(if (platform == "bailian" || mode == "video") IagentPriceUnit.SECOND else IagentPriceUnit.VIDEO)
                }
            }
            prices to group.effectiveVideoRate
        }
        "image" -> {
            val sizes = if (resolved == null) group.imagePrices() else emptyList()
            sizes.ifEmpty { pricing.requestPrices(IagentPriceUnit.IMAGE) }
                .ifEmpty { defaults.tokenPrices(tiers = false, imageOnly = true) } to group.effectiveImageRate
        }
        "per_request" -> pricing.requestPrices(IagentPriceUnit.REQUEST) to group.standardRate
        else -> pricing.tokenPrices(longContext).ifEmpty { defaults.tokenPrices(tiers = false) } to group.standardRate
    }
    return IagentPriceView(
        prices = prices,
        rate = rate,
        reference = resolved == null,
        media = media,
        longContextBasis = when {
            kind != "token" -> null
            !longContext -> "lowest"
            pricing?.intervals.orEmpty().size > 1 -> resolved?.longContextBasis ?: "whole_request"
            else -> null
        },
        pricing = (pricing ?: defaults).takeIf { kind == "token" },
        schedule = (resolved?.schedule ?: pricing?.schedule).takeIf { kind == "token" },
    )
}

private fun IagentModelPricing?.tokenPrices(tiers: Boolean, imageOnly: Boolean = false): List<IagentPrice> {
    if (this == null) return emptyList()
    val sorted = intervals.orEmpty().sortedBy(IagentPriceInterval::min)
    val selected = if (tiers) sorted else sorted.take(1)
    return selected.ifEmpty { listOf(null) }.flatMap { interval ->
        val tier = interval?.title()?.takeIf { tiers }
        fun price(field: IagentPriceField, base: Double?, override: Double?, factor: Double? = null, unit: IagentPriceUnit = IagentPriceUnit.TOKEN) =
            (override.validPrice() ?: base.validPrice()?.times(factor.validPrice()?.takeIf { it > 0 } ?: 1.0))
                .validPrice()?.let { IagentPrice(it, unit, field, tier) }
        listOfNotNull(
            price(IagentPriceField.INPUT, input, interval?.input, interval?.inputRate),
            price(IagentPriceField.OUTPUT, output, interval?.output, interval?.outputRate),
            price(IagentPriceField.CACHE_WRITE, cacheWrite, interval?.cacheWrite, interval?.cacheWriteRate).takeUnless { imageOnly },
            price(IagentPriceField.CACHE_WRITE_HOUR, cacheWriteHour, interval?.cacheWriteHour, interval?.cacheWriteRate).takeUnless { imageOnly },
            price(IagentPriceField.CACHE_READ, cacheRead, interval?.cacheRead, interval?.cacheReadRate).takeUnless { imageOnly },
            price(IagentPriceField.IMAGE_INPUT, imageInput, interval?.imageInput, unit = IagentPriceUnit.IMAGE_TOKEN),
            price(IagentPriceField.IMAGE_OUTPUT, imageOutput, interval?.imageOutput, unit = IagentPriceUnit.IMAGE_TOKEN),
        )
    }
}

private fun IagentModelPricing?.requestPrices(unit: IagentPriceUnit): List<IagentPrice> = this?.let {
    intervals.orEmpty().mapNotNull { interval ->
        interval.perRequest.validPrice()?.let { IagentPrice(it, unit, IagentPriceField.GENERATION, interval.title()) }
    }.ifEmpty { listOfNotNull(perRequest.validPrice()?.let { IagentPrice(it, unit, IagentPriceField.GENERATION) }) }
}.orEmpty()

private fun IagentGroupPricing.imagePrices() = resolutionPrices(
    IagentPriceUnit.IMAGE, "1K" to imagePrice1k, "2K" to imagePrice2k, "4K" to imagePrice4k,
)

private val grokVideo15 = Regex("""(?:grok-imagine-video-1\.5(?:-preview)?|grok-video-1\.5)""")
private val grokVideoBase = Regex("""(?:grok-imagine-video(?:-preview)?|grok-video(?:-latest)?)""")

private fun IagentGroupPricing.videoPrices(platform: String, id: String): List<IagentPrice> {
    val name = pricingModelName(id).replace(Regex("^(xai|x-ai|grok)/"), "").replace("grok-imagen-video", "grok-imagine-video")
    val family = when {
        platform == "bailian" -> id
        grokVideo15.matches(name) -> "grok-imagine-video-1.5"
        grokVideoBase.matches(name) -> "grok-imagine-video"
        else -> name
    }
    val model = videoModelPrices?.get(family)
    return resolutionPrices(
        IagentPriceUnit.SECOND,
        "480p" to (model?.get("480p") ?: videoPrice480p),
        "720p" to (model?.get("720p") ?: videoPrice720p),
        "1080p" to (model?.get("1080p") ?: videoPrice1080p),
    )
}

private val sdResolution = Regex("""sd-2(?:\.5(?:-flash)?|-fast|-mini|-vip)-(480|720|1080)""")

private fun videoLtPrice(group: IagentGroupPricing, id: String, displayName: String): List<IagentPrice> {
    val profile = "$id $displayName".lowercase(Locale.ROOT).split(Regex("[/\\s]+")).firstOrNull {
        it == "minimax-h3" || it == "sd-2-vip" || sdResolution.matches(it)
    } ?: return emptyList()
    val resolution = when (profile) {
        "minimax-h3" -> "720p"
        "sd-2-vip" -> "480p"
        else -> "${profile.substringAfterLast('-')}p"
    }
    val alias = when (id) {
        "sd-2-vip" -> "sd-2-vip-480"
        "sd-2-vip-480" -> "sd-2-vip"
        else -> null
    }
    val price = group.videoModelPrices?.get(id)?.get(resolution) ?: group.videoModelPrices?.get(alias)?.get(resolution)
    val unit = if (profile == "minimax-h3") IagentPriceUnit.REQUEST else IagentPriceUnit.SECOND
    return resolutionPrices(unit, resolution to price)
}

private fun resolutionPrices(unit: IagentPriceUnit, vararg prices: Pair<String, Double?>) = prices.mapNotNull { (label, value) ->
    value.validPrice()?.let { IagentPrice(it, unit, IagentPriceField.GENERATION, label) }
}

private fun compactTokens(value: Int): String = when {
    value >= 1_000_000 -> "${(value / 1_000_000.0).toString().removeSuffix(".0")}M"
    value >= 1_000 -> "${(value / 1_000.0).toString().removeSuffix(".0")}K"
    else -> value.toString()
}
