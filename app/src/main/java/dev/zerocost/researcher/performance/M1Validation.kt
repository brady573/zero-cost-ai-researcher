package dev.zerocost.researcher.performance

import android.content.Context
import dev.zerocost.researcher.inference.JsonExtractor
import dev.zerocost.researcher.inference.ModelBenchmark
import dev.zerocost.researcher.inference.ModelNotReadyException
import dev.zerocost.researcher.inference.ResearchModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class M1ValidationConfig(
    val durationMs: Long = 60_000,
) {
    init {
        require(durationMs in 10_000..600_000)
    }
}

data class M1AcceptanceCheck(
    val metric: String,
    val actual: String,
    val target: String,
    val passes: Boolean,
)

data class M1ValidationResult(
    val id: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val requestedDurationMs: Long,
    val actualDurationMs: Long,
    val completedNormally: Boolean,
    val structuredAttempts: Int,
    val structuredSuccesses: Int,
    val structuredSuccessRate: Double,
    val modelTelemetry: ModelTelemetrySnapshot,
    val nativeBenchmark: ModelBenchmark?,
    val deviceTelemetry: DeviceTelemetrySummary,
    val deviceSamples: List<DeviceTelemetrySample>,
    val checks: List<M1AcceptanceCheck>,
    val reportPath: String,
)

