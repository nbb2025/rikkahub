package me.rerere.rikkahub.data.iagent

import me.rerere.mediagen.provider.IagentMediaProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IagentCatalogTest {
    @Test
    fun separatesPublishedImageAndVideoFamilies() {
        assertEquals(
            IagentMediaProtocol.OPENAI_IMAGE,
            mediaProtocol("openai", IagentCatalogModel("gpt-image-2.6-flare"))
        )
        assertEquals(
            IagentMediaProtocol.GEMINI_IMAGE,
            mediaProtocol("gemini", IagentCatalogModel("gemini-3.1-flash-image-preview"))
        )
        assertEquals(IagentMediaProtocol.WAN, mediaProtocol("bailian", IagentCatalogModel("wan3.0-video-prime")))
        assertEquals(
            IagentMediaProtocol.VIDEO_LT_FRAMES,
            mediaProtocol("video-lt", IagentCatalogModel("sd-2.5-flash-480"))
        )
        assertEquals(IagentMediaProtocol.VIDEO_LT, mediaProtocol("video-lt", IagentCatalogModel("sd-2-fast-720")))
        assertEquals(IagentMediaProtocol.VIDEO_LT_H3, mediaProtocol("video-lt", IagentCatalogModel("minimax-h3")))
        assertNull(mediaProtocol("openai", IagentCatalogModel("gpt-6.1-sol")))
    }

    @Test
    fun credentialReferenceCarriesAccountGroupAndPurposeWithoutASecret() {
        val ref = IagentKeyRef(user = 71, group = 21, media = true)
        assertEquals(ref, IagentKeyRef.parse(ref.encode()))
        assertNull(IagentKeyRef.parse("not-an-iagent-reference"))
    }
}
