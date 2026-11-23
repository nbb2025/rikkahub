package me.rerere.rikkahub.data.iagent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
internal data class IagentRequestMetrics(
    @SerialName("window_start") val start: String,
    @SerialName("window_end") val end: String,
    @SerialName("bucket_seconds") val bucketSeconds: Int,
    val groups: List<IagentGroupRequestStatus>,
    val items: List<IagentModelRequestStatus>,
) {
    // The website and server use an hourly history covering the past 24 hours.
    private val byGroup = groups.associateBy(IagentGroupRequestStatus::groupId)
    private val byModel = items.associateBy { it.groupId to it.model.metricKey() }

    fun group(id: Long) = byGroup[id]
    fun model(group: Long, id: String) = byModel[group to id.metricKey()]
}

internal const val IAGENT_REQUEST_METRIC_HOURS = 24

internal interface IagentRequestStatus {
    val successRate: Double?
    val buckets: List<IagentRequestBucket>
}

@Serializable
internal data class IagentGroupRequestStatus(
    @SerialName("group_id") val groupId: Long,
    @SerialName("success_rate") override val successRate: Double? = null,
    override val buckets: List<IagentRequestBucket> = emptyList(),
) : IagentRequestStatus

@Serializable
internal data class IagentModelRequestStatus(
    @SerialName("group_id") val groupId: Long,
    val model: String,
    @SerialName("success_rate") override val successRate: Double? = null,
    @SerialName("avg_latency_ms") val latencyMs: Double? = null,
    @SerialName("stream_output_tokens_per_second") val outputTokensPerSecond: Double? = null,
    override val buckets: List<IagentRequestBucket> = emptyList(),
) : IagentRequestStatus

@Serializable
internal data class IagentRequestBucket(val index: Int, val state: String)

private fun String.metricKey() = trim().lowercase(Locale.ROOT)
