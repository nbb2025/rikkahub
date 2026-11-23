package me.rerere.rikkahub.data.iagent

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

internal class IagentBilling(private val account: IagentAccount) {
    suspend fun checkout(owner: Long): IagentCheckout = coroutineScope {
        val server = async { account.authenticated(owner, "/api/v1/payment/checkout-info") as JsonObject }
        val options = async { account.authenticated(owner, "/api/v1/payment/config") as JsonObject }
        val site = async { account.siteBilling() }
        val limits = account.decode<IagentCheckoutLimits>(JsonObject(options.await() + server.await()))
        val configuration = site.await()
        IagentCheckout(
            limits,
            configuration.amounts,
            configuration.methods.mapNotNull { method ->
                val available = limits.methods[method.id] ?: return@mapNotNull null
                if (!method.enabled || !available.available) return@mapNotNull null
                IagentPaymentOption(method.id, method.label.ifBlank { available.displayName ?: method.id }, available)
            },
        )
    }

    suspend fun orders(owner: Long): List<IagentPaymentOrder> =
        account.decode<IagentOrderPage>(
            account.authenticated(owner, "/api/v1/payment/orders/my?page=1&page_size=20")
        ).items

    suspend fun order(owner: Long, id: Long): IagentPaymentOrder =
        account.decode(account.authenticated(owner, "/api/v1/payment/orders/$id"))

    suspend fun cancel(owner: Long, id: Long) {
        account.authenticated(owner, "/api/v1/payment/orders/$id/cancel", buildJsonObject {})
    }

    suspend fun create(owner: Long, amount: BigDecimal, method: String): IagentPaymentOrder =
        account.decode(account.authenticated(owner, "/api/v1/payment/orders", buildJsonObject {
            put("amount", JsonPrimitive(amount))
            put("payment_type", method)
            put("order_type", "balance")
            put("payment_source", "hosted_redirect")
            put("is_mobile", true)
            put("return_url", "$IAGENT_ORIGIN/payment/result")
        }))
}

@Serializable
internal data class IagentSiteConfig(val billing: IagentSiteBilling = IagentSiteBilling())

@Serializable
internal data class IagentSiteBilling(
    val amounts: List<Double> = emptyList(),
    val methods: List<IagentSitePaymentMethod> = emptyList(),
)

@Serializable
internal data class IagentSitePaymentMethod(val id: String, val label: String = "", val enabled: Boolean = false)

@Serializable
internal data class IagentCheckoutLimits(
    val methods: Map<String, IagentPaymentLimit>,
    val enabled: Boolean = true,
    @SerialName("payment_enabled") val paymentEnabled: Boolean = true,
    @SerialName("balance_disabled") val balanceDisabled: Boolean = false,
    @SerialName("min_amount") val minimum: Double? = null,
    @SerialName("max_amount") val maximum: Double? = null,
    @SerialName("global_min") val globalMinimum: Double? = null,
    @SerialName("global_max") val globalMaximum: Double? = null,
    @SerialName("balance_recharge_multiplier") val multiplier: Double = 1.0,
    @SerialName("recharge_fee_rate") val feeRate: Double? = null,
    @SerialName("recharge_bonus_mode") val promotionMode: String? = null,
    @SerialName("recharge_bonus_tiers") val promotionTiers: List<IagentRechargeTier> = emptyList(),
    @SerialName("recharge_bonus_notice") val promotionNotice: String? = null,
)

@Serializable
internal data class IagentPaymentLimit(
    val currency: String = "CNY",
    val available: Boolean = true,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("single_min") val minimum: Double = 0.0,
    @SerialName("single_max") val maximum: Double = 0.0,
    @SerialName("fee_rate") val feeRate: Double = 0.0,
)

@Serializable
internal data class IagentRechargeTier(
    @SerialName("min_amount") val minimum: Double,
    @SerialName("bonus_percent") val percent: Double,
)

@Serializable
private data class IagentOrderPage(val items: List<IagentPaymentOrder> = emptyList())

