package me.rerere.mediagen.provider.providers.iagent

import kotlinx.coroutines.runBlocking
import me.rerere.mediagen.model.ImageRole
import me.rerere.mediagen.model.MediaGenerationInput
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaGenerationRequest
import me.rerere.mediagen.model.MediaGenerationStatus
import me.rerere.mediagen.provider.IagentMediaProtocol
import me.rerere.mediagen.provider.MediaGenerationManager
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class IagentMediaProviderTest {
    @Test
    fun imageUsesSiteTaskAndRestoresResult() = runBlocking {
        val model = model("gpt-image-2", IagentMediaProtocol.OPENAI_IMAGE)
        val seen = mutableListOf<String>()
        val manager = client { request ->
            seen += request.url.encodedPath
            when (request.url.encodedPath) {
                "/site-api/image-config" -> """{"async_enabled":true}"""
                "/images/generations/async" -> {
                    assertEquals("openai-generation", request.header("X-Iagent-Image-Route"))
                    assertEquals("Bearer fixture-key", request.header("Authorization"))
                    assertTrue(request.bodyText().contains("gpt-image-2"))
                    """{"task_id":"imgtask_fixture","status":"processing","poll_url":"/site-api/image-tasks/imgtask_fixture"}"""
                }
                "/site-api/image-tasks/imgtask_fixture" -> """
                    {"status":"completed","result":{"data":[{"url":"https://cdn.example/sample.png"}]}}
                """.trimIndent()
                else -> error("Unexpected request")
            }
        }
        val setting = setting(model, IagentMediaProtocol.OPENAI_IMAGE)
        val task = manager.create(setting, model, MediaGenerationRequest(prompt = "A lake")).getOrThrow()
        assertEquals(MediaGenerationStatus.RUNNING, task.status)
        val finished = manager.query(setting, model, task.id).getOrThrow()
        assertEquals(MediaGenerationStatus.SUCCEEDED, finished.status)
        assertEquals("https://cdn.example/sample.png", finished.outputs.single().url)
        assertEquals(
            listOf("/site-api/image-config", "/images/generations/async", "/site-api/image-tasks/imgtask_fixture"),
            seen
        )
    }

    @Test
    fun wanUsesNativeTaskIdAndSendsReferenceAudio() = runBlocking {
        val model = model("wan3.0-video", IagentMediaProtocol.WAN)
        val manager = client { request ->
            when (request.url.encodedPath) {
                "/api/v1/services/aigc/video-generation/video-synthesis" -> {
                    assertEquals("enable", request.header("X-DashScope-Async"))
                    assertTrue(request.bodyText().contains("\"type\":\"reference_audio\""))
                    """{"request_id":"trace","output":{"task_id":"wan-task","task_status":"PENDING"}}"""
                }
                "/api/v1/tasks/wan-task" -> """
                    {"output":{"task_id":"wan-task","task_status":"SUCCEEDED","video_url":"https://cdn.example/sample.mp4"}}
                """.trimIndent()
                else -> error("Unexpected request")
            }
        }
        val setting = setting(model, IagentMediaProtocol.WAN)
        val request =
            MediaGenerationRequest(
                prompt = "A bird",
                inputs = listOf(MediaGenerationInput.Audio("https://cdn.example/ref.wav"))
            )
        val task = manager.create(setting, model, request).getOrThrow()
        assertEquals("wan-task", task.id)
        assertEquals(
            "https://cdn.example/sample.mp4",
            manager.query(setting, model, task.id).getOrThrow().outputs.single().url
        )
    }

    @Test
    fun videoLtFramesUseSeparateFieldsFromGeneralReferences() {
        val protocol = IagentMediaProtocol.VIDEO_LT_FRAMES
        val request = MediaGenerationRequest(
            prompt = "A bird",
            durationSeconds = 5,
            inputs = listOf(
                MediaGenerationInput.Image("https://cdn.example/first.png", ImageRole.FIRST_FRAME),
                MediaGenerationInput.Image("https://cdn.example/last.png", ImageRole.LAST_FRAME),
            ),
        )
        val body = iagentVideoBody(protocol, "sd-2.5-flash-480", request)
        assertEquals("https://cdn.example/first.png", body.text("start_image_url"))
        assertEquals("https://cdn.example/last.png", body.text("end_image_url"))
        assertFalse(body.containsKey("images"))
        assertEquals("v1/video/create", videoCreatePath(protocol))
        assertEquals("v1/video/query", videoQueryPath(protocol))
    }

    @Test
    fun ambiguousCreateDoesNotSendAnotherSubmission() = runBlocking {
        val model = model("wan3.0-video", IagentMediaProtocol.WAN)
        var calls = 0
        val manager = client(status = 503) {
            calls++
            """{"message":"temporarily unavailable"}"""
        }
        val response = manager.create(
            setting(model, IagentMediaProtocol.WAN),
            model,
            MediaGenerationRequest(prompt = "Clouds")
        )
        assertTrue(response.exceptionOrNull() is IagentSubmissionUnknown)
        assertEquals(1, calls)
    }

    private fun model(name: String, protocol: IagentMediaProtocol) = MediaGenerationModel(name, protocol.kind)

    private fun setting(model: MediaGenerationModel, protocol: IagentMediaProtocol) =
        MediaGenerationProviderSetting.Iagent(Uuid.random(), "iAgent", "fixture-key", protocol, listOf(model))

    private fun client(status: Int = 200, payload: (Request) -> String): MediaGenerationManager {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(
                    status
                ).message("fixture").body(payload(request).toResponseBody("application/json".toMediaType())).build()
        }.build()
        return MediaGenerationManager(client)
    }

    private fun Request.bodyText(): String = Buffer().apply { body?.writeTo(this) }.readUtf8()
}
