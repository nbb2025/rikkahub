package me.rerere.rikkahub.data.iagent

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.normalizedForIagent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class IagentLaunchPolicyTest {
    @Test
    fun keepsOnlyManagedChatAndRemovesReviewModelsFromStoredSettings() {
        val review = Model(modelId = "codex-auto-review", displayName = "Codex Auto Review")
        val chat = Model(modelId = "gpt-6.1-sol", providerOverwrite = ProviderSetting.OpenAI(name = "Other"))
        val managed = ProviderSetting.OpenAI(
            name = "iAgent",
            apiKey = IagentKeyRef(1, 2, false).encode(),
            enabled = false,
            models = listOf(review, chat),
        )
        val settings = Settings(
            providers = listOf(
                managed,
                ProviderSetting.OpenAI(name = "Other", models = listOf(Model(modelId = "other")))
            ),
            chatModelId = review.id,
            favoriteModels = listOf(review.id),
        ).normalizedForIagent()
        assertEquals(listOf(managed.id), settings.providers.map { it.id })
        assertTrue(settings.providers.single().enabled)
        assertEquals(listOf(chat.id), settings.providers.single().models.map { it.id })
        assertNull(settings.providers.single().models.single().providerOverwrite)
        assertEquals(chat.id, settings.chatModelId)
        assertTrue(settings.favoriteModels.isEmpty())
        assertTrue(isIagentReviewModel("alias", "Codex Auto Review"))
        assertTrue(!isIagentReviewModel("gemini-3-pro-image-preview", "Gemini Image Preview"))
    }

    @Test
    fun followsCatalogOrderAndUsesNaturalDescendingFallback() {
        val models = listOf("free-2", "claude-haiku-9", "gpt-5-terra", "free-11", "gpt-6-sol", "claude-opus-4")
            .map { IagentCatalogModel(id = it, name = it) }
        val catalog = IagentCatalog(
            models,
            groupOrder = listOf(3L, 4L),
            sortRules = listOf(
                IagentModelSortRule("gpt-*", "version-tier", listOf("Terra", "Sol")),
                IagentModelSortRule("claude-*", "tier-version", listOf("Opus", "Haiku")),
            ),
        )
        assertEquals(
            listOf("gpt-6-sol", "gpt-5-terra", "claude-opus-4", "claude-haiku-9", "free-11", "free-2"),
            catalog.orderModels(models).map { it.id }
        )
        val groups = listOf(1L to "Group 2", 4L to "A", 3L to "B", 5L to "Group 11")
        assertEquals(listOf(3L, 4L, 5L, 1L), catalog.orderGroups(groups, { it.first }, { it.second }).map { it.first })
    }

    @Test
    fun computesRechargeQuoteAndValidatesActualChannelLimits() {
        val limit = IagentPaymentLimit(minimum = 10.0, maximum = 120.0, feeRate = 0.0)
        val option = IagentPaymentOption("alipay", "Alipay", limit)
        val config = IagentCheckout(
            IagentCheckoutLimits(
                methods = mapOf("alipay" to limit),
                minimum = 10.0,
                maximum = 200.0,
                multiplier = 0.2,
                feeRate = 0.35,
                promotionTiers = listOf(IagentRechargeTier(100.0, 5.0)),
            ),
            presets = listOf(100.0),
            options = listOf(option),
        )
        val bonus = config.quote(BigDecimal("100.01"), option)!!
        assertEquals(BigDecimal("100.37"), bonus.paid)
        assertEquals(BigDecimal("21.00"), bonus.credit)
        val discount = config.copy(limits = config.limits.copy(promotionMode = "discount"))
            .quote(BigDecimal("100.00"), option)!!
        assertEquals(BigDecimal("95.34"), discount.paid)
        assertEquals(BigDecimal("20.00"), discount.credit)
        assertNull(config.quote(BigDecimal("130"), option))
        assertNull(config.quote(BigDecimal("10.001"), option))
        assertNull(IagentPaymentOrder(payUrl = "http://pay.example").paymentUrl)
        assertEquals(
            "https://ai.iagent.dev/payment/result",
            IagentPaymentOrder(payUrl = "/payment/result").paymentUrl.toString()
        )
    }
}
