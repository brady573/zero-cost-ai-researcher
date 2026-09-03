package dev.zerocost.researcher.performance

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ModelCallMetric(
    val startedAtEpochMs: Long,
    val durationMs: Long,
    val streamEmissions: Int,
    val outputChars: Int,
) {
    val emissionsPerSecond: Double?
        get() = if (durationMs > 0 && streamEmissions > 0) {
            streamEmissions * 1000.0 / durationMs
        } else {
            null
        }
}

data class ModelTelemetrySnapshot(
    val calls: Int,
    val streamEmissions: Long,
    val outputChars: Long,
    val totalDurationMs: Long,
    val averageEmissionsPerSecond: Double?,
    val recentCalls: List<ModelCallMetric>,
)

class ModelTelemetryRecorder(
    private val maxRetainedCalls: Int = 200,
) {
    private val mutex = Mutex()
    private val calls = ArrayDeque<ModelCallMetric>()

    suspend fun record(metric: ModelCallMetric) {
        mutex.withLock {
            calls += metric
            while (calls.size > maxRetainedCalls) {
                calls.removeFirst()
            }
        }
    }

    suspend fun snapshot(): ModelTelemetrySnapshot = mutex.withLock {
        val retained = calls.toList()
        val totalEmissions = retained.sumOf {
            it.streamEmissions.toLong()
        }
        val totalChars = retained.sumOf { it.outputChars.toLong() }
        val totalDuration = retained.sumOf { it.durationMs }
        val rateSamples = retained.mapNotNull(
            ModelCallMetric::emissionsPerSecond
        )

        ModelTelemetrySnapshot(
            calls = retained.size,
            streamEmissions = totalEmissions,
            outputChars = totalChars,
            totalDurationMs = totalDuration,
            averageEmissionsPerSecond = rateSamples
                .takeIf { it.isNotEmpty() }
                ?.average(),
            recentCalls = retained,
        )
    }

    suspend fun clear() {
        mutex.withLock {
            calls.clear()
        }
    }
}
