package dev.zerocost.researcher.inference

import android.content.Context
import android.os.SystemClock
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import dev.zerocost.researcher.config.AppPreferences
import dev.zerocost.researcher.performance.ModelCallMetric
import dev.zerocost.researcher.performance.ModelTelemetryRecorder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class LlamaCppResearchModel(
    context: Context,
    private val preferences: AppPreferences,
    private val telemetry: ModelTelemetryRecorder,
) : ResearchModel {
    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)
    private val mutex = Mutex()
    private var loadedPath: String? = null

    override suspend fun generateText(
        systemPrompt: String,
        prompt: String,
        maxTokens: Int,
    ): String = mutex.withLock {
        ensureModelLoaded()
        engine.setSystemPrompt(
            "$systemPrompt\n\n/no_think"
        )

        val startedAtEpochMs = System.currentTimeMillis()
        val startedElapsedMs = SystemClock.elapsedRealtime()
        var streamEmissions = 0
        val output = StringBuilder()

        try {
            engine.sendUserPrompt(
                message = prompt,
                predictLength = maxTokens.coerceIn(64, 2048),
            ).collect { token ->
                streamEmissions++
                output.append(token)
            }
            ThinkingOutputSanitizer.strip(output.toString())
        } finally {
            telemetry.record(
                ModelCallMetric(
                    startedAtEpochMs = startedAtEpochMs,
                    durationMs = (
                        SystemClock.elapsedRealtime() - startedElapsedMs
                    ).coerceAtLeast(1),
                    streamEmissions = streamEmissions,
                    outputChars = output.length,
                )
            )
        }
    }

    override suspend fun benchmark(
        promptTokens: Int,
        generationTokens: Int,
        parallelSequences: Int,
        repetitions: Int,
    ): ModelBenchmark = mutex.withLock {
        ensureModelLoaded()
        val raw = engine.bench(
            pp = promptTokens,
            tg = generationTokens,
            pl = parallelSequences,
            nr = repetitions,
        )
        val rates = parseBenchmarkRates(raw)

        ModelBenchmark(
            promptTokens = promptTokens,
            generationTokens = generationTokens,
            parallelSequences = parallelSequences,
            repetitions = repetitions,
            promptTokensPerSecond = rates["pp"],
            generationTokensPerSecond = rates["tg"],
            rawOutput = raw,
        )
    }

    private fun parseBenchmarkRates(raw: String): Map<String, Double> {
        val regex = Regex(
            """\|\s*(pp|tg)\s+\d+\s*\|\s*([0-9]+(?:\.[0-9]+)?)"""
        )
        return buildMap {
            regex.findAll(raw).forEach { match ->
                val value = match.groupValues[2].toDoubleOrNull()
                    ?: return@forEach
                put(match.groupValues[1], value)
            }
        }
    }

    private suspend fun ensureModelLoaded() {
        val path = preferences.modelPath
        if (path.isBlank() || !File(path).isFile) {
            throw ModelNotReadyException(
                "Import a GGUF model before researching."
            )
        }
        if (loadedPath == path) return

        if (loadedPath != null) {
            engine.cleanUp()
        }
        engine.loadModel(path)
        loadedPath = path
    }
}
