package me.rerere.rikkahub.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.mediagen.model.MediaKind
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.iagent.IagentAccount
import me.rerere.rikkahub.data.iagent.IagentModels
import me.rerere.rikkahub.data.model.MediaCreationOutput
import me.rerere.rikkahub.data.model.MediaCreationRecord
import me.rerere.rikkahub.data.model.MediaCreationStatus
import me.rerere.rikkahub.data.repository.MediaCreationRepository
import me.rerere.rikkahub.data.skills.SkillManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class IagentWorkbenchTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun guestCanBrowseBeforeSigningInAndUsingTheWorkbench() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        Locale.setDefault(Locale.US)
        val koin = GlobalContext.get()
        val account = IagentAccount(context, koin.get<AppScope>(), fixtureClient())
        koin.loadModules(listOf(module {
            single { account }
            single { IagentModels(account = account, settings = get()) }
        }), allowOverride = true)
        val repo = koin.get<MediaCreationRepository>()
        val session = runBlocking {
            val session = repo.newSession()
            val recordId = Uuid.random()
            val file = File(repo.recordDir(session.id, recordId).apply { mkdirs() }, "fixture.webp")
            context.resources.openRawResource(R.drawable.iagent_studio_example).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            repo.insertRecord(MediaCreationRecord(
                id = recordId,
                sessionId = session.id,
                providerId = Uuid.random(),
                providerName = "Images",
                modelId = "gpt-image-2",
                kind = MediaKind.IMAGE,
                prompt = "Dunes at first light",
                status = MediaCreationStatus.SUCCEEDED,
                outputs = listOf(MediaCreationOutput(repo.relativePath(file), "image/webp", width = 640, height = 360)),
            ))
            session
        }
        try {
            ActivityScenario.launch(RouteActivity::class.java).use {
                visible("Sign in to chat")
                capture("guest")
                compose.onNodeWithText("Model catalog").performClick()
                visible("Wan · Bailian")
                visible("Video 1.3×", substring = true)
                compose.onAllNodesWithText("Codex Auto Review").assertCountEquals(0)
                capture("catalog")
                compose.onNodeWithContentDescription("Back").performClick()
                compose.onNodeWithText("Account").performClick()
                visible("Guest")
                compose.onNodeWithText("Recharge balance").performClick()
                visible("Sign in to iAgent")
                capture("login")
                compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("preview@iagent.example")
                compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("preview-only")
                compose.onNodeWithText("Sign in to iAgent").performClick()
                visible("Recharge amount")
                compose.onNodeWithText("100.00", substring = true).performClick()
                visible("Estimated payment")
                capture("billing")
                compose.onNodeWithText("Continue to payment").performClick()
                visible("Payment QR code")
                visible("Credited")
                compose.onNodeWithContentDescription("Back").performClick()
                visible("iAgent account")
                compose.onNodeWithText("Chat").performClick()
                visible("What’s on your mind?")
                visible("GPT-6.1 Sol")
                val settings = koin.get<SettingsStore>().settingsFlow.value
                assertTrue(settings.providers.flatMap { it.models }.none { it.modelId == "codex-auto-review" })
                capture("chat")
                compose.onNodeWithText("Studio").performClick()
                visible("Creative studio")
                visible("GPT Image 2")
                capture("studio")
                compose.onNode(hasSetTextAction()).performTextReplacement("An editorial still life")
                compose.onNodeWithText("Video").performClick()
                visible("Wan 3.0")
                compose.onNode(hasSetTextAction()).performTextReplacement("Slow camera movement")
                compose.onNodeWithText("Image").performClick()
                visible("An editorial still life")
                compose.onNodeWithText("Video").performClick()
                visible("Slow camera movement")
                capture("video")
                compose.onNodeWithText("Wan 3.0").performClick()
                visible("Search models or groups")
                compose.onNodeWithText("Select model").assertIsDisplayed()
                capture("models")
                instrumentation.uiAutomation.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                )
                visible("Results")
                compose.onNodeWithText("Results").performClick()
                visible("Dunes at first light")
                capture("results")
                if (InstrumentationRegistry.getArguments().getString("publicSkills") == "true") {
                    verifyPublicSkillImport(koin.get())
                }
                runBlocking { account.logout() }
                visible("Sign in to chat")
                compose.onAllNodesWithText("Dunes at first light").assertCountEquals(0)
            }
        } finally {
            runBlocking {
                account.logout()
                repo.deleteSession(session.id)
            }
        }
    }

    private fun verifyPublicSkillImport(skillManager: SkillManager) {
        compose.onNodeWithText("Account").performClick()
        visible("Agent Skills")
        compose.onNodeWithText("Agent Skills").performClick()
        visible("Skill marketplace")
        compose.onNodeWithText("Skill marketplace").performClick()
        visible("Popular on Skills.sh")
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("frontend-design")
        compose.onNodeWithContentDescription("Search", useUnmergedTree = true).performClick()
        visible("anthropics/skills")
        capture("skills")
        val existing = skillManager.findSkill("frontend-design")
        try {
            compose.onNodeWithContentDescription("Import frontend-design from anthropics/skills").performClick()
            visible("SKILL.md")
            assertNotNull(skillManager.findSkill("frontend-design"))
        } finally {
            if (existing == null) {
                runBlocking {
                    skillManager.findSkill("frontend-design")?.let { skillManager.deleteSkill(it) }
                }
            }
        }
    }

    private fun visible(label: String, substring: Boolean = false) {
        compose.waitUntil(
            timeoutMillis = 15_000
        ) { compose.onAllNodesWithText(label, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("capture") != "true") return
        compose.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.cacheDir, "iagent-$name.png").outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
    }

    private fun fixtureClient(): OkHttpClient {
        val checks = AtomicInteger()
        return OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val json = when (request.url.encodedPath) {
            "/api/v1/auth/login" -> """
                {"code":0,"data":{"access_token":"fixture","refresh_token":"fixture-refresh","expires_in":86400,
                "user":{"id":8021,"email":"preview@iagent.example","balance":24.1}}}
            """.trimIndent()
            "/api/v1/auth/logout" -> """{"code":0,"data":{}}"""
            "/api/v1/auth/me" -> """{"code":0,"data":{"id":8021,"email":"preview@iagent.example","balance":24.1}}"""
            "/api/v1/groups/available" -> """
                {"code":0,"data":[{"id":4,"name":"OpenAI Workspace","platform":"openai","status":"active"},
                {"id":42,"name":"Wan · Bailian","platform":"bailian","status":"active"}]}
            """.trimIndent()
            "/site-api/models" -> """
                {"group_order":[42,4],"group_pricing":[{"id":42,"rate_multiplier":1.3}],"models":[
                {"id":"gpt-6.1-sol","name":"GPT-6.1 Sol","platform":"openai","group_id":4,"group_name":"OpenAI Workspace"},
                {"id":"gpt-image-2","name":"GPT Image 2","platform":"openai","group_id":4,"group_name":"OpenAI Workspace"},
                {"id":"codex-auto-review","name":"Codex Auto Review","platform":"openai","group_id":4,"group_name":"OpenAI Workspace"},
                {"id":"wan3.0-video","name":"Wan 3.0","platform":"bailian","group_id":42,"group_name":"Wan · Bailian"}]}
            """.trimIndent()
            "/site-api/config" -> """{"billing":{"amounts":[50,100],"methods":[{"id":"alipay","label":"Alipay","enabled":true}]}}"""
            "/api/v1/payment/config" -> """{"code":0,"data":{"min_amount":1,"max_amount":1000,"enabled":true}}"""
            "/api/v1/payment/checkout-info" -> """{"code":0,"data":{"methods":{"alipay":{"currency":"CNY","single_min":1,"single_max":1000}}}}"""
            "/api/v1/payment/orders/my" -> """{"code":0,"data":{"items":[]}}"""
            "/api/v1/payment/orders" -> {
                val body = Buffer()
                request.body!!.writeTo(body)
                val payload = org.json.JSONObject(body.readUtf8())
                check(payload.getInt("amount") == 100 && payload.getString("payment_source") == "hosted_redirect")
                check(payload.getString("payment_type") == "alipay" && payload.getBoolean("is_mobile"))
                """{"code":0,"data":{"order_id":101,"status":"PENDING","payment_type":"alipay","qr_code":"https://qa.example/pay","out_trade_no":"TEST101","pay_amount":100}}"""
            }
            "/api/v1/payment/orders/101" -> {
                val status = if (checks.incrementAndGet() >= 2) "COMPLETED" else "PENDING"
                """{"code":0,"data":{"id":101,"status":"$status","out_trade_no":"TEST101","pay_amount":100}}"""
            }
            else -> error("Unexpected request ${request.url.encodedPath}")
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(200).message("OK").body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
    }
}