class M1ValidationRunner(
    private val context: Context,
    private val model: ResearchModel,
    private val modelTelemetry: ModelTelemetryRecorder,
    private val deviceTelemetry: DeviceTelemetryCollector,
) {
    suspend fun run(
        config: M1ValidationConfig,
        scope: CoroutineScope,
        onProgress: suspend (String) -> Unit = {},
    ): M1ValidationResult {
        modelTelemetry.clear()

        val id = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val deadline = startedAt + config.durationMs
        val session = DeviceTelemetrySession(
            collector = deviceTelemetry,
            scope = scope,
        )
        session.start()

        var attempts = 0
        var successes = 0
        var completedNormally = false
        var cancellation: CancellationException? = null

        var samples: List<DeviceTelemetrySample> = emptyList()
        var nativeBenchmark: ModelBenchmark? = null
        try {
            while (System.currentTimeMillis() < deadline) {
                currentCoroutineContext().ensureActive()
                val workload = WORKLOADS[attempts % WORKLOADS.size]
                attempts++

                val raw = try {
                    model.generateText(
                        systemPrompt = STRUCTURED_SYSTEM,
                        prompt = workload,
                        maxTokens = 220,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (notReady: ModelNotReadyException) {
                    throw notReady
                } catch (error: Exception) {
                    throw runtimeFailure("M1 inference", error)
                }

                val succeeded = try {
                    validateWorkloadResult(
                        JSONObject(JsonExtractor.objectText(raw))
                    )
                } catch (_: Exception) {
                    false
                }

                if (succeeded) successes++

                val elapsedSeconds = (
                    System.currentTimeMillis() - startedAt
                ) / 1000
                onProgress(
                    "M1 local soak • ${elapsedSeconds}s • structured " +
                        "$successes/$attempts"
                )
            }
            nativeBenchmark = try {
                model.benchmark(
                    promptTokens = 128,
                    generationTokens = 128,
                    parallelSequences = 1,
                    repetitions = 3,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (notReady: ModelNotReadyException) {
                throw notReady
            } catch (error: Exception) {
                throw runtimeFailure("M1 benchmark", error)
            }
            completedNormally = true
        } catch (cancelled: CancellationException) {
            cancellation = cancelled
        } finally {
            samples = withContext(NonCancellable) {
                session.stop()
            }
        }

        val completedAt = System.currentTimeMillis()
        val result = buildResult(
            id = id,
            startedAt = startedAt,
            completedAt = completedAt,
            config = config,
            completedNormally = completedNormally,
            attempts = attempts,
            successes = successes,
            modelSnapshot = modelTelemetry.snapshot(),
            nativeBenchmark = nativeBenchmark,
            deviceSummary = deviceTelemetry.summarize(samples),
            deviceSamples = samples,
        )
        writeReport(result)

        cancellation?.let { throw it }
        return result
    }

    private fun runtimeFailure(
        stage: String,
        error: Exception,
    ): IllegalStateException {
        val type = error::class.java.simpleName.ifBlank {
            error::class.java.name
        }
        val detail = error.message
            ?.takeIf { it.isNotBlank() }
            ?: "no message"
        return IllegalStateException(
            "$stage failed: $type: $detail",
            error,
        )
    }

    private fun buildResult(
        id: String,
        startedAt: Long,
        completedAt: Long,
        config: M1ValidationConfig,
        completedNormally: Boolean,
        attempts: Int,
        successes: Int,
        modelSnapshot: ModelTelemetrySnapshot,
        nativeBenchmark: ModelBenchmark?,
        deviceSummary: DeviceTelemetrySummary,
        deviceSamples: List<DeviceTelemetrySample>,
    ): M1ValidationResult {
        val successRate = if (attempts > 0) {
            successes.toDouble() / attempts
        } else {
            0.0
        }
        val actualDuration = completedAt - startedAt
        val acceptanceDuration = config.durationMs >= ACCEPTANCE_DURATION_MS

        val checks = listOf(
            M1AcceptanceCheck(
                metric = "workload completed without crash",
                actual = completedNormally.toString(),
                target = "true",
                passes = completedNormally,
            ),
            M1AcceptanceCheck(
                metric = "structured-output parse success",
                actual = percent(successRate),
                target = ">=95%",
                passes = successRate >= 0.95,
            ),
            M1AcceptanceCheck(
                metric = "10-minute acceptance duration",
                actual = "${actualDuration / 1000}s",
                target = ">=600s",
                passes = acceptanceDuration &&
                    actualDuration >= ACCEPTANCE_DURATION_MS - DURATION_TOLERANCE_MS,
            ),
            M1AcceptanceCheck(
                metric = "persistent severe thermal throttling",
                actual = percent(deviceSummary.severeOrWorseFraction),
                target = "<20% of samples",
                passes = deviceSummary.severeOrWorseFraction < 0.20,
            ),
            M1AcceptanceCheck(
                metric = "system low-memory condition",
                actual = if (deviceSummary.lowMemoryObserved) {
                    "Android reported low-memory pressure"
                } else {
                    "${deviceSummary.minimumAvailableRamMb.toInt()} MB minimum free"
                },
                target = "no Android low-memory condition",
                passes = !deviceSummary.lowMemoryObserved,
            ),
        )

        val directory = File(context.filesDir, "evaluation").apply { mkdirs() }
        val reportFile = File(directory, "m1-$startedAt-${id.take(8)}.json")

        return M1ValidationResult(
            id = id,
            startedAtEpochMs = startedAt,
            completedAtEpochMs = completedAt,
            requestedDurationMs = config.durationMs,
            actualDurationMs = actualDuration,
            completedNormally = completedNormally,
            structuredAttempts = attempts,
            structuredSuccesses = successes,
            structuredSuccessRate = successRate,
            modelTelemetry = modelSnapshot,
            nativeBenchmark = nativeBenchmark,
            deviceTelemetry = deviceSummary,
            deviceSamples = deviceSamples,
            checks = checks,
            reportPath = reportFile.absolutePath,
        )
    }

    private fun writeReport(result: M1ValidationResult) {
        val json = JSONObject().apply {
            put("id", result.id)
            put("startedAtEpochMs", result.startedAtEpochMs)
            put("completedAtEpochMs", result.completedAtEpochMs)
            put("requestedDurationMs", result.requestedDurationMs)
            put("actualDurationMs", result.actualDurationMs)
            put("completedNormally", result.completedNormally)
            put("structuredAttempts", result.structuredAttempts)
            put("structuredSuccesses", result.structuredSuccesses)
            put("structuredSuccessRate", result.structuredSuccessRate)
            put("modelTelemetry", JSONObject().apply {
                put("calls", result.modelTelemetry.calls)
                put("streamEmissions", result.modelTelemetry.streamEmissions)
                put("outputChars", result.modelTelemetry.outputChars)
                put("totalDurationMs", result.modelTelemetry.totalDurationMs)
                put(
                    "averageEmissionsPerSecond",
                    result.modelTelemetry.averageEmissionsPerSecond ?: JSONObject.NULL,
                )
                put("recentCalls", JSONArray().apply {
                    result.modelTelemetry.recentCalls.forEach { call ->
                        put(JSONObject().apply {
                            put("startedAtEpochMs", call.startedAtEpochMs)
                            put("durationMs", call.durationMs)
                            put("streamEmissions", call.streamEmissions)
                            put("outputChars", call.outputChars)
                            put(
                                "emissionsPerSecond",
                                call.emissionsPerSecond ?: JSONObject.NULL,
                            )
                        })
                    }
                })
            })
            put("nativeBenchmark", if (result.nativeBenchmark != null) {
                JSONObject().apply {
                    val benchmark = result.nativeBenchmark
                    put("promptTokens", benchmark.promptTokens)
                    put("generationTokens", benchmark.generationTokens)
                    put("parallelSequences", benchmark.parallelSequences)
                    put("repetitions", benchmark.repetitions)
                    put(
                        "promptTokensPerSecond",
                        benchmark.promptTokensPerSecond ?: JSONObject.NULL,
                    )
                    put(
                        "generationTokensPerSecond",
                        benchmark.generationTokensPerSecond ?: JSONObject.NULL,
                    )
                    put("rawOutput", benchmark.rawOutput)
                }
            } else {
                JSONObject.NULL
            })
            put("deviceTelemetry", JSONObject().apply {
                put("samples", result.deviceTelemetry.samples)
                put("peakProcessPssMb", result.deviceTelemetry.peakProcessPssMb)
                put(
                    "peakProcessRssMb",
                    result.deviceTelemetry.peakProcessRssMb ?: JSONObject.NULL,
                )
                put("peakNativeHeapMb", result.deviceTelemetry.peakNativeHeapMb)
                put("minimumAvailableRamMb", result.deviceTelemetry.minimumAvailableRamMb)
                put("totalRamMb", result.deviceTelemetry.totalRamMb)
                put("lowMemoryObserved", result.deviceTelemetry.lowMemoryObserved)
                put("maximumThermalStatus", result.deviceTelemetry.maximumThermalStatus)
                put(
                    "maximumThermalStatusLabel",
                    ThermalStatusLabel.label(result.deviceTelemetry.maximumThermalStatus),
                )
                put(
                    "severeOrWorseFraction",
                    result.deviceTelemetry.severeOrWorseFraction,
                )
                put(
                    "batteryStartPercent",
                    result.deviceTelemetry.batteryStartPercent ?: JSONObject.NULL,
                )
                put(
                    "batteryEndPercent",
                    result.deviceTelemetry.batteryEndPercent ?: JSONObject.NULL,
                )
                put(
                    "chargeCounterDeltaMicroAh",
                    result.deviceTelemetry.chargeCounterDeltaMicroAh ?: JSONObject.NULL,
                )
                put(
                    "maximumBatteryTemperatureC",
                    result.deviceTelemetry.maximumBatteryTemperatureC ?: JSONObject.NULL,
                )
            })
            put("deviceSamples", JSONArray().apply {
                result.deviceSamples.forEach { sample ->
                    put(JSONObject().apply {
                        put("timestampEpochMs", sample.timestampEpochMs)
                        put("processPssKb", sample.processPssKb)
                        put("processRssKb", sample.processRssKb ?: JSONObject.NULL)
                        put("nativeHeapAllocatedBytes", sample.nativeHeapAllocatedBytes)
                        put("systemAvailableBytes", sample.systemAvailableBytes)
                        put("systemTotalBytes", sample.systemTotalBytes)
                        put("systemLowMemory", sample.systemLowMemory)
                        put("thermalStatus", sample.thermalStatus)
                        put(
                            "batteryPercent",
                            sample.batteryPercent ?: JSONObject.NULL,
                        )
                        put(
                            "batteryChargeCounterMicroAh",
                            sample.batteryChargeCounterMicroAh ?: JSONObject.NULL,
                        )
                        put(
                            "batteryTemperatureTenthsC",
                            sample.batteryTemperatureTenthsC ?: JSONObject.NULL,
                        )
                    })
                }
            })
            put("checks", JSONArray().apply {
                result.checks.forEach { check ->
                    put(JSONObject().apply {
                        put("metric", check.metric)
                        put("actual", check.actual)
                        put("target", check.target)
                        put("passes", check.passes)
                    })
                }
            })
        }

        File(result.reportPath).writeText(json.toString(2))
    }

    private fun validateWorkloadResult(json: JSONObject): Boolean {
        val answer = json.optString("answer").trim()
        val confidence = json.optDouble("confidence", Double.NaN)
        val tags = json.optJSONArray("tags")
        return answer.isNotBlank() &&
            confidence.isFinite() &&
            confidence in 0.0..1.0 &&
            tags != null &&
            tags.length() in 1..4
    }

    private fun percent(value: Double): String =
        "${"%.1f".format(value * 100)}%"

    companion object {
        const val ACCEPTANCE_DURATION_MS = 10L * 60 * 1000
        private const val DURATION_TOLERANCE_MS = 2_000
        private const val STRUCTURED_SYSTEM = """
            You are a deterministic structured-output test.
            Return exactly one JSON object with:
            {"answer":"short text","confidence":0.0,"tags":["tag"]}
            Do not use markdown or prose outside JSON.
        """

        private val WORKLOADS = listOf(
            "Summarize the relationship between evidence and claims in one sentence.",
            "Give one sentence explaining why citation verification is useful.",
            "State one benefit of explicit compute budgets in autonomous research.",
            "Explain in one sentence why contradictory sources should not be averaged blindly.",
            "State one reason to cache retrieved pages in a mobile research application.",
            "Give one sentence about why primary sources can matter for technical facts.",
        )
    }
}
