package me.rerere.rikkahub.data.iagent

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IagentCatalogPricingTest {
    private val textModel = IagentCatalogModel(
        id = "gpt-6.1-sol",
        platform = "openai",
        pricing = IagentModelPricing(found = true, input = 0.000010, output = 0.000040),
    )

    @Test
    fun referencePriceUsesTheMatchingGroupRate() {
        val discounted = textModel.priceView("openai", IagentGroupPricing(1, rate = 0.5), null, null, true)
        val standard = textModel.priceView("openai", IagentGroupPricing(2, rate = 1.5), null, null, true)
        assertEquals(10.0, discounted.prices.first().base, 1e-12)
        assertEquals(10.0, standard.prices.first().base, 1e-12)
        assertEquals(5.0, discounted.prices.first().base * discounted.rate!!, 1e-12)
        assertEquals(15.0, standard.prices.first().base * standard.rate!!, 1e-12)
        assertTrue(discounted.reference)
    }

    @Test
    fun channelPricesAreScopedByGroupAndPlatform() {
        val channels = Json.decodeFromString<List<IagentPublicChannel>>(
            """
            [{"platforms":[{"platform":"openai","groups":[{"id":1},{"id":2}],"supported_models":[
            {"model_name":"models/GPT-6.1-sol","group_id":2,"pricing":{"billing_mode":"token","input_price":0.000005}},
            {"name":"gpt-6.1-sol","pricing":{"billing_mode":"token","input_price":0.000010,"output_price":0.000040}}
            ]}]}]
            """.trimIndent(),
        ).indexPrices()
        assertEquals(0.000010, channels.forModel(1, textModel)?.input)
        assertEquals(0.000005, channels.forModel(2, textModel)?.input)
        assertNull(channels.forModel(2, textModel.copy(platform = "anthropic")))
    }

    @Test
    fun resolvedPriceHonorsTheUserRateAndLowestTier() {
        val effective = IagentPlazaModel(
            name = textModel.id,
            platform = textModel.platform,
            pricing = IagentModelPricing(
                mode = "token",
                input = 0.000020,
                intervals = listOf(
                    IagentPriceInterval(min = 128_000, input = 0.000035),
                    IagentPriceInterval(min = 0, max = 128_000, inputRate = 1.5),
                ),
            ),
            longContextBasis = "marginal",
        )
        val group = IagentGroupPricing(1, rate = 1.5, userRate = 0.75, longContextEnabled = false)
        val price = textModel.priceView("openai", group, null, effective, true)
        assertFalse(price.reference)
        assertEquals(22.5, price.prices.single().base * price.rate!!, 1e-12)
        assertNull(price.prices.single().tier)
        assertEquals("lowest", price.longContextBasis)

        val tiered = textModel.priceView("openai", group.copy(longContextEnabled = true), null, effective, true)
        assertEquals(listOf("0–128K", "128K+"), tiered.prices.map { it.tier })
        assertEquals("marginal", tiered.longContextBasis)
        val undisclosed = textModel.priceView("openai", group, null, effective.copy(pricing = null), true)
        assertTrue(undisclosed.prices.isEmpty())
    }

    @Test
    fun mediaUsesItsOwnRateAndThePublishedBillingUnit() {
        val wan = IagentCatalogModel("wan3.0-video", platform = "bailian")
        val wanGroup = IagentGroupPricing(
            42,
            rate = 1.5,
            videoRateIndependent = true,
            videoRate = 0.67,
            videoPrice480p = 0.8,
            videoModelPrices = mapOf(wan.id to mapOf("480p" to 0.3)),
        )
        val wanPrice = wan.priceView("bailian", wanGroup, null, null, true)
        assertEquals(IagentPriceUnit.SECOND, wanPrice.prices.single().unit)
        assertEquals(0.201, wanPrice.prices.single().base * wanPrice.rate!!, 1e-12)

        val image = IagentCatalogModel("gpt-image-2", platform = "openai")
        val imageGroup = IagentGroupPricing(
            4,
            rate = 1.5,
            imageRateIndependent = true,
            imageRate = 0.25,
            imagePrice1k = 0.4,
        )
        val imagePrice = image.priceView("openai", imageGroup, null, null, true)
        assertEquals(IagentPriceUnit.IMAGE, imagePrice.prices.single().unit)
        assertEquals(0.1, imagePrice.prices.single().base * imagePrice.rate!!, 1e-12)

        val h3 = IagentCatalogModel("minimax-h3", platform = "video-lt")
        val videoLtGroup = IagentGroupPricing(46, rate = 1.0, videoModelPrices = mapOf(h3.id to mapOf("720p" to 1.3)))
        val videoLtPrice = h3.priceView("video-lt", videoLtGroup, null, null, true)
        assertEquals(IagentPriceUnit.REQUEST, videoLtPrice.prices.single().unit)
        assertEquals(1.3, videoLtPrice.prices.single().base, 1e-12)
    }
}
