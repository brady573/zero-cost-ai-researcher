package dev.zerocost.researcher.performance

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTelemetryRecorderTest {
    @Test
    fun snapshotAggregatesThroughputAndRetention() = runBlocking {
        val recorder = ModelTelemetryRecorder(maxRetainedCalls = 2)

        recorder.record(
            ModelCallMetric(
                startedAtEpochMs = 1,
                durationMs = 1_000,
                streamEmissions = 10,
                outputChars = 20,
            )
        )
        recorder.record(
            ModelCallMetric(
                startedAtEpochMs = 2,
                durationMs = 2_000,
                streamEmissions = 20,
                outputChars = 40,
            )
        )
        recorder.record(
            ModelCallMetric(
                startedAtEpochMs = 3,
                durationMs = 1_000,
                streamEmissions = 30,
                outputChars = 60,
            )
        )

        val snapshot = recorder.snapshot()

        assertEquals(2, snapshot.calls)
        assertEquals(50L, snapshot.streamEmissions)
        assertEquals(100L, snapshot.outputChars)
        assertEquals(3_000L, snapshot.totalDurationMs)
        assertEquals(20.0, snapshot.averageEmissionsPerSecond ?: 0.0, 0.0001)
    }
}
