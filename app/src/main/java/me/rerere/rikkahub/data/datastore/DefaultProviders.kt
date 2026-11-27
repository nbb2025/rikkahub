package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.QuotaQuery
import kotlin.uuid.Uuid

val DEFAULT_AUTO_MODEL_ID = Uuid.parse("b7055fb4-39f9-4042-a88a-0d80ed76cf08")

val DEEPSEEK_PROVIDER_ID = Uuid.parse("f099ad5b-ef03-446d-8e78-7e36787f780b")
internal const val DEEPSEEK_ORIGIN = "https://api.deepseek.com"

internal val DEEPSEEK_PROVIDER = ProviderSetting.OpenAI(
    id = DEEPSEEK_PROVIDER_ID,
    name = "DeepSeek",
    baseUrl = "$DEEPSEEK_ORIGIN/v1",
    builtIn = true,
    quotaQuery = QuotaQuery(
        enabled = true,
        url = "{{baseUrl}}/user/balance",
        resultPath = "balance_infos[0].total_balance",
    ),
)

val DEFAULT_PROVIDERS: List<ProviderSetting> = listOf(DEEPSEEK_PROVIDER)
