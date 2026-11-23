package me.rerere.rikkahub.ui.pages.iagent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.iagent.IAGENT_REQUEST_METRIC_HOURS
import me.rerere.rikkahub.data.iagent.IagentCatalogMedia
import me.rerere.rikkahub.data.iagent.IagentGroupPricing
import me.rerere.rikkahub.data.iagent.IagentModelRequestStatus
import me.rerere.rikkahub.data.iagent.IagentPriceField
import me.rerere.rikkahub.data.iagent.IagentPriceUnit
import me.rerere.rikkahub.data.iagent.IagentPriceView
import me.rerere.rikkahub.data.iagent.IagentRequestStatus
import me.rerere.rikkahub.data.iagent.validPrice
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat

@Composable
internal fun CatalogPriceDetails(pricing: IagentPriceView, group: IagentGroupPricing, applyRate: Boolean) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val rate = pricing.rate.takeIf { applyRate }
    if (pricing.prices.isEmpty()) {
        Text(stringResource(R.string.iagent_catalog_price_missing), color = colors.onSurfaceVariant, style = type.bodySmall)
        return
    }
    val source = stringResource(
        if (pricing.reference) R.string.iagent_catalog_price_reference else R.string.iagent_catalog_price_standard,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            rate?.let { stringResource(R.string.iagent_catalog_rate_applied, source, catalogNumber(it, 4)) } ?: source,
            color = colors.onSurfaceVariant,
            style = type.labelMedium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            maxItemsInEachRow = 2,
        ) {
            pricing.prices.forEach { price ->
                Column(Modifier.weight(1f)) {
                    val label = stringResource(price.field.label)
                    Text(
                        when {
                            price.tier == null -> label
                            price.field == IagentPriceField.GENERATION -> price.tier
                            else -> "${price.tier} · $label"
                        },
                        color = colors.onSurfaceVariant,
                        style = type.labelSmall,
                    )
                    Text(
                        stringResource(price.unit.label, formatPrice(price.base * (rate ?: 1.0))),
                        style = type.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        CatalogPriceConditions(pricing, group)
    }
}

@Composable
private fun CatalogPriceConditions(view: IagentPriceView, group: IagentGroupPricing) {
    val conditions = buildList {
        val basis = when (view.longContextBasis) {
            "lowest" -> R.string.iagent_catalog_tier_lowest
            "marginal" -> R.string.iagent_catalog_tier_marginal
            "whole_request" -> R.string.iagent_catalog_tier_whole
            else -> null
        }
        basis?.let { add(stringResource(it)) }
        val pricing = view.pricing
        if (pricing != null) {
            pricing.fastRate.validPrice()?.takeIf { it > 0 }?.let { add("Fast / Priority ×${catalogNumber(it, 8)}") }
            pricing.flexRate.validPrice()?.takeIf { it > 0 }?.let { add("Flex ×${catalogNumber(it, 8)}") }
            pricing.reasoningRates.orEmpty().toSortedMap().forEach { (effort, factor) ->
                if (factor.isFinite() && factor > 0 && factor != 1.0) {
                    add(stringResource(R.string.iagent_catalog_reasoning_rate, effort, catalogNumber(factor, 8)))
                }
            }
            view.schedule?.let { schedule ->
                val timezone = if (schedule.weekdaysOnly) {
                    stringResource(R.string.iagent_catalog_weekdays, schedule.timezone)
                } else {
                    schedule.timezone
                }
                schedule.periods.forEach { period ->
                    if (period.multiplier.isFinite() && period.multiplier > 0 && period.multiplier != 1.0) {
                        add("${period.start}–${period.end} ($timezone) ×${catalogNumber(period.multiplier, 8)}")
                    }
                }
            }
            group.peakRate.validPrice()?.takeIf { group.peakRateEnabled && it > 0 && it != 1.0 }?.let {
                add(stringResource(R.string.iagent_catalog_peak_rate, group.peakStart, group.peakEnd, catalogNumber(it, 8)))
            }
        }
    }
    if (conditions.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) R.string.iagent_catalog_hide_conditions else R.string.iagent_catalog_show_conditions))
    }
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            conditions.forEach {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun CatalogRequestMetrics(
    status: IagentRequestStatus?,
    available: Boolean,
    loading: Boolean,
    media: IagentCatalogMedia? = null,
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val rate = status?.successRate?.takeIf { it.isFinite() && it in 0.0..1.0 }
    val summary = if (rate != null) {
        val percentage = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(rate)
        stringResource(R.string.iagent_catalog_metrics_success, percentage)
    } else {
        stringResource(
            when {
                loading -> R.string.iagent_catalog_metrics_loading
                !available -> R.string.iagent_catalog_metrics_unavailable
                else -> R.string.iagent_catalog_metrics_no_data
            },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.iagent_catalog_metrics_window),
                modifier = Modifier.weight(1f),
                color = colors.onSurfaceVariant,
                style = type.labelMedium,
            )
            Text(summary, style = type.labelMedium, fontWeight = if (rate == null) FontWeight.Normal else FontWeight.SemiBold)
        }
        if (rate != null) {
            val byIndex = status.buckets.associateBy { it.index }
            val states = List(IAGENT_REQUEST_METRIC_HOURS) { byIndex[it]?.state }
            val success = states.count { it == "success" }
            val mixed = states.count { it == "mixed" }
            val failure = states.count { it == "failure" }
            val description = stringResource(
                R.string.iagent_catalog_metrics_history, success, mixed, failure, states.size - success - mixed - failure,
            )
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                states.forEach { state ->
                    val color = when (state) {
                        "success" -> Color(0xFF278F72)
                        "mixed" -> Color(0xFFC68124)
                        "failure" -> colors.error
                        else -> colors.outlineVariant
                    }
                    Box(Modifier.weight(1f).height(7.dp).background(color, RoundedCornerShape(2.dp)))
                }
            }
            if (status is IagentModelRequestStatus) {
                val latency = status.latencyMs.validPrice()?.let {
                    if (it >= 1_000) "${catalogNumber(it / 1_000, 2)} s" else "${catalogNumber(it, 0)} ms"
                } ?: "—"
                val output = status.outputTokensPerSecond.validPrice()?.let {
                    stringResource(R.string.iagent_catalog_metrics_tokens_per_second, catalogNumber(it, 1))
                } ?: "—"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CatalogMetricValue(Modifier.weight(1f), stringResource(R.string.iagent_catalog_metrics_latency), latency)
                    if (media == IagentCatalogMedia.TEXT) {
                        CatalogMetricValue(Modifier.weight(1f), stringResource(R.string.iagent_catalog_metrics_output), output)
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogMetricValue(modifier: Modifier, label: String, value: String) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

internal fun catalogNumber(value: Double, digits: Int) =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = digits }.format(value)

private fun formatPrice(value: Double): String = when {
    value > 0 && value < 1e-8 -> DecimalFormat("0.########E0", DecimalFormatSymbols.getInstance()).format(value)
    value < 0.01 -> catalogNumber(value, 8)
    value < 1 -> catalogNumber(value, 6)
    else -> catalogNumber(value, 4)
}

private val IagentPriceUnit.label: Int
    get() = when (this) {
        IagentPriceUnit.TOKEN -> R.string.iagent_catalog_usd_tokens
        IagentPriceUnit.IMAGE_TOKEN -> R.string.iagent_catalog_usd_image_tokens
        IagentPriceUnit.IMAGE -> R.string.iagent_catalog_usd_image
        IagentPriceUnit.SECOND -> R.string.iagent_catalog_usd_second
        IagentPriceUnit.VIDEO -> R.string.iagent_catalog_usd_video
        IagentPriceUnit.REQUEST -> R.string.iagent_catalog_usd_request
    }

private val IagentPriceField.label: Int
    get() = when (this) {
        IagentPriceField.INPUT -> R.string.iagent_catalog_price_input
        IagentPriceField.OUTPUT -> R.string.iagent_catalog_price_output
        IagentPriceField.CACHE_WRITE -> R.string.iagent_catalog_price_cache_write
        IagentPriceField.CACHE_WRITE_HOUR -> R.string.iagent_catalog_price_cache_hour
        IagentPriceField.CACHE_READ -> R.string.iagent_catalog_price_cache_read
        IagentPriceField.IMAGE_INPUT -> R.string.iagent_catalog_price_image_input
        IagentPriceField.IMAGE_OUTPUT -> R.string.iagent_catalog_price_image_output
        IagentPriceField.GENERATION -> R.string.iagent_catalog_price_generation
    }