@Serializable
internal data class IagentPaymentOrder(
    val id: Long? = null,
    @SerialName("order_id") val orderId: Long? = null,
    @SerialName("out_trade_no") val reference: String = "",
    val amount: Double? = null,
    @SerialName("pay_amount") val paidAmount: Double? = null,
    val currency: String? = null,
    val status: String = "PENDING",
    @SerialName("payment_type") val paymentType: String? = null,
    @SerialName("pay_url") val payUrl: String? = null,
    @SerialName("qr_code") val qrCode: String? = null,
    @SerialName("result_type") val resultType: String? = null,
) {
    val resolvedId get() = (orderId ?: id)?.takeIf { it > 0 }
    val state get() = status.trim().uppercase(Locale.ROOT)
    val pending get() = state in setOf("PENDING", "PAID", "RECHARGING")
    val paymentUrl: HttpUrl? get() = payUrl?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith('#') }
        ?.let { IAGENT_ORIGIN.toHttpUrl().resolve(it) }
        ?.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() }
}

internal data class IagentPaymentOption(val id: String, val name: String, val limit: IagentPaymentLimit)

internal data class IagentCheckout(
    val limits: IagentCheckoutLimits,
    val presets: List<Double>,
    val options: List<IagentPaymentOption>,
) {
    val enabled get() = limits.enabled && limits.paymentEnabled && !limits.balanceDisabled && options.isNotEmpty()
}

internal data class IagentRechargeQuote(val paid: BigDecimal, val credit: BigDecimal, val currency: Currency)

internal fun IagentCheckout.quote(amount: BigDecimal, option: IagentPaymentOption): IagentRechargeQuote? {
    if (!enabled || option !in options || amount.signum() <= 0) return null
    val limit = option.limit
    val currency = iagentPaymentCurrency(limit.currency)
    val digits = when (currency.currencyCode) {
        "ISK", "UGX" -> 0
        "IQD", "LYD" -> 3
        else -> currency.defaultFractionDigits.coerceIn(0, 3)
    }
    if (amount.stripTrailingZeros().scale() > digits) return null
    val minimum = limits.minimum ?: limits.globalMinimum
    val maximum = limits.maximum ?: limits.globalMaximum
    if (lessThanLimit(amount, minimum) || greaterThanLimit(amount, maximum)) return null
    val multiplier = decimal(limits.multiplier)?.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
    val tier = limits.promotionTiers.asSequence()
        .filter {
            it.minimum.isFinite() && it.minimum >= 0 && !lessThanLimit(amount, it.minimum) && it.percent > 0 &&
                it.percent <= 1_000
        }
        .maxByOrNull { it.minimum }
    val baseCredit = amount.multiply(multiplier).setScale(2, RoundingMode.HALF_UP)
    val percent = tier?.percent?.let { BigDecimal.valueOf(it).movePointLeft(2) }
    val discount = limits.promotionMode == "discount"
    val payBase = if (discount && percent != null && percent < BigDecimal.ONE) {
        amount.multiply(BigDecimal.ONE.subtract(percent)).setScale(digits, RoundingMode.HALF_UP)
            .takeIf { it.signum() > 0 } ?: amount
    } else {
        amount
    }
    val credit = if (!discount && percent != null) {
        baseCredit.add(baseCredit.multiply(percent).setScale(2, RoundingMode.HALF_UP))
    } else {
        baseCredit
    }
    val feeRate = decimal(limits.feeRate) ?: decimal(limit.feeRate) ?: BigDecimal.ZERO
    val fee = payBase.multiply(feeRate.movePointLeft(2)).setScale(digits, RoundingMode.CEILING)
    val paid = payBase.add(fee).setScale(digits, RoundingMode.HALF_UP)
    if (lessThanLimit(paid, limit.minimum) || greaterThanLimit(paid, limit.maximum)) return null
    return IagentRechargeQuote(paid, credit, currency)
}

internal fun iagentPaymentCurrency(code: String?) =
    runCatching { Currency.getInstance(code.orEmpty().trim().uppercase(Locale.ROOT)) }
        .getOrDefault(Currency.getInstance("CNY"))

private fun decimal(value: Double?) =
    value?.takeIf { it.isFinite() && it >= 0 }?.let(BigDecimal::valueOf)

private fun lessThanLimit(amount: BigDecimal, minimum: Double?) =
    minimum?.takeIf { it.isFinite() && it > 0 }?.let { amount < BigDecimal.valueOf(it) } == true

private fun greaterThanLimit(amount: BigDecimal, maximum: Double?) =
    maximum?.takeIf { it.isFinite() && it > 0 }?.let { amount > BigDecimal.valueOf(it) } == true
