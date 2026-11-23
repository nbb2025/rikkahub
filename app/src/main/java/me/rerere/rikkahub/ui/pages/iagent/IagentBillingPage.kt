package me.rerere.rikkahub.ui.pages.iagent

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.iagent.IagentAccount
import me.rerere.rikkahub.data.iagent.IagentBilling
import me.rerere.rikkahub.data.iagent.IagentCheckout
import me.rerere.rikkahub.data.iagent.IagentException
import me.rerere.rikkahub.data.iagent.IagentPaymentOrder
import me.rerere.rikkahub.data.iagent.iagentPaymentCurrency
import me.rerere.rikkahub.data.iagent.quote
import me.rerere.rikkahub.ui.components.nav.BackButton
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency

@Composable
internal fun IagentBillingPage(account: IagentAccount, owner: Long) {
    val billing = remember(account) { IagentBilling(account) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val user by account.user.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("") }
    var trackedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var checkout by remember { mutableStateOf<IagentCheckout?>(null) }
    var orders by remember { mutableStateOf(emptyList<IagentPaymentOrder>()) }
    var tracked by remember { mutableStateOf<IagentPaymentOrder?>(null) }
    var qrOrder by remember { mutableStateOf<IagentPaymentOrder?>(null) }
    var cancelOrder by remember { mutableStateOf<IagentPaymentOrder?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var paymentIssue by remember { mutableStateOf<String?>(null) }
    var checkoutIssue by remember { mutableStateOf<String?>(null) }
    var ordersIssue by remember { mutableStateOf<String?>(null) }
    val failedCheckout = stringResource(R.string.iagent_recharge_load_error)
    val failedOrders = stringResource(R.string.iagent_recharge_orders_error)
    val failedOpen = stringResource(R.string.iagent_recharge_no_checkout)
    val orderUnknown = stringResource(R.string.iagent_recharge_unknown)

    LaunchedEffect(owner, reload) {
        loading = true
        checkoutIssue = null
        ordersIssue = null
        try {
            coroutineScope {
                launch {
                    try {
                        checkout = billing.checkout(owner)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        checkoutIssue = failedCheckout
                    }
                }
                launch {
                    try {
                        orders = billing.orders(owner)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        ordersIssue = failedOrders
                    }
                }
                launch {
                    try {
                        account.refreshProfile(owner)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // Keep the balance fetched by the account page.
                    }
                }
            }
        } finally {
            loading = false
        }
    }

    LaunchedEffect(owner, trackedId, lifecycle) {
        val id = trackedId ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                try {
                    val current = billing.order(owner, id)
                    tracked = current
                    if (!current.pending) {
                        if (qrOrder?.resolvedId == id) qrOrder = null
                        if (current.state == "COMPLETED") account.refreshProfile(owner)
                        reload++
                        return@repeatOnLifecycle
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The next foreground check can recover a temporarily unavailable order.
                }
                delay(3_000)
            }
        }
    }

    fun openPayment(order: IagentPaymentOrder) {
        val url = order.paymentUrl
        when {
            url != null -> {
                try {
                    CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url.toString()))
                } catch (_: Exception) {
                    paymentIssue = failedOpen
                }
            }
            !order.qrCode.isNullOrBlank() -> qrOrder = order
            else -> paymentIssue = failedOpen
        }
    }

    val config = checkout
    val selected = config?.options?.firstOrNull { it.id == method } ?: config?.options?.firstOrNull()
    val amount = input.trim().toBigDecimalOrNull()?.takeIf { it.precision() <= 14 && it.scale() in 0..6 }
    val quote = if (amount != null && selected != null) config?.quote(amount, selected) else null
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.iagent_recharge)) },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { reload++ }, enabled = !loading && !working) {
                        Icon(HugeIcons.Refresh, stringResource(R.string.iagent_retry))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("balance") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.iagent_account_balance_note), style = MaterialTheme.typography.labelLarge)
                        Text(
                            user?.balance?.let { money(it, Currency.getInstance("USD")) } ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
            }
            if (loading || working) item("progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            checkoutIssue?.let { item("checkout-error") { BillingError(it) } }
            val openConfig = config?.takeIf { it.enabled && checkoutIssue == null }
            if (openConfig != null && selected != null) {
                item("form") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val currency = iagentPaymentCurrency(selected.limit.currency)
                        Text(stringResource(R.string.iagent_recharge_amount), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            openConfig.presets.filter { it.isFinite() && it > 0 }.distinct().forEach { value ->
                                val preset = BigDecimal.valueOf(value).stripTrailingZeros()
                                FilterChip(
                                    selected = amount?.compareTo(preset) == 0,
                                    onClick = { input = preset.toPlainString() },
                                    label = { Text(money(value, currency)) },
                                    enabled = !working,
                                )
                            }
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.replace(',', '.').take(15) },
                            label = { Text(stringResource(R.string.iagent_recharge_amount_input, currency.currencyCode)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        )
                        Text(stringResource(R.string.iagent_recharge_method), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            openConfig.options.forEach { option ->
                                FilterChip(
                                    selected = selected.id == option.id,
                                    onClick = { method = option.id },
                                    label = { Text(option.name) },
                                    enabled = !working,
                                )
                            }
                        }
                        openConfig.limits.promotionNotice?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        if (quote != null) {
                            BillingAmount(R.string.iagent_recharge_credit, money(quote.credit, Currency.getInstance("USD")))
                            BillingAmount(R.string.iagent_recharge_due, money(quote.paid, quote.currency))
                        } else if (input.isNotBlank()) {
                            BillingError(stringResource(R.string.iagent_recharge_invalid))
                        }
                        Button(
                            enabled = !working && !loading && quote != null && ordersIssue == null &&
                                (trackedId == null || tracked?.pending == false),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (working || loading || (trackedId != null && tracked?.pending != false)) return@Button
                                val requested = amount ?: return@Button
                                working = true
                                paymentIssue = null
                                scope.launch {
                                    try {
                                        val submitted = billing.create(owner, requested, selected.id)
                                        tracked = submitted
                                        trackedId = submitted.resolvedId
                                        reload++
                                        openPayment(submitted)
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        val clientError = error is IagentException && error.status in 400..499
                                        paymentIssue = if (clientError) error.message ?: orderUnknown else orderUnknown
                                        if (!clientError) reload++
                                    } finally {
                                        working = false
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.iagent_recharge_continue)) }
                    }
                }
            } else if (!loading && checkoutIssue == null) {
                item("unavailable") { Text(stringResource(R.string.iagent_recharge_unavailable)) }
            }
            paymentIssue?.let { item("payment-error") { BillingError(it) } }
            item("orders-title") {
                Text(stringResource(R.string.iagent_recharge_orders), style = MaterialTheme.typography.titleLarge)
            }
            ordersIssue?.let { item("orders-error") { BillingError(it) } }
            val orderRows = (listOfNotNull(tracked) + orders).distinctBy { it.resolvedId ?: it.reference }
            if (orderRows.isEmpty() && !loading && ordersIssue == null) {
                item("no-orders") { Text(stringResource(R.string.iagent_recharge_no_orders)) }
            }
            items(orderRows, key = { it.resolvedId ?: it.reference }) { order ->
                BillingOrderRow(
                    order = order,
                    enabled = !working,
                    onOpen = {
                        tracked = order
                        trackedId = order.resolvedId
                        openPayment(order)
                    },
                    onCancel = { cancelOrder = order },
                )
            }
        }
    }
    cancelOrder?.let { target ->
        AlertDialog(
            onDismissRequest = { cancelOrder = null },
            title = { Text(stringResource(R.string.iagent_recharge_cancel_order)) },
            text = { Text(target.reference) },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.resolvedId ?: return@TextButton
                    cancelOrder = null
                    working = true
                    scope.launch {
                        try {
                            billing.cancel(owner, id)
                            if (trackedId == id) {
                                tracked = null
                                trackedId = null
                            }
                            reload++
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            paymentIssue = error.message ?: failedOrders
                        } finally {
                            working = false
                        }
                    }
                }) { Text(stringResource(R.string.iagent_recharge_cancel_order)) }
            },
            dismissButton = { TextButton(onClick = { cancelOrder = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    qrOrder?.let { order ->
        PaymentQrDialog(order, onDismiss = { qrOrder = null }, onOpenAlipay = {
            val link = Uri.parse("alipays://platformapi/startapp").buildUpon()
                .appendQueryParameter("saId", "10000007")
                .appendQueryParameter("qrcode", order.qrCode)
                .build()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, link))
            } catch (_: Exception) {
                qrOrder = null
                paymentIssue = failedOpen
            }
        })
    }
}

@Composable
private fun BillingOrderRow(order: IagentPaymentOrder, enabled: Boolean, onOpen: () -> Unit, onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(order.reference.ifBlank { "#${order.resolvedId ?: ""}" }, style = MaterialTheme.typography.labelLarge)
                Text(orderState(order.state), style = MaterialTheme.typography.labelLarge)
            }
            val currency = iagentPaymentCurrency(order.currency)
            order.paidAmount?.let { Text(money(it, currency), style = MaterialTheme.typography.titleMedium) }
            if (order.state == "PENDING" && order.resolvedId != null) {
                Row {
                    if (order.paymentUrl != null || !order.qrCode.isNullOrBlank()) {
                        TextButton(onClick = onOpen, enabled = enabled) { Text(stringResource(R.string.iagent_recharge_resume)) }
                    }
                    TextButton(onClick = onCancel, enabled = enabled) { Text(stringResource(R.string.iagent_recharge_cancel_order)) }
                }
            }
        }
    }
}

@Composable
private fun orderState(state: String) = when (state) {
    "PENDING" -> stringResource(R.string.iagent_order_pending)
    "PAID", "RECHARGING" -> stringResource(R.string.iagent_order_crediting)
    "COMPLETED" -> stringResource(R.string.iagent_order_complete)
    "CANCELLED" -> stringResource(R.string.iagent_order_cancelled)
    "EXPIRED" -> stringResource(R.string.iagent_order_expired)
    "FAILED" -> stringResource(R.string.iagent_order_failed)
    else -> state
}

@Composable
private fun BillingAmount(label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BillingError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PaymentQrDialog(order: IagentPaymentOrder, onDismiss: () -> Unit, onOpenAlipay: () -> Unit) {
    val code = order.qrCode.orEmpty()
    val qr = remember(code) { renderPaymentQr(code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.iagent_recharge_qr)) },
        text = {
            if (qr == null) BillingError(stringResource(R.string.iagent_recharge_no_checkout))
            else Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(qr, contentDescription = stringResource(R.string.iagent_recharge_qr), modifier = Modifier.size(226.dp))
            }
        },
        confirmButton = {
            if (order.paymentType.orEmpty().startsWith("alipay")) {
                TextButton(onClick = onOpenAlipay) { Text(stringResource(R.string.iagent_recharge_alipay)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private fun renderPaymentQr(code: String): ImageBitmap? = runCatching {
    val size = 400
    val bits = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { if (bits[it % size, it / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()

private fun money(amount: Number, currency: Currency): String =
    NumberFormat.getCurrencyInstance().apply {
        this.currency = currency
        maximumFractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
        minimumFractionDigits = maximumFractionDigits
    }.format(amount)
